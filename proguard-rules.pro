# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep ExoPlayer classes
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Keep MediaSession classes
-keep class android.support.v4.media.** { *; }
-keep interface android.support.v4.media.** { *; }
