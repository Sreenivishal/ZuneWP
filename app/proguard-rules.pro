
-keep class com.coremedia.iso.** { *; }
-keep class com.googlecode.mp4parser.** { *; }
-dontwarn com.googlecode.mp4parser.**

# JSoup optional dependency on RE2J
-dontwarn com.google.re2j.**

# Ignore missing ScriptEngineFactory
-dontwarn javax.script.**

# Ignore missing java.beans APIs on Android
-dontwarn java.beans.**


