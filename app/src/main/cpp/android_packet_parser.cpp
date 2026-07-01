/**
 * android_packet_parser.cpp
 *
 * Parses raw IPv4 packets read from the Android VpnService TUN file descriptor
 * into fw::PacketInfo structs that the RuleEngine can evaluate.
 *
 * This mirrors the logic in the desktop nfq_capture.cpp but has no NFQ dependency.
 * The TUN interface always delivers complete IPv4 packets.
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

/**
 * Parse a raw IP packet buffer into a fw::PacketInfo.
 * Called from aegis_jni.cpp::nativeEvaluatePacket().
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

    if (len < (int)sizeof(IpHeader)) {
        LOGD("Packet too short for IP header: %d bytes", len);
        return pkt;  // Return default — will likely be ALLOWED (engine sees zeros)
    }

    const IpHeader* ip = reinterpret_cast<const IpHeader*>(buf);

    // Only handle IPv4 (version == 4)
    uint8_t version = (ip->ihl_version >> 4) & 0x0F;
    if (version != 4) {
        pkt.is_ipv6 = (version == 6);
        return pkt;
    }

    uint8_t ihl = (ip->ihl_version & 0x0F) * 4; // IP header length in bytes
    if (ihl < 20 || ihl > len) return pkt;

    // Source and destination IPs (convert from network byte order to host byte order)
    pkt.src_ip = ntohl(ip->src_ip);
    pkt.dst_ip = ntohl(ip->dst_ip);
    pkt.ttl    = ip->ttl;

    // Fragment information
    uint16_t frag_off_raw = ntohs(ip->frag_off);
    pkt.has_more_frags    = (frag_off_raw & IP_MF) != 0;
    pkt.frag_offset_bytes = (frag_off_raw & IP_OFFMASK) * 8;
    pkt.is_frag_offset    = pkt.frag_offset_bytes > 0;

    uint8_t proto = ip->protocol;

    const uint8_t* transport_start = buf + ihl;
    int transport_len = len - ihl;

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

                // Payload after TCP header
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
        case 1: { // ICMP
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

    return pkt;
}
