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

# ML Kit - Text Recognition and Image Labeling
# Keep ML Kit classes to prevent runtime failures when models are loaded via reflection
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.odml.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_common.**

# Firebase Firestore
# Keep Firebase and Firestore classes for proper initialization and serialization
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.** { *; }
-keepnames class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.**

# Keep Firestore model classes when they are created
# Uncomment and replace with actual package when Firestore models are added:
# -keep class app.otakureader.data.ai.model.** { *; }

# Keep Firebase annotations
-keepclassmembers class * {
    @com.google.android.gms.common.annotation.Keep *;
}
-keepclassmembers class * {
    @com.google.firebase.components.ComponentRegistrar <init>(...);
}
