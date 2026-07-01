cmake_minimum_required(VERSION 3.22.1)
project("aegisxii_jni" CXX)

# ── C++17 required (same as desktop) ────────────────────────────
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ── Paths to the bundled firewall C++ sources ───────────────────
# All sources are now self-contained inside the Android project.
set(FIREWALL_CORE "${CMAKE_SOURCE_DIR}/firewall_core")
set(FIREWALL_SRC  "${FIREWALL_CORE}/src")
set(FIREWALL_INC  "${FIREWALL_CORE}/include")

message(STATUS "Firewall core: ${FIREWALL_CORE}")

# ── Android-specific defines ────────────────────────────────────
add_definitions(
    -DANDROID_BUILD=1          # Enables Android code paths
    -D_ANDROID=1
    -UHAVE_NFQUEUE             # Explicitly undefine NFQ (not available on Android)
    -U_WIN32                   # Never set on Android anyway, but be explicit
)

# ── Collect portable C++ source files ───────────────────────────
# Only include modules that compile cleanly on Android (no NFQ, no Win32, no httplib)
set(FIREWALL_SOURCES
    "${FIREWALL_SRC}/rule_engine.cpp"
    "${FIREWALL_SRC}/dpi_engine.cpp"
    "${FIREWALL_SRC}/logger.cpp"
    "${FIREWALL_SRC}/config_parser.cpp"
    "${FIREWALL_SRC}/port_scan_detector.cpp"
    "${FIREWALL_SRC}/packet.cpp"
)

# ── Android-specific C++ sources (in this directory) ────────────
set(JNI_SOURCES
    "${CMAKE_SOURCE_DIR}/aegis_jni.cpp"
    "${CMAKE_SOURCE_DIR}/android_packet_parser.cpp"
    "${CMAKE_SOURCE_DIR}/android_process_resolver.cpp"
)

# ── Build the shared library ─────────────────────────────────────
add_library(
    aegisjni
    SHARED
    ${FIREWALL_SOURCES}
    ${JNI_SOURCES}
)

# ── Include paths ────────────────────────────────────────────────
target_include_directories(aegisjni PRIVATE
    ${FIREWALL_INC}          # All desktop headers (rule_engine.hpp, types.hpp, etc.)
    ${CMAKE_SOURCE_DIR}      # Local android headers (android_compat.hpp etc.)
)

# ── Link against Android system libraries ────────────────────────
target_link_libraries(aegisjni
    android          # Android-specific APIs
    log              # __android_log_print → logcat
)

# ── Compiler flags ───────────────────────────────────────────────
target_compile_options(aegisjni PRIVATE
    -fexceptions
    -frtti
    -Wall
    -Wextra
    -Wno-unused-parameter
    -Wno-missing-field-initializers
    -O2
)
