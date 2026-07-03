/**
 * android_packet_parser.cpp
 *
 * Parses raw IPv4 and IPv6 packets read from the Android VpnService TUN fd
 * into fw::PacketInfo structs that the RuleEngine can evaluate.
 *
 * IPv4: Full header parse with fragmentation, TCP/UDP/ICMP extraction.
 * IPv6: Fixed 40-byte header + TCP/UDP/ICMPv6 next-header extraction.
 */

#include "platform.hpp"
#include "types.hpp"
#include <cstring>
#include <cstdint>
#include <string>
#include <android/log.h>

#define LOG_TAG "AegisPktParser"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ── Raw IP/TCP/UDP/ICMP header structs ─────────────────────────
// We define these inline to avoid pulling in Linux kernel headers.

#pragma pack(push, 1)
struct IpHeader {
    uint8_t  ihl_version;   // version (4 bits) + IHL (4 bits)
    uint8_t  tos;
    uint16_t total_len;
    uint16_t id;
    uint16_t frag_off;
    uint8_t  ttl;
    uint8_t  protocol;
    uint16_t checksum;
    uint32_t src_ip;
    uint32_t dst_ip;
};

// IPv6 fixed header (RFC 2460) — always 40 bytes
struct Ip6Header {
    uint32_t version_tc_flow; // version(4) + traffic class(8) + flow label(20)
    uint16_t payload_len;
    uint8_t  next_header;     // protocol (6=TCP, 17=UDP, 58=ICMPv6)
    uint8_t  hop_limit;       // TTL equivalent
    uint8_t  src_addr[16];    // 128-bit source address
    uint8_t  dst_addr[16];    // 128-bit destination address
};

struct TcpHeader {
    uint16_t src_port;
    uint16_t dst_port;
    uint32_t seq;
    uint32_t ack;
    uint8_t  data_off;  // data offset (4 bits, upper) + reserved
    uint8_t  flags;     // TCP flags: FIN SYN RST PSH ACK URG
    uint16_t window;
    uint16_t checksum;
    uint16_t urg_ptr;
};

struct UdpHeader {
    uint16_t src_port;
    uint16_t dst_port;
    uint16_t length;
    uint16_t checksum;
};

struct IcmpHeader {
    uint8_t  type;
    uint8_t  code;
    uint16_t checksum;
    uint32_t rest;
};
#pragma pack(pop)

// Fragment flags
static constexpr uint16_t IP_DF    = 0x4000; // Don't Fragment
static constexpr uint16_t IP_MF    = 0x2000; // More Fragments
static constexpr uint16_t IP_OFFMASK = 0x1FFF;

// ── Helper: parse TCP/UDP layer (shared by IPv4 and IPv6) ──────────
static void parse_transport(
        const uint8_t* transport_start, int transport_len,
        uint8_t proto, fw::PacketInfo& pkt)
{
    switch (proto) {
        case 6: { // TCP
            pkt.proto = fw::Proto::TCP;
            if (transport_len >= (int)sizeof(TcpHeader)) {
                const TcpHeader* tcp = reinterpret_cast<const TcpHeader*>(transport_start);
                pkt.src_port  = ntohs(tcp->src_port);
                pkt.dst_port  = ntohs(tcp->dst_port);
                pkt.tcp_flags = tcp->flags;
                pkt.tcp_seq   = ntohl(tcp->seq);
                pkt.tcp_ack   = ntohl(tcp->ack);
                uint8_t tcp_hdr_len = ((tcp->data_off >> 4) & 0x0F) * 4;
                if (tcp_hdr_len >= 20 && tcp_hdr_len <= transport_len) {
                    pkt.payload_ptr = transport_start + tcp_hdr_len;
                    pkt.payload_len = static_cast<uint16_t>(transport_len - tcp_hdr_len);
                }
            }
            break;
        }
        case 17: { // UDP
            pkt.proto = fw::Proto::UDP;
            if (transport_len >= (int)sizeof(UdpHeader)) {
                const UdpHeader* udp = reinterpret_cast<const UdpHeader*>(transport_start);
                pkt.src_port    = ntohs(udp->src_port);
                pkt.dst_port    = ntohs(udp->dst_port);
                pkt.payload_ptr = transport_start + sizeof(UdpHeader);
                pkt.payload_len = static_cast<uint16_t>(
                    std::min<int>(transport_len - sizeof(UdpHeader), 65535));
            }
            break;
        }
        case 1:   // ICMPv4
        case 58: { // ICMPv6
            pkt.proto = fw::Proto::ICMP;
            if (transport_len >= (int)sizeof(IcmpHeader)) {
                const IcmpHeader* icmp = reinterpret_cast<const IcmpHeader*>(transport_start);
                pkt.icmp_type = icmp->type;
                pkt.icmp_code = icmp->code;
            }
            break;
        }
        default:
            pkt.proto = fw::Proto::ANY;
            break;
    }
}

/**
 * Parse a raw IP packet buffer into a fw::PacketInfo.
 * Handles both IPv4 and IPv6.
 *
 * @param buf       pointer to raw IP packet bytes (TUN delivers complete IP packets)
 * @param len       total byte length of the packet
 * @param app_name  package name of the app that sent/received this packet
 */
fw::PacketInfo parse_ip_packet(const uint8_t* buf, int len, const std::string& app_name)
{
    fw::PacketInfo pkt;
    pkt.size = len;
    pkt.process_name = app_name;

    if (len < 1) return pkt;

    uint8_t version = (buf[0] >> 4) & 0x0F;

    // ── IPv4 ────────────────────────────────────────────────────
    if (version == 4) {
        if (len < (int)sizeof(IpHeader)) {
            LOGD("IPv4 packet too short: %d bytes", len);
            return pkt;
        }

        const IpHeader* ip = reinterpret_cast<const IpHeader*>(buf);
        uint8_t ihl = (ip->ihl_version & 0x0F) * 4;
        if (ihl < 20 || ihl > len) return pkt;

        pkt.src_ip = ntohl(ip->src_ip);
        pkt.dst_ip = ntohl(ip->dst_ip);
        pkt.ttl    = ip->ttl;

        uint16_t frag_off_raw = ntohs(ip->frag_off);
        pkt.has_more_frags    = (frag_off_raw & IP_MF) != 0;
        pkt.frag_offset_bytes = (frag_off_raw & IP_OFFMASK) * 8;
        pkt.is_frag_offset    = pkt.frag_offset_bytes > 0;

        const uint8_t* transport_start = buf + ihl;
        int transport_len = len - ihl;
        parse_transport(transport_start, transport_len, ip->protocol, pkt);
        return pkt;
    }

    // ── IPv6 ────────────────────────────────────────────────────
    if (version == 6) {
        if (len < (int)sizeof(Ip6Header)) {
            LOGD("IPv6 packet too short: %d bytes", len);
            return pkt;
        }

        pkt.is_ipv6 = true;
        const Ip6Header* ip6 = reinterpret_cast<const Ip6Header*>(buf);
        pkt.ttl = ip6->hop_limit; // hop limit = IPv6 TTL

        // Represent IPv6 src/dst as truncated 32-bit (last 4 bytes) for rule matching.
        // Full 128-bit matching would require extending types.hpp — this is a pragmatic
        // approximation that still catches most patterns (loopback, link-local, etc).
        pkt.src_ip = (ip6->src_addr[12] << 24) | (ip6->src_addr[13] << 16) |
                     (ip6->src_addr[14] <<  8) |  ip6->src_addr[15];
        pkt.dst_ip = (ip6->dst_addr[12] << 24) | (ip6->dst_addr[13] << 16) |
                     (ip6->dst_addr[14] <<  8) |  ip6->dst_addr[15];

        // Walk extension headers to find the actual transport protocol.
        // Common extension headers: hop-by-hop(0), routing(43), fragment(44), dest options(60)
        uint8_t next_hdr = ip6->next_header;
        const uint8_t* cursor = buf + sizeof(Ip6Header);
        int remaining = len - (int)sizeof(Ip6Header);

        // Skip known extension headers
        while (remaining > 0) {
            if (next_hdr == 6 || next_hdr == 17 || next_hdr == 58 || next_hdr == 59) {
                break; // TCP, UDP, ICMPv6, or NO_NEXT_HEADER
            }
            // Extension headers: first byte = next header, second byte = length in 8-byte units
            if (next_hdr == 0 || next_hdr == 43 || next_hdr == 60) {
                if (remaining < 2) break;
                uint8_t ext_len = (cursor[1] + 1) * 8;
                next_hdr = cursor[0];
                if (ext_len > remaining) break;
                cursor   += ext_len;
                remaining -= ext_len;
            } else if (next_hdr == 44) { // Fragment header — fixed 8 bytes
                if (remaining < 8) break;
                pkt.has_more_frags = (cursor[3] & 0x01) != 0;
                uint16_t frag_offset = ((cursor[2] << 8) | cursor[3]) >> 3;
                pkt.is_frag_offset = (frag_offset != 0);
                next_hdr   = cursor[0];
                cursor    += 8;
                remaining -= 8;
            } else {
                break; // Unknown extension header — stop
            }
        }

        parse_transport(cursor, remaining, next_hdr, pkt);
        return pkt;
    }

    // Unknown version
    LOGD("Unknown IP version: %d", version);
    return pkt;
}
