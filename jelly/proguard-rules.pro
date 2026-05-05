# =============================================================================
#  Jelly SDK — library-side ProGuard / R8 rules
#
#  These rules apply if and when the library itself is run through R8 (rare
#  for an Android library — minification typically happens in the consuming
#  app). They are a superset of consumer-rules.pro: same rules consumers
#  inherit, plus any internals we need to keep when the .aar is ever
#  shipped pre-minified.
#
#  In practice, our published .aar is non-minified and the consumer app's
#  R8 sees Jelly's bytecode untouched — the rules below are defensive in
#  case a build tool flips on `isMinifyEnabled` for the library variant.
# =============================================================================

# Inherit every consumer rule. AGP applies consumer-rules.pro automatically
# at the consumer side, but for our own minification we have to load it
# explicitly via the library buildType's `proguardFiles` declaration in
# build.gradle.kts. This file complements that.

# ─── Internal-but-reflected types ────────────────────────────────────────
#
# These are not part of the public surface but are referenced via reflection
# / identity comparisons that R8 can't statically prove are required.

-keep class dev.jelly.JellyOverlayMarker { *; }
-keep class dev.jelly.capture.CompositionInspectorDebug { *; }
-keep class dev.jelly.capture.NoopCompositionInspector { *; }
-keep class dev.jelly.capture.CompositionInspector { *; }
-keep class dev.jelly.capture.CompositionInspector$* { *; }

# ─── Keep filename + line metadata for the stack walker ──────────────────
-keepattributes SourceFile, LineNumberTable, *Annotation*, InnerClasses
-renamesourcefileattribute SourceFile

# ─── Suppress noise on reflective probes ─────────────────────────────────
-dontwarn androidx.compose.ui.tooling.data.**
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
