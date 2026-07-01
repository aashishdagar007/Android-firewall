/**
 * aegis_jni.cpp  —  JNI bridge between Android (Kotlin) and the C++ firewall engine
 *
 * This file is the heart of the Android JNI layer. It instantiates a single
 * FirewallEngine instance (RuleEngine + Logger + ChainLedger) and exposes
 * its full API to Kotlin through JNI method calls.
 *
 * All JNI functions are named following the Java_<package>_<class>_<method>
 * convention, matching the Kotlin `external fun` declarations in FirewallEngine.kt.
 *
 * Thread safety: The C++ RuleEngine is internally thread-safe (shared_mutex).
 * JNI calls can come from multiple threads (VPN read thread, UI thread, etc.).
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <memory>
#include <sstream>
#include <vector>
#include <cstring>
#include <fstream>

// ── Include the desktop firewall headers ────────────────────────
// The CMakeLists.txt adds the Firewall/include directory to the include path.
#include "platform.hpp"       // MUST be first (platform abstractions)
#include "types.hpp"
#include "rule_engine.hpp"
#include "logger.hpp"
#include "config_parser.hpp"
#include "ring_buffer.hpp"
#include "chain_ledger.hpp"
#include "nfq_capture.hpp"    // For PacketRecord, LiveStats definitions
#include "android_compat.hpp" // Android-specific helpers

#define LOG_TAG "AegisJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ── Global engine state ─────────────────────────────────────────
// A single instance shared across all JNI calls.
struct AegisEngine {
    fw::RuleEngine                    engine{fw::Action::BLOCK};
    fw::LiveStats                     stats;
    fw::RingBuffer<fw::PacketRecord>  ring{1000};
    std::unique_ptr<fw::Logger>       logger;
    fw::ChainLedger                   ledger;
    std::string                       ledger_json_path; // stored for getLedger()
    bool                              ledger_open{false}; // result of ledger.open()
    uint64_t                          seq_counter{0};

    AegisEngine(const std::string& log_path, const std::string& ledger_chain, const std::string& ledger_json)
        : ledger(ledger_chain, ledger_json), ledger_json_path(ledger_json)
    {
        logger = std::make_unique<fw::Logger>(log_path, fw::LogLevel::LOG_INFO);
        LOGI("AegisEngine constructed");
    }
};

static AegisEngine* g_engine = nullptr;

// ── Helper: escape JSON string ──────────────────────────────────
static std::string escape_json(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:   out += c;      break;
        }
    }
    return out;
}

// ── Helper: jstring → std::string ──────────────────────────────
static std::string jstr(JNIEnv* env, jstring js) {
    if (!js) return {};
    const char* cs = env->GetStringUTFChars(js, nullptr);
    std::string s(cs);
    env->ReleaseStringUTFChars(js, cs);
    return s;
}

// ── Helper: rule to JSON object ────────────────────────────────
static std::string rule_to_json(const fw::Rule& r) {
    std::ostringstream j;
    j << "{"
      << "\"id\":"          << r.id                            << ","
      << "\"action\":\""    << fw::action_name(r.action)       << "\","
      << "\"proto\":\""     << fw::proto_name(r.proto)         << "\","
      << "\"src_ip\":"      << r.src_ip                        << ","
      << "\"dst_ip\":"      << r.dst_ip                        << ","
      << "\"src_port\":"    << r.src_port                      << ","
      << "\"dst_port\":"    << r.dst_port                      << ","
      << "\"description\":\"" << escape_json(r.description)    << "\","
      << "\"hit_count\":"   << r.hit_count.load()
      << "}";
    return j.str();
}

// ════════════════════════════════════════════════════════════════
//  JNI Exports
// ════════════════════════════════════════════════════════════════

extern "C" {

/**
 * Create the firewall engine. Must be called once before any other JNI function.
 * @param logPath     absolute path for the log file inside app's files dir
 * @param configPath  absolute path for rules.conf
 * @param ledgerDir   absolute path for ledger files directory
 */
JNIEXPORT void JNICALL
Java_com_asd_firewall_FirewallEngine_nativeCreate(
        JNIEnv* env, jobject /*this*/,
        jstring logPath, jstring configPath, jstring ledgerDir)
{
    if (g_engine) {
        LOGI("Engine already created, skipping");
        return;
    }

    std::string log_path    = jstr(env, logPath);
    std::string config_path = jstr(env, configPath);
    std::string ledger_dir  = jstr(env, ledgerDir);

    std::string ledger_chain = ledger_dir + "/ledger.chain";
    std::string ledger_json  = ledger_dir + "/ledger.json";

    g_engine = new AegisEngine(log_path, ledger_chain, ledger_json);

    // Load rules from config file (if it exists)
    auto loaded_rules = fw::ConfigParser::load(config_path);
    for (auto& r : loaded_rules)
        g_engine->engine.add_rule(std::move(r));

    // Open ledger
    g_engine->ledger_open = g_engine->ledger.open();
    if (g_engine->ledger_open) {
        g_engine->ledger.log_firewall_start();
        g_engine->logger->log(fw::LogLevel::LOG_INFO, "Chain ledger initialized");
    }

    g_engine->logger->log(fw::LogLevel::LOG_INFO, "AEGIS XII JNI engine started");
    LOGI("Engine created: log=%s config=%s", log_path.c_str(), config_path.c_str());
}

/**
 * Destroy the engine. Call on service stop.
 */
JNIEXPORT void JNICALL
Java_com_asd_firewall_FirewallEngine_nativeDestroy(
        JNIEnv* /*env*/, jobject /*this*/)
{
    if (!g_engine) return;
    g_engine->logger->log(fw::LogLevel::LOG_INFO, "Firewall stopping");
    g_engine->ledger.log_firewall_stop();
    g_engine->ledger.close();
    delete g_engine;
    g_engine = nullptr;
    LOGI("Engine destroyed");
}

/**
 * Evaluate a raw IP packet. Called from the VPN TUN read loop for every packet.
 * @param rawPacket  byte array of the raw IP packet (from TUN fd)
 * @return  1 = ALLOW, 0 = BLOCK
 */
JNIEXPORT jint JNICALL
Java_com_asd_firewall_FirewallEngine_nativeEvaluatePacket(
        JNIEnv* env, jobject /*this*/,
        jbyteArray rawPacket, jint length, jstring appName)
{
    if (!g_engine) return 1; // Allow by default if engine not ready

    jbyte* buf = env->GetByteArrayElements(rawPacket, nullptr);
    if (!buf) return 1;

    std::string app = jstr(env, appName);

    // Parse the raw IP packet into PacketInfo
    // (android_packet_parser.cpp provides this function)
    extern fw::PacketInfo parse_ip_packet(const uint8_t* buf, int len, const std::string& app_name);
    fw::PacketInfo pkt = parse_ip_packet(
        reinterpret_cast<const uint8_t*>(buf),
        static_cast<int>(length),
        app
    );

    env->ReleaseByteArrayElements(rawPacket, buf, JNI_ABORT);

    // Run through the rule engine
    fw::EvalResult result = g_engine->engine.evaluate(pkt);

    // Update live stats
    g_engine->stats.total++;
    g_engine->stats.bytes_total += static_cast<uint64_t>(pkt.size);
    if (result.verdict == fw::Action::ALLOW) {
        g_engine->stats.allowed++;
    } else {
        g_engine->stats.blocked++;
        // Log blocked packet to the tamper-proof ledger
        if (g_engine->ledger_open) {
            // ledger entry for blocked packet
        }
    }

    switch (pkt.proto) {
        case fw::Proto::TCP:  g_engine->stats.tcp++;  break;
        case fw::Proto::UDP:  g_engine->stats.udp++;  break;
        case fw::Proto::ICMP: g_engine->stats.icmp++; break;
        default: break;
    }

    // Store in ring buffer
    fw::PacketRecord rec;
    rec.info         = pkt;
    rec.result       = result;
    rec.timestamp    = fw::make_android_timestamp();
    rec.src_ip_str   = ip4_to_string(pkt.src_ip);
    rec.dst_ip_str   = ip4_to_string(pkt.dst_ip);
    rec.seq          = ++g_engine->seq_counter;
    rec.process_name = app;
    g_engine->ring.push(rec);

    return (result.verdict == fw::Action::ALLOW) ? 1 : 0;
}

/**
 * Get live stats as JSON string.
 */
JNIEXPORT jstring JNICALL
Java_com_asd_firewall_FirewallEngine_nativeGetStats(
        JNIEnv* env, jobject /*this*/)
{
    if (!g_engine) return env->NewStringUTF("{\"total\":0,\"allowed\":0,\"blocked\":0,\"bytes_total\":0}");

    std::ostringstream j;
    j << "{"
      << "\"total\":"      << g_engine->stats.total.load()       << ","
      << "\"allowed\":"    << g_engine->stats.allowed.load()     << ","
      << "\"blocked\":"    << g_engine->stats.blocked.load()     << ","
      << "\"tcp\":"        << g_engine->stats.tcp.load()         << ","
      << "\"udp\":"        << g_engine->stats.udp.load()         << ","
      << "\"icmp\":"       << g_engine->stats.icmp.load()        << ","
      << "\"bytes_total\":" << g_engine->stats.bytes_total.load()
      << "}";
    return env->NewStringUTF(j.str().c_str());
}

/**
 * Get all current rules as a JSON array.
 */
JNIEXPORT jstring JNICALL
Java_com_asd_firewall_FirewallEngine_nativeGetRules(
        JNIEnv* env, jobject /*this*/)
{
    if (!g_engine) return env->NewStringUTF("[]");

    const auto& rules = g_engine->engine.rules();
    std::ostringstream j;
    j << "[";
    bool first = true;
    for (const auto& r : rules) {
        if (!first) j << ",";
        j << rule_to_json(r);
        first = false;
    }
    j << "]";
    return env->NewStringUTF(j.str().c_str());
}

/**
 * Add a rule from a JSON body string (same format as the REST API).
 * Supported fields: action, proto, src_ip, dst_ip, src_port, dst_port, description
 */
JNIEXPORT jboolean JNICALL
Java_com_asd_firewall_FirewallEngine_nativeAddRule(
        JNIEnv* env, jobject /*this*/, jstring jsonBody)
{
    if (!g_engine) return JNI_FALSE;
    std::string body = jstr(env, jsonBody);

    // Simple JSON field extraction (avoid heavy JSON lib dependency)
    auto extract = [&](const std::string& key) -> std::string {
        std::string search = "\"" + key + "\":\"";
        auto pos = body.find(search);
        if (pos == std::string::npos) {
            // Try without quotes (numeric)
            search = "\"" + key + "\":";
            pos = body.find(search);
            if (pos == std::string::npos) return "";
            pos += search.size();
            auto end = body.find_first_of(",}", pos);
            return body.substr(pos, end - pos);
        }
        pos += search.size();
        auto end = body.find('"', pos);
        return body.substr(pos, end - pos);
    };

    fw::Rule r;
    std::string action_str = extract("action");
    std::string proto_str  = extract("proto");
    std::string desc       = extract("description");

    r.action = (action_str == "ALLOW") ? fw::Action::ALLOW : fw::Action::BLOCK;

    if      (proto_str == "TCP")  r.proto = fw::Proto::TCP;
    else if (proto_str == "UDP")  r.proto = fw::Proto::UDP;
    else if (proto_str == "ICMP") r.proto = fw::Proto::ICMP;
    else                          r.proto = fw::Proto::ANY;

    std::string src_ip_s  = extract("src_ip");
    std::string dst_ip_s  = extract("dst_ip");
    std::string dst_port_s = extract("dst_port");

    if (!src_ip_s.empty() && src_ip_s != "*") r.src_ip   = string_to_ip4(src_ip_s.c_str());
    if (!dst_ip_s.empty() && dst_ip_s != "*") r.dst_ip   = string_to_ip4(dst_ip_s.c_str());
    if (!dst_port_s.empty()) {
        try { r.dst_port = static_cast<uint16_t>(std::stoi(dst_port_s)); } catch (...) {}
    }

    r.description = desc;
    g_engine->engine.add_rule(std::move(r));
    return JNI_TRUE;
}

/**
 * Delete a rule by ID.
 */
JNIEXPORT jboolean JNICALL
Java_com_asd_firewall_FirewallEngine_nativeDeleteRule(
        JNIEnv* /*env*/, jobject /*this*/, jint ruleId)
{
    if (!g_engine) return JNI_FALSE;
    return g_engine->engine.remove_rule(static_cast<uint32_t>(ruleId)) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Get last N packets from ring buffer as JSON array.
 */
JNIEXPORT jstring JNICALL
Java_com_asd_firewall_FirewallEngine_nativeGetPackets(
        JNIEnv* env, jobject /*this*/, jint n)
{
    if (!g_engine) return env->NewStringUTF("[]");

    auto packets = g_engine->ring.tail(static_cast<size_t>(n));
    std::ostringstream j;
    j << "[";
    bool first = true;
    for (const auto& pr : packets) {
        if (!first) j << ",";
        j << "{"
          << "\"seq\":"        << pr.seq                          << ","
          << "\"timestamp\":\"" << escape_json(pr.timestamp)      << "\","
          << "\"src_ip\":\""   << escape_json(pr.src_ip_str)      << "\","
          << "\"dst_ip\":\""   << escape_json(pr.dst_ip_str)      << "\","
          << "\"src_port\":"   << pr.info.src_port                << ","
          << "\"dst_port\":"   << pr.info.dst_port                << ","
          << "\"proto\":\""    << fw::proto_name(pr.info.proto)   << "\","
          << "\"verdict\":\""  << fw::action_name(pr.result.verdict) << "\","
          << "\"process\":\""  << escape_json(pr.process_name)    << "\","
          << "\"size\":"       << pr.info.size
          << "}";
        first = false;
    }
    j << "]";
    return env->NewStringUTF(j.str().c_str());
}

/**
 * Get anomaly hit counts as JSON array.
 */
JNIEXPORT jstring JNICALL
Java_com_asd_firewall_FirewallEngine_nativeGetAnomalies(
        JNIEnv* env, jobject /*this*/)
{
    if (!g_engine) return env->NewStringUTF("[]");

    auto anomalies = g_engine->engine.get_anomaly_snapshot();
    std::ostringstream j;
    j << "[";
    bool first = true;
    for (const auto& a : anomalies) {
        if (!first) j << ",";
        j << "{\"name\":\"" << escape_json(a.name) << "\","
          << "\"hit_count\":" << a.hit_count << "}";
        first = false;
    }
    j << "]";
    return env->NewStringUTF(j.str().c_str());
}

/**
 * Get live connection tracking table as JSON array.
 */
JNIEXPORT jstring JNICALL
Java_com_asd_firewall_FirewallEngine_nativeGetConnections(
        JNIEnv* env, jobject /*this*/)
{
    if (!g_engine) return env->NewStringUTF("[]");

    auto conns = g_engine->engine.get_connection_snapshot();
    std::ostringstream j;
    j << "[";
    bool first = true;
    for (const auto& c : conns) {
        if (!first) j << ",";
        j << "{"
          << "\"src_ip\":\""   << escape_json(c.src_ip)   << "\","
          << "\"dst_ip\":\""   << escape_json(c.dst_ip)   << "\","
          << "\"src_port\":"   << c.src_port               << ","
          << "\"dst_port\":"   << c.dst_port               << ","
          << "\"proto\":\""    << escape_json(c.proto)     << "\","
          << "\"state\":\""    << escape_json(c.state)     << "\","
          << "\"bytes_in\":"   << c.bytes_in               << ","
          << "\"bytes_out\":"  << c.bytes_out              << ","
          << "\"age_sec\":"    << c.age_sec
          << "}";
        first = false;
    }
    j << "]";
    return env->NewStringUTF(j.str().c_str());
}

/**
 * Get threat ban table as JSON array.
 */
JNIEXPORT jstring JNICALL
Java_com_asd_firewall_FirewallEngine_nativeGetThreats(
        JNIEnv* env, jobject /*this*/)
{
    if (!g_engine) return env->NewStringUTF("[]");

    auto threats = g_engine->engine.get_threat_table_snapshot();
    std::ostringstream j;
    j << "[";
    bool first = true;
    for (const auto& t : threats) {
        if (!first) j << ",";
        j << "{"
          << "\"ip\":\""       << escape_json(t.ip_str)  << "\","
          << "\"reason\":\""   << escape_json(t.reason)  << "\","
          << "\"ban_count\":"  << t.ban_count
          << "}";
        first = false;
    }
    j << "]";
    return env->NewStringUTF(j.str().c_str());
}

/**
 * Manually ban an IP address.
 */
JNIEXPORT jboolean JNICALL
Java_com_asd_firewall_FirewallEngine_nativeBanIp(
        JNIEnv* env, jobject /*this*/, jstring ipStr, jstring reason)
{
    if (!g_engine) return JNI_FALSE;
    uint32_t ip = string_to_ip4(jstr(env, ipStr).c_str());
    return g_engine->engine.ban_ip(ip, jstr(env, reason)) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Manually unban an IP address.
 */
JNIEXPORT jboolean JNICALL
Java_com_asd_firewall_FirewallEngine_nativeUnbanIp(
        JNIEnv* env, jobject /*this*/, jstring ipStr)
{
    if (!g_engine) return JNI_FALSE;
    uint32_t ip = string_to_ip4(jstr(env, ipStr).c_str());
    return g_engine->engine.unban_ip(ip) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Enable or disable stealth mode (silent drops — no RST/ICMP sent back).
 */
JNIEXPORT void JNICALL
Java_com_asd_firewall_FirewallEngine_nativeSetStealthMode(
        JNIEnv* /*env*/, jobject /*this*/, jboolean enabled)
{
    if (!g_engine) return;
    g_engine->engine.set_stealth_mode(enabled == JNI_TRUE);
}

/**
 * Get stealth mode status.
 */
JNIEXPORT jboolean JNICALL
Java_com_asd_firewall_FirewallEngine_nativeGetStealthMode(
        JNIEnv* /*env*/, jobject /*this*/)
{
    if (!g_engine) return JNI_FALSE;
    return g_engine->engine.get_stealth_mode() ? JNI_TRUE : JNI_FALSE;
}

/**
 * Get last N ledger entries as a JSON array.
 */
JNIEXPORT jstring JNICALL
Java_com_asd_firewall_FirewallEngine_nativeGetLedger(
        JNIEnv* env, jobject /*this*/, jint n)
{
    if (!g_engine) return env->NewStringUTF("[]");
    // Ledger stores entries as JSON lines — read last N lines
    std::string ledger_path = g_engine->ledger_json_path;
    std::ifstream f(ledger_path);
    if (!f.is_open()) return env->NewStringUTF("[]");

    std::vector<std::string> lines;
    std::string line;
    while (std::getline(f, line)) {
        if (!line.empty()) lines.push_back(std::move(line));
    }

    std::ostringstream j;
    j << "[";
    int start = std::max(0, (int)lines.size() - n);
    for (int i = start; i < (int)lines.size(); ++i) {
        if (i > start) j << ",";
        j << lines[i];
    }
    j << "]";
    return env->NewStringUTF(j.str().c_str());
}

/**
 * Set default policy (ALLOW or BLOCK for unmatched packets).
 */
JNIEXPORT void JNICALL
Java_com_asd_firewall_FirewallEngine_nativeSetDefaultPolicy(
        JNIEnv* env, jobject /*this*/, jstring policy)
{
    if (!g_engine) return;
    std::string p = jstr(env, policy);
    fw::Action action = (p == "ALLOW") ? fw::Action::ALLOW : fw::Action::BLOCK;
    g_engine->engine.set_default_policy(action);
}

/**
 * Get current rate limit (packets/second).
 */
JNIEXPORT jint JNICALL
Java_com_asd_firewall_FirewallEngine_nativeGetRateLimit(
        JNIEnv* /*env*/, jobject /*this*/)
{
    if (!g_engine) return 1000;
    return static_cast<jint>(g_engine->engine.get_rate_limit());
}

/**
 * Set rate limit (packets/second).
 */
JNIEXPORT void JNICALL
Java_com_asd_firewall_FirewallEngine_nativeSetRateLimit(
        JNIEnv* /*env*/, jobject /*this*/, jint pps)
{
    if (!g_engine) return;
    g_engine->engine.set_rate_limit(static_cast<uint32_t>(pps));
}

} // extern "C"
