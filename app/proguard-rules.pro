# ProGuard rules for AEGIS XII

# ── Keep JNI bridge ──────────────────────────────────────────────
# Prevent ProGuard from renaming or removing classes/methods called via JNI
-keep class com.aegisxii.firewall.FirewallEngine {
    native <methods>;
    public *;
}

# ── Keep VPN Service ─────────────────────────────────────────────
-keep class com.aegisxii.firewall.AegisVpnService { *; }
-keep class com.aegisxii.firewall.BootReceiver { *; }

# ── Keep data classes (used with Gson) ───────────────────────────
-keep class com.aegisxii.firewall.ui.viewmodel.** { *; }

# ── Gson ─────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# ── AndroidX / Compose ───────────────────────────────────────────
-keep class androidx.** { *; }
-dontwarn androidx.**

# ── General rules ────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
