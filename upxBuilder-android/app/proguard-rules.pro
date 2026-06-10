# upxBuilder R8 / ProGuard rules.
#
# R8 shrinks and obfuscates the release build by default. Jetpack Compose and
# the AndroidX libraries ship their own keep rules, so most of this works out of
# the box. The rules below only protect things the app relies on at runtime.

# Keep enum constant names — they are shown in the UI / console (e.g. build
# actions, language fallbacks), so they must not be renamed by the obfuscator.
-keepclassmembers enum com.upx.builder.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# The single Activity entry point referenced from the manifest.
-keep class com.upx.builder.MainActivity { *; }

# Silence harmless warnings from optional/transitive annotations.
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**
