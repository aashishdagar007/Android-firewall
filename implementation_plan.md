# AEGIS XII — Android App (Full Feature Parity, Background Service)

## Background

AEGIS XII is a production-grade C++ kernel firewall with:
- A rule engine (DPI, port-scan detection, anomaly detection, geo-blocking, rate limiting)
- Tamper-proof SHA-256 chain ledger
- BVUDP protocol stack
- Cloud control plane client
- A REST API server (cpp-httplib)
- A rich dark-theme HTML dashboard served at `localhost:8080`

The goal is to create a new `Android/` folder containing a **complete Android app** that:
1. Runs **persistently in the background** (like Spotify) using a VPN Service
2. Provides **full feature parity** with the desktop version
3. Is **Google Play compliant** — no root required, no system-level exploits
4. Ships the same beautiful dark-mode dashboard as a native Android UI

---

## User Review Required

> [!IMPORTANT]
> **Android VPN Approach — No Root Required**
> Android's `VpnService` API (available since API 14) allows any app to create a virtual TUN interface that routes all device traffic through the app. This is the **ONLY Play-compliant way** to intercept and filter traffic without root. Google Play explicitly allows VPN apps using `VpnService`. This is how NetGuard, Blokada, AdGuard (no-root mode), and many others work.
>
> The app will show a "VPN connection request" dialog on first launch — this is a required OS permission prompt and cannot be bypassed.

> [!IMPORTANT]
> **Google Play Compliance Strategy**
> - Use **`VpnService`** (not root, not raw sockets) — fully allowed
> - Use **Foreground Service** with a persistent notification (exactly like Spotify) — required for background operation on Android 8+
> - All C++ firewall logic runs in a **native library (JNI)** inside the app — no external executables
> - The app does **not** intercept HTTPS content (it only looks at IP headers / metadata) — safe for Play policy
> - Declare `FOREGROUND_SERVICE`, `BIND_VPN_SERVICE`, and `INTERNET` permissions — all standard

> [!WARNING]
> **What changes on Android vs Desktop**
> - No `NFQUEUE` (Linux kernel module) — replaced by `VpnService` + TUN interface
> - No `WinDivert` or raw sockets needing root
> - `ProcessMonitor` (Windows-only) → Android equivalent uses `/proc` + `PackageManager` for app attribution
> - The REST API server is internal-only (localhost) — the UI is a native Android screen, not an external browser
> - `BVUDP` receiver (UDP port 9000) will only work on rooted devices for raw inbound; on standard devices it is in observer-mode only (already matches Windows behavior)

---

## Open Questions

> [!NOTE]
> **No blocking questions — proceeding with optimal design decisions:**
> 1. UI: Native Android (Kotlin + Jetpack Compose) — same aesthetic (dark mode, cyan accent, glass panels)
> 2. C++ core: Shared as a JNI `.so` library — reuses `rule_engine`, `logger`, `dpi_engine`, `chain_ledger`, `config_parser`, `port_scan_detector` directly
> 3. Packet capture: Android `VpnService` TUN → reads raw IP packets from the TUN file descriptor, feeds them into the same C++ `RuleEngine::evaluate()` pipeline
> 4. Min SDK: **API 26** (Android 8.0 Oreo) — required for `startForegroundService()`; covers ~95% of active devices
> 5. Target SDK: **API 35** (Android 15) — required for Play submission in 2025

---

## Proposed Changes

### New Top-Level Folder

#### [NEW] `Android/` — Root of the Android project

---

### Gradle & Build System

#### [NEW] [settings.gradle.kts](file:///d:/AASHISH/Projects/Firewall/Android/settings.gradle.kts)
Standard multi-module Gradle Kotlin DSL settings file.

#### [NEW] [build.gradle.kts (root)](file:///d:/AASHISH/Projects/Firewall/Android/build.gradle.kts)
Root build file with AGP + Kotlin version catalogs.

#### [NEW] [app/build.gradle.kts](file:///d:/AASHISH/Projects/Firewall/Android/app/build.gradle.kts)
App module build config: `minSdk 26`, `targetSdk 35`, NDK ABI filters (`arm64-v8a`, `x86_64`), CMake for JNI.

#### [NEW] [app/src/main/cpp/CMakeLists.txt](file:///d:/AASHISH/Projects/Firewall/Android/app/src/main/cpp/CMakeLists.txt)
NDK CMake config that compiles the shared C++ firewall core into `libaegisjni.so`. Symlinks/copies headers from `../../../../../../include/` and sources from `../../../../../../src/` (only the portable, non-Windows/non-NFQ modules).

---

### C++ JNI Bridge (Native Layer)

#### [NEW] [app/src/main/cpp/aegis_jni.cpp](file:///d:/AASHISH/Projects/Firewall/Android/app/src/main/cpp/aegis_jni.cpp)
JNI entry points that expose the C++ firewall engine to Kotlin:
- `Java_..._FirewallEngine_create()` — instantiates `RuleEngine`, `Logger`, `ChainLedger`
- `Java_..._FirewallEngine_evaluatePacket(byte[] rawIpPacket)` — calls `RuleEngine::evaluate()`, returns ALLOW/BLOCK verdict + matched rule
- `Java_..._FirewallEngine_getStats()` — returns JSON stats string
- `Java_..._FirewallEngine_getRules()` — returns JSON rule list
- `Java_..._FirewallEngine_addRule(String json)` — adds a rule
- `Java_..._FirewallEngine_deleteRule(int id)` — removes a rule
- `Java_..._FirewallEngine_getAnomalies()` — hit counts
- `Java_..._FirewallEngine_getConnections()` — connection table
- `Java_..._FirewallEngine_getLedger(int n)` — tamper-proof log
- `Java_..._FirewallEngine_getThreats()` — threat ban table
- `Java_..._FirewallEngine_banIp(String ip)` / `unbanIp(String ip)`
- `Java_..._FirewallEngine_setStealthMode(boolean on)`
- `Java_..._FirewallEngine_destroy()` — clean shutdown

#### [NEW] [app/src/main/cpp/android_packet_parser.cpp](file:///d:/AASHISH/Projects/Firewall/Android/app/src/main/cpp/android_packet_parser.cpp)
Parses raw IP packets read from the TUN fd into `fw::PacketInfo` structs (mirrors `nfq_capture.cpp` logic, no NFQ dependency).

#### [NEW] [app/src/main/cpp/android_process_resolver.cpp](file:///d:/AASHISH/Projects/Firewall/Android/app/src/main/cpp/android_process_resolver.cpp)
Maps UID → app package name via `/proc/net/tcp` + `/proc/net/udp` (same technique used by NetGuard). Passes app name back through JNI to populate `PacketInfo::process_name`.

---

### Android VPN Capture Layer (Kotlin)

#### [NEW] [app/src/main/java/.../AegisVpnService.kt](file:///d:/AASHISH/Projects/Firewall/Android/app/src/main/java/com/aegisxii/firewall/AegisVpnService.kt)
The heart of the Android version. Extends `VpnService`:
- `onStartCommand()` → builds VPN interface (`Builder.establish()`), starts foreground service with a persistent notification (like Spotify)
- Background thread reads raw IP packets from TUN `FileDescriptor` in a loop
- Each packet is passed to `FirewallEngine.evaluatePacket()` (JNI)
- ALLOW → writes packet to TUN output (forwarded to real network via real socket)
- BLOCK → packet is silently dropped (no write-back)
- Supports packet re-injection via a protected socket to the upstream network
- All traffic stats, connection logs, and events are posted to a `SharedFlow` consumed by the UI

#### [NEW] [app/src/main/java/.../FirewallEngine.kt](file:///d:/AASHISH/Projects/Firewall/Android/app/src/main/java/com/aegisxii/firewall/FirewallEngine.kt)
Kotlin singleton wrapping the JNI native library. Loads `libaegisjni.so`, declares all `external fun` JNI bindings, exposes Kotlin-friendly coroutine wrappers.

---

### UI — Jetpack Compose (Same Aesthetic as Desktop)

#### [NEW] [app/src/main/java/.../MainActivity.kt](file:///d:/AASHISH/Projects/Firewall/Android/app/src/main/java/com/aegisxii/firewall/ui/MainActivity.kt)
Single-activity host. Handles VPN permission dialog flow, dark mode theming.

#### [NEW] [app/src/main/java/.../ui/theme/](file:///d:/AASHISH/Projects/Firewall/Android/app/src/main/java/com/aegisxii/firewall/ui/theme/)
- `Theme.kt` — dark background `#030508`, cyan accent `#00E5FF`, exact color palette from desktop dashboard
- `Type.kt` — Outfit + JetBrains Mono fonts (same as dashboard)

#### [NEW] Dashboard Screens (Jetpack Compose)
Mirrors the 8 tabs of the desktop dashboard:

| Screen | Desktop Tab Equivalent |
|---|---|
| `DashboardScreen.kt` | Live Stats, chart, KPI cards |
| `PacketsScreen.kt` | Live packet log (ring buffer) |
| `RulesScreen.kt` | Rule chain CRUD |
| `ProcessesScreen.kt` | App-level traffic attribution |
| `ThreatsScreen.kt` | Threat table + ban/unban |
| `AnomaliesScreen.kt` | 20 anomaly hit counters |
| `ConnectionsScreen.kt` | Live connection tracking |
| `LedgerScreen.kt` | Tamper-proof chain ledger |

Each screen is a `@Composable` that collects from a `ViewModel` backed by the `FirewallEngine` JNI calls + `AegisVpnService` stats flow.

---

### Manifest & Resources

#### [NEW] [app/src/main/AndroidManifest.xml](file:///d:/AASHISH/Projects/Firewall/Android/app/src/main/AndroidManifest.xml)
Key declarations:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<service android:name=".AegisVpnService"
         android:permission="android.permission.BIND_VPN_SERVICE">
  <intent-filter>
    <action android:name="android.net.VpnService" />
  </intent-filter>
</service>
```

#### [NEW] Boot Receiver (`BootReceiver.kt`)
Starts the VPN service on device boot (exactly like Spotify auto-starts on boot).

---

### Symlinked C++ Sources

The Android CMake build will reference the **existing** C++ source files from the parent project via relative paths (`../../../../../src/*.cpp`, `../../../../../include/*.hpp`). Only Android-compatible modules are compiled:

| Module | Android? | Notes |
|---|---|---|
| `rule_engine.cpp` | ✅ | Core logic, no platform deps |
| `dpi_engine.cpp` | ✅ | Pure payload analysis |
| `logger.cpp` | ✅ | File + logcat logging |
| `config_parser.cpp` | ✅ | Rules file parser |
| `port_scan_detector.cpp` | ✅ | Stateless detector |
| `chain_ledger.hpp` | ✅ | Header-only SHA-256 chain |
| `sha256.hpp` | ✅ | Header-only |
| `bvudp.hpp` | ✅ (observer) | Socket ops compile on Android |
| `nfq_capture.cpp` | ❌ | Replaced by Android TUN |
| `process_monitor.cpp` | ❌ | Replaced by `/proc` resolver |
| `api_server.cpp` | ❌ | Replaced by JNI + Compose UI |
| `main.cpp` | ❌ | Replaced by Android entry points |
| `port_demux.hpp` | ❌ | WinDivert-specific |
| `control_plane.hpp` | ✅ (optional) | Cloud sync — can be enabled |

---

### Key Files Summary

```
Android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/
│       │   ├── CMakeLists.txt          ← NDK build
│       │   ├── aegis_jni.cpp           ← JNI bridge
│       │   ├── android_packet_parser.cpp
│       │   └── android_process_resolver.cpp
│       ├── java/com/aegisxii/firewall/
│       │   ├── FirewallEngine.kt       ← JNI wrapper
│       │   ├── AegisVpnService.kt      ← Background VPN service
│       │   ├── BootReceiver.kt
│       │   └── ui/
│       │       ├── MainActivity.kt
│       │       ├── theme/
│       │       │   ├── Theme.kt
│       │       │   └── Type.kt
│       │       └── screens/
│       │           ├── DashboardScreen.kt
│       │           ├── PacketsScreen.kt
│       │           ├── RulesScreen.kt
│       │           ├── ProcessesScreen.kt
│       │           ├── ThreatsScreen.kt
│       │           ├── AnomaliesScreen.kt
│       │           ├── ConnectionsScreen.kt
│       │           └── LedgerScreen.kt
│       └── res/
│           ├── values/
│           │   ├── strings.xml
│           │   └── colors.xml
│           └── drawable/
│               └── ic_shield.xml
```

---

## Verification Plan

### Automated Tests
- `./gradlew test` — Kotlin unit tests for `FirewallEngine` rule parsing
- `./gradlew connectedAndroidTest` — instrumented tests (VPN not testable in emulator; rule logic is)

### Manual Verification
1. Build with Android Studio: `./gradlew assembleDebug`
2. Install APK on a physical Android 8+ device
3. Grant VPN permission on first launch
4. Verify persistent notification persists (like Spotify)
5. Open YouTube or browse web — verify traffic shows up in Live Packets tab
6. Add a BLOCK rule for a specific IP — verify traffic drops
7. Verify ledger entries are written correctly
8. Reboot device — verify service auto-restarts (BootReceiver)
9. Check memory usage stays < 60 MB (native engine is lean)

### Google Play Policy Checklist
- ✅ Uses `VpnService` API only — no root
- ✅ `FOREGROUND_SERVICE` declared — persistent notification always visible
- ✅ No content interception (IP-layer only, no TLS decryption)
- ✅ Privacy policy must be provided in Play Console (not code — user action)
- ✅ Target SDK 35
- ✅ 64-bit native library (`arm64-v8a`) — required since 2019
