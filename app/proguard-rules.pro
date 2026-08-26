# Add project specific ProGuard rules here.

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room entities
-keep class app.otakureader.core.database.entity.** { *; }

# Keep serialization
-keep class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep custom Coil 3 Decoder implementations loaded reflectively by the ImageLoader pipeline.
# Without this, R8 would strip Factory/create() methods that Coil discovers at runtime.
-keep class * implements coil3.decode.Decoder { *; }
-keep class * implements coil3.decode.Decoder$Factory { *; }

# NOTE: Firebase/Firestore rules were removed — Firebase is not a project dependency.
# If Firebase is added in the future, re-add the appropriate keep rules.

# Keep Glance widget entry points and AppWidget subclasses
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep @dagger.hilt.EntryPoint interface * { *; }

# Extension system — classloaded dynamically at runtime
-keep class app.otakureader.core.extension.** { *; }
-keep class * implements app.otakureader.domain.extension.Extension { *; }
-keep class app.otakureader.core.tachiyomi.compat.** { *; }

# Strip verbose/debug logging from release builds.
#
# This is what the (unused) core/common Logger abstraction was for: android.util.Log.d and .v are
# NOT compiled out by the platform, so every one of them runs in release, building its message
# string and writing manga titles, source names and URLs to logcat. 49 files call android.util.Log
# directly and none ever adopted the injectable Logger, so the fix belongs here — one rule that
# covers every call site, plus the libraries, instead of an interface each class must remember to
# take.
#
# Only d/v. i/w/e are deliberately kept: they are the diagnostics that make a user's bug report
# useful, and CrashHandler's report is separate from them.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# jsoup 1.22.1 declares re2j as an OPTIONAL dependency (verified in its published POM:
# <optional>true</optional>), so the artifact is not on the classpath and org.jsoup.helper.Re2jRegex
# can never be reached. Without this, R8 treats the dangling reference as an error and the release
# build fails outright — which it did, on main, meaning `release.yml` would have failed on the next
# version tag. Debug builds never run R8, so nothing on a PR would have caught it.
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
