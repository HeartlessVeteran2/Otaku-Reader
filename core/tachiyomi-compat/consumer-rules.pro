# ProGuard rules for core:tachiyomi-compat
# Keep Tachiyomi extension interface types so they can be loaded via reflection
-keep interface eu.kanade.tachiyomi.source.** { *; }
-keep class eu.kanade.tachiyomi.source.**$DefaultImpls { *; }
-keep class eu.kanade.tachiyomi.source.model.** { *; }
