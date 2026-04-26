# ML Kit and Firebase Integration Guide

This document addresses the runtime requirements, dependency exposure, and APK size considerations for ML Kit and Firebase Firestore integration in the `core/ai` module.

## Overview

PR #439 adds the following dependencies to `core/ai`:
- ML Kit Text Recognition v16.0.1
- ML Kit Image Labeling v17.0.9
- Firebase Firestore KTX v25.1.2

## 1. Runtime / Packaging Requirements ✅

### ML Kit Requirements

**No special initialization required.** ML Kit libraries work out-of-the-box with automatic initialization:

- **Bundled Models**: The `com.google.mlkit:text-recognition` and `com.google.mlkit:image-labeling` dependencies include bundled models that are embedded in the APK
- **No Manifest Entries**: ML Kit does not require any special manifest entries or providers
- **No Google Services**: ML Kit on-device APIs do NOT require `google-services.json` or Firebase configuration
- **Play Services**: ML Kit bundled models do NOT require Google Play Services on the device

### Firebase Firestore Requirements

**Current Status**: Firebase Firestore is added as a dependency but:

⚠️ **Important**: The app does NOT currently include `google-services.json` or the Google Services Gradle plugin, which means:
- Firestore will not be functional until Firebase is properly configured
- The dependency is included but dormant
- No runtime crashes will occur from missing configuration (Firestore initialization is lazy)

**To enable Firebase Firestore** (if/when needed):

1. **Add Google Services Plugin** to `app/build.gradle.kts`:
   ```kotlin
   plugins {
       alias(libs.plugins.otakureader.android.application)
       alias(libs.plugins.otakureader.android.hilt)
       alias(libs.plugins.kotlin.serialization)
       id("com.google.gms.google-services") // Add this
   }
   ```

2. **Add google-services.json**:
   - Download from Firebase Console
   - Place in `app/` directory (module root, not project root)
   - Add to `.gitignore` to avoid committing sensitive configuration

3. **Add Plugin Dependency** to `gradle/libs.versions.toml`:
   ```toml
   [versions]
   google-services = "4.4.0"

   [plugins]
   google-services = { id = "com.google.gms.google-services", version.ref = "google-services" }
   ```

4. **Initialize in Application class** (optional, usually automatic):
   ```kotlin
   // Usually not needed; Firebase auto-initializes
   // Only if manual control is required:
   FirebaseApp.initializeApp(this)
   ```

### ProGuard / R8 Rules

The following ProGuard rules have been added to ensure ML Kit and Firebase work correctly with code shrinking and obfuscation:

**Location**: `app/proguard-rules.pro`

```proguard
# ML Kit - Text Recognition and Image Labeling
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.odml.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_common.**

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.** { *; }
-keepnames class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.**

# Keep Firestore model classes (if/when used)
# Replace with actual package when Firestore models are created
# -keep class app.otakureader.data.ai.model.** { *; }

# Keep Firebase annotations
-keepclassmembers class * {
    @com.google.android.gms.common.annotation.Keep *;
}
-keepclassmembers class * {
    @com.google.firebase.components.ComponentRegistrar <init>(...);
}
```

## 2. Dependency Exposure ✅

### Current Configuration

**`core/ai/build.gradle.kts`:**
```kotlin
dependencies {
    // Gemini AI SDK - exposed via api()
    api(libs.generativeai)

    // ML Kit - implementation (not exposed)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.image.labeling)

    // Firebase Firestore - implementation (not exposed)
    implementation(libs.firebase.firestore.ktx)
}
```

### Analysis

**✅ Correct Configuration:**

1. **Gemini SDK (`api`)**: Correctly exposed because:
   - `GeminiClient` is a public singleton injected via Hilt
   - Consumers may need `GenerativeModel` types in method signatures
   - The SDK is the primary API surface of `core/ai`

2. **ML Kit (`implementation`)**: Correctly hidden because:
   - ML Kit types should be internal implementation details
   - Any AI features using ML Kit should expose domain-specific types, not raw ML Kit classes
   - Prevents version conflicts if other modules use ML Kit independently

3. **Firestore (`implementation`)**: Correctly hidden because:
   - Firestore should be an implementation detail for AI data storage
   - Domain layer defines repository interfaces; data layer uses Firestore
   - Prevents tight coupling to Firebase across the codebase

### When to Use `api` vs `implementation`

**Use `api` when:**
- Types from the dependency appear in public method signatures
- Consumers need compile-time access to the dependency's classes
- Example: Gemini SDK types in `GeminiClient` public API

**Use `implementation` when:**
- The dependency is an internal implementation detail
- Consumers interact via abstraction layers (repositories, use cases)
- Example: ML Kit and Firestore hidden behind domain interfaces

### Verification

No changes needed. The current dependency exposure is correct and follows best practices.

## 3. APK Size / Modularity ⚠️

### APK Size Impact

**ML Kit Bundled Models:**
- **Text Recognition**: ~4 MB per script (Latin script)
- **Image Labeling**: ~5.7 MB
- **Total ML Kit Impact**: ~9.7 MB to base APK

**Firebase Firestore:**
- **SDK Size**: ~1-2 MB (includes dependencies)
- **Total Impact**: ~11-12 MB to base APK

### Current Architecture

**Flavor-based Separation:**
```kotlin
// app/build.gradle.kts
productFlavors {
    create("full") {
        dimension = "distribution"
    }
    create("foss") {
        dimension = "distribution"
        applicationIdSuffix = ".foss"
    }
}

dependencies {
    "fullImplementation"(projects.core.ai)      // Includes ML Kit, Firebase
    "fossImplementation"(projects.core.aiNoop)  // No AI dependencies
}
```

**✅ APK Size is Already Optimized:**

1. **FOSS build excludes AI entirely**: Users who don't need AI features can use the FOSS variant
2. **Full build includes all AI features**: Users who opt-in get the complete experience
3. **Flavor-based separation**: No need for additional feature modules

### Recommendations

**Option A: Keep Current Architecture (Recommended)**

**Pros:**
- Simple to maintain
- Clear separation: Full vs FOSS
- All AI features in one module
- Users choose at install time

**Cons:**
- Full build always includes ML Kit (~12 MB overhead)
- Cannot disable individual AI features post-install

**When to use:**
- AI features are core value proposition
- Most full-build users will use AI features
- Simplicity is preferred over granular control

**Option B: Split into Feature Modules (Not Recommended)**

Create separate modules:
```
core/ai-gemini    (Gemini SDK only, ~1 MB)
core/ai-mlkit     (ML Kit, ~9.7 MB)
core/ai-firebase  (Firestore, ~1-2 MB)
```

**Pros:**
- Fine-grained control over APK size
- Could use dynamic feature modules for on-demand download

**Cons:**
- Significant complexity increase
- More Hilt modules to manage
- Harder to maintain
- Overkill unless user base demands it

**When to use:**
- User analytics show most users don't use ML features
- APK size is critical business metric
- Willing to invest in complexity

### Alternative: Unbundled ML Kit Models

**Reduce APK Size Without Module Splitting:**

Change dependencies to use Play Services versions:

```kotlin
// Instead of bundled models (current):
implementation(libs.mlkit.text.recognition)         // 4 MB
implementation(libs.mlkit.image.labeling)           // 5.7 MB

// Use unbundled models:
implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")  // 260 KB
implementation("com.google.android.gms:play-services-mlkit-image-labeling:16.0.9")   // 200 KB
```

**Trade-offs:**
- **APK Size**: Reduces from ~9.7 MB to ~460 KB (95% reduction!)
- **First Use**: Models download at runtime (~9.7 MB download once)
- **Offline**: Not available until model downloads
- **Play Services**: Requires Google Play Services on device

**Recommendation**: Consider unbundled models if:
- APK size is a concern
- Internet connectivity is expected on first use
- Full build users have Google Play Services (most do)

## 4. Testing Checklist

Before merging PR #439, verify:

- [ ] **Build succeeds** with `./gradlew assembleFullRelease`
- [ ] **ProGuard rules work**: Test release build with minification enabled
- [ ] **ML Kit loads**: Verify text recognition and image labeling initialize without errors
- [ ] **Firestore gracefully fails**: Verify app doesn't crash when Firestore is unconfigured (expected: lazy initialization, no immediate error)
- [ ] **FOSS build works**: Verify `./gradlew assembleFossRelease` excludes AI dependencies
- [ ] **APK size measured**: Compare Full vs FOSS build sizes to confirm ~12 MB difference

## 5. Future Considerations

### When Firebase is Fully Integrated

If/when Firestore is actively used:

1. **Add google-services.json** (see section 1)
2. **Create Firestore models** with proper keep rules
3. **Document Firestore schema** in this file or separate doc
4. **Add Firestore-specific rules** to protect model classes from obfuscation

### When ML Kit is Used

When implementing features that use ML Kit:

1. **Wrap in domain abstractions**: Don't expose ML Kit types directly
2. **Handle model availability**: Check if models are ready (especially if switching to unbundled)
3. **Error handling**: Handle potential ML Kit errors (unsupported device, model download failures)
4. **Privacy**: Document what data is processed on-device vs sent to cloud

## References

- [ML Kit Text Recognition Documentation](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [ML Kit Image Labeling Documentation](https://developers.google.com/ml-kit/vision/image-labeling/android)
- [ML Kit Model Installation Paths](https://developers.google.com/ml-kit/tips/installation-paths)
- [Firebase Firestore Android Setup](https://firebase.google.com/docs/android/setup)
- [Android R8 Keep Rules Guide](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview)

## Summary

| Concern | Status | Action Required |
|---------|--------|-----------------|
| **Runtime Requirements** | ✅ Addressed | ProGuard rules added; Firebase setup documented |
| **Dependency Exposure** | ✅ Verified | Current configuration is correct |
| **APK Size Impact** | ⚠️ Documented | ~12 MB increase in full build; FOSS unaffected; consider unbundled models if size is critical |
