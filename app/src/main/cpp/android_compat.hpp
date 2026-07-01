/**
 * android_compat.hpp
 *
 * Android-specific compatibility shims for APIs that differ slightly
 * between the desktop build and the Android NDK build.
 *
 * This header is included ONLY in the Android JNI build (via CMakeLists.txt
 * ANDROID_BUILD=1 define) and should never be included in the desktop build.
 */

#pragma once
#include <chrono>
#include <string>
#include <sstream>
#include <iomanip>
#include <ctime>

namespace fw {

/**
 * Make a timestamp string in ISO-like format (mirrors Logger::timestamp()).
 * Defined here because Logger::timestamp() is private.
 */
inline std::string make_android_timestamp() {
    auto now = std::chrono::system_clock::now();
    auto t   = std::chrono::system_clock::to_time_t(now);
    auto ms  = std::chrono::duration_cast<std::chrono::milliseconds>(
                   now.time_since_epoch()) % 1000;
    std::ostringstream ss;
    ss << std::put_time(std::localtime(&t), "%Y-%m-%dT%H:%M:%S")
       << '.' << std::setfill('0') << std::setw(3) << ms.count();
    return ss.str();
}

} // namespace fw

// Expose as Logger::make_timestamp() alias for JNI code
namespace fw {
    class Logger;
    // Free function alias used in aegis_jni.cpp
    inline std::string logger_timestamp() { return make_android_timestamp(); }
}
