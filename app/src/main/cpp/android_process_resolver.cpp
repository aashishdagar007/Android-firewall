/**
 * android_process_resolver.cpp
 *
 * Maps Android UIDs to app package names for process-level traffic attribution.
 *
 * Android's VpnService provides the UID of the app that owns each packet via
 * VpnService.Builder.addAllowedApplication() or through the PacketInfo UID
 * (available via the Kotlin-side TrafficStats/NetworkCapabilities layer).
 *
 * This C++ module provides a fast cache lookup for UID → package name strings
 * that are pre-populated from the Kotlin side (which has full PackageManager access).
 *
 * Design:
 *   - Kotlin calls nativeSetUidPackageMap() to push a uid→package JSON map
 *   - C++ stores it in a hash map
 *   - When evaluating packets, the UID is resolved here to a package name string
 *   - The package name is set on PacketInfo::process_name
 */

#include <jni.h>
#include <android/log.h>
#include <unordered_map>
#include <string>
#include <mutex>
#include <fstream>
#include <sstream>

#define LOG_TAG "AegisUidResolver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

// ── UID → Package name cache ─────────────────────────────────────
static std::unordered_map<int, std::string> g_uid_map;
static std::mutex g_uid_mtx;

/**
 * Resolve a UID to a package name.
 * Returns the package name or "uid:<uid>" if not found.
 */
std::string resolve_uid_to_package(int uid) {
    std::lock_guard<std::mutex> lock(g_uid_mtx);
    auto it = g_uid_map.find(uid);
    if (it != g_uid_map.end()) return it->second;
    // Fallback: try reading from /proc/packages (not always available)
    return "uid:" + std::to_string(uid);
}

/**
 * Also try to read /proc/net/tcp to find UID for a given local port.
 * Returns -1 if not found.
 *
 * /proc/net/tcp format:
 *   sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
 *   0: 0F01A8C0:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000   0 12345 1 ...
 */
int resolve_port_to_uid(uint16_t local_port, bool is_tcp) {
    const char* path = is_tcp ? "/proc/net/tcp" : "/proc/net/udp";
    std::ifstream f(path);
    if (!f.is_open()) return -1;

    std::string line;
    std::getline(f, line); // skip header
    while (std::getline(f, line)) {
        std::istringstream ss(line);
        std::string sl, local_addr, rem_addr, state, tx_q, rx_q, tr, tm, retrnsmt;
        int uid;
        if (!(ss >> sl >> local_addr >> rem_addr >> state >> tx_q >> rx_q >> tr >> tm >> retrnsmt >> uid))
            continue;

        // Extract port from local_address (hex format: IP:PORT)
        auto colon = local_addr.find(':');
        if (colon == std::string::npos) continue;
        uint16_t port = static_cast<uint16_t>(std::stoul(local_addr.substr(colon + 1), nullptr, 16));
        if (port == local_port) return uid;
    }
    return -1;
}

extern "C" {

/**
 * Called from Kotlin to push the UID→package name map.
 * JSON format: {"1000": "com.android.system", "10234": "com.youtube.android", ...}
 */
JNIEXPORT void JNICALL
Java_com_asd_firewall_FirewallEngine_nativeSetUidPackageMap(
        JNIEnv* env, jobject /*this*/, jstring jsonMap)
{
    const char* raw = env->GetStringUTFChars(jsonMap, nullptr);
    std::string json(raw);
    env->ReleaseStringUTFChars(jsonMap, raw);

    std::unordered_map<int, std::string> new_map;

    // Simple JSON parsing for {"uid":"pkg",...} format
    size_t pos = 0;
    while ((pos = json.find('"', pos)) != std::string::npos) {
        // Read key (uid)
        size_t key_start = pos + 1;
        size_t key_end   = json.find('"', key_start);
        if (key_end == std::string::npos) break;
        std::string uid_str = json.substr(key_start, key_end - key_start);

        // Skip ":""
        pos = json.find('"', key_end + 1);
        if (pos == std::string::npos) break;
        size_t val_start = pos + 1;
        size_t val_end   = json.find('"', val_start);
        if (val_end == std::string::npos) break;
        std::string pkg = json.substr(val_start, val_end - val_start);

        try {
            int uid = std::stoi(uid_str);
            new_map[uid] = pkg;
        } catch (...) {}

        pos = val_end + 1;
    }

    {
        std::lock_guard<std::mutex> lock(g_uid_mtx);
        g_uid_map = std::move(new_map);
    }

    LOGI("UID map updated: %zu entries", g_uid_map.size());
}

/**
 * Resolve a UID to a package name string (called from Kotlin if needed).
 */
JNIEXPORT jstring JNICALL
Java_com_asd_firewall_FirewallEngine_nativeResolveUid(
        JNIEnv* env, jobject /*this*/, jint uid)
{
    std::string pkg = resolve_uid_to_package(static_cast<int>(uid));
    return env->NewStringUTF(pkg.c_str());
}

} // extern "C"
