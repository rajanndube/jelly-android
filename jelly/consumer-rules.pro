# =============================================================================
#  Jelly SDK — consumer ProGuard / R8 rules
#
#  These rules are bundled into the published .aar via `consumerProguardFiles`
#  in jelly/build.gradle.kts and are merged into the consumer app's R8 step
#  automatically. The goal is "Jelly works under R8 with the same behavior as
#  unminified" — nothing the SDK relies on at runtime should be stripped,
#  renamed, or merged.
#
#  Categories:
#    1. Public API surface
#    2. Reflectively-loaded classes (Class.forName, asTree, etc.)
#    3. kotlinx.serialization wire-format types
#    4. Compose ui-tooling-data slot-tree reflective access
#    5. Stack-walker source attribution requires SourceFile/LineNumberTable
#    6. Suppress benign warnings on optional / unreferenced transitive deps
# =============================================================================


# ─── 1. Public API surface ──────────────────────────────────────────────────
#
# Kept aggressively (with members) so reflection in Kotlin generated code
# (data class copy/equals, default-arg synthetics, sealed class hierarchy
# enumeration) all keeps working on the consumer's release build.

-keep public class dev.jelly.Jelly { *; }
-keep public class dev.jelly.JellyConfig { *; }
-keep public class dev.jelly.JellyConfig$* { *; }
-keep public class dev.jelly.JellyFileProvider { *; }

# Public top-level Modifier extension and its semantics property key.
# Kotlin compiles the file-level `Modifier.jellySource()` extension into
# `dev.jelly.capture.JellySourceRegistryKt`; the property key is an
# object that must keep its INSTANCE / static field intact for the
# semantics tree lookup at hit-test time to find the tagged sources.
-keep public class dev.jelly.capture.JellySourceRegistryKt { *; }
-keep public class dev.jelly.capture.SourceInfo { *; }
-keep public class dev.jelly.capture.JellySourceKey { *; }

# Public sync API + exception type. Consumers wiring custom MCP endpoints
# may reference these directly, and ktor's serialization needs the
# nested `@Serializable` request/response types kept (see section 3).
-keep public class dev.jelly.sync.JellyApi { *; }
-keep public class dev.jelly.sync.JellyApi$* { *; }
-keep public class dev.jelly.sync.JellyApiException { *; }

# Public output / theme types. Referenced from JellyConfig defaults and
# from the toolbar's settings sheet at runtime.
-keep public class dev.jelly.output.OutputGenerator { *; }
-keep public class dev.jelly.output.DetailLevel { *; }
-keep public class dev.jelly.output.DetailLevel$* { *; }
-keep public class dev.jelly.theme.AccentColor { *; }
-keep public class dev.jelly.theme.AccentColor$* { *; }
-keep public class dev.jelly.theme.JellyTheme { *; }
-keep public class dev.jelly.theme.JellyThemeKt { *; }
-keep public class dev.jelly.theme.JellyMotion { *; }


# ─── 2. Reflectively-loaded classes ─────────────────────────────────────────
#
# `resolveCompositionInspector()` does:
#     Class.forName("dev.jelly.capture.CompositionInspectorDebug")
#         .getDeclaredConstructor()
#         .newInstance()
#
# Without this rule R8 inlines/removes the class as "unreachable" and the
# slot-tree fallback silently disappears in consumers' minified builds.

-keep class dev.jelly.capture.CompositionInspectorDebug { *; }
-keepclassmembers class dev.jelly.capture.CompositionInspectorDebug {
    public <init>();
}

# Public interface implemented by the inspector. Kept for the
# Class.forName cast and so the no-op fallback is safe to instantiate.
-keep public class dev.jelly.capture.CompositionInspector { *; }
-keep public class dev.jelly.capture.CompositionInspector$* { *; }
-keep class dev.jelly.capture.NoopCompositionInspector { *; }

# `view.tag === JellyOverlayMarker` is an identity check against the
# singleton. R8 must not collapse the object into a constant or strip
# its INSTANCE field — both would break the "skip our own overlay
# subtree" guard in SemanticsCapture.captureInWindow.
-keep class dev.jelly.JellyOverlayMarker { *; }
-keepclassmembers class dev.jelly.JellyOverlayMarker {
    public static *** INSTANCE;
}

# CapturedElement is read through public displayName/elementPath getters
# from caller code that may live outside dev.jelly.* (e.g. the embedded
# `Jelly { content }` callbacks). Kept defensively.
-keep public class dev.jelly.capture.CapturedElement { *; }


# ─── 3. kotlinx.serialization — wire-format-locked types ────────────────────
#
# The protocol with the MCP `/sessions` endpoint and the on-disk DataStore
# format both go through kotlinx.serialization. Any serializer R8 strips
# turns into a runtime `MissingFieldException` or "no serializer found"
# crash — and we'd only learn about it when a user shares an annotation
# from a release build.

# Generated $serializer classes for every @Serializable in dev.jelly.model.
-keep class dev.jelly.model.** { *; }
-keep class dev.jelly.model.**$$serializer { *; }
-keepclassmembers class dev.jelly.model.**$$serializer {
    public static *** INSTANCE;
}
-keepclassmembers class dev.jelly.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Same pattern for the @Serializable request/response wrappers nested
# inside JellyApi (CreateSessionRequest, ActionRequest, ActionResponse,
# ActionResponse$Delivered).
-keep class dev.jelly.sync.**$$serializer { *; }
-keepclassmembers class dev.jelly.sync.JellyApi$* {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Generic kotlinx.serialization rules — recommended by JetBrains for any
# project using it. Bundled here so consumers who don't already apply
# them still get correct behavior.
# https://github.com/Kotlin/kotlinx.serialization#android
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class kotlinx.serialization.**$$serializer { *; }
-keepclassmembers class kotlinx.serialization.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum @Serializable values — preserve values() / valueOf() and any
# @SerialName annotation metadata.
-keepclassmembers enum dev.jelly.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ─── 4. Compose ui-tooling-data — reflective slot-tree access ───────────────
#
# `CompositionInspectorDebug` walks the slot tree purely via reflection
# (Class.forName + getMethods). When the consumer ships ui-tooling-data
# (typically `debugImplementation`), R8 must not rename the entry points
# we look up by name. These rules are no-ops when ui-tooling-data is
# absent.

-keep class androidx.compose.ui.tooling.data.SlotTreeKt {
    public static *** asTree(...);
}
-keepclassmembers class androidx.compose.ui.tooling.data.* {
    public *** getName();
    public *** getLocation();
    public *** getBox();
    public *** getChildren();
}
-keepclassmembers class androidx.compose.ui.tooling.data.SourceLocation {
    public *** getSourceFile();
    public *** getLineNumber();
}

# Compose-internal RootForTest is referenced via `is RootForTest` to detect
# AndroidComposeView roots inside the View tree. instanceof checks survive
# obfuscation, but keep the marker explicitly so cross-version Compose UI
# upgrades don't silently break the cast.
-keep class androidx.compose.ui.node.RootForTest { *; }


# ─── 5. Stack-walker source attribution ─────────────────────────────────────
#
# `detectHostSource()` reads StackTraceElement.fileName / lineNumber to
# auto-fill the `Source: MainActivity.kt:36` line on every annotation.
# If the consumer's R8 step strips SourceFile / LineNumberTable, every
# annotation falls back to "no source" attribution — the toolbar still
# works, but the most useful agent context is missing.
#
# Most apps already keep these for readable crash stacks, but include the
# rule defensively.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile


# ─── 6. Suppress benign warnings on optional / unused transitive deps ───────
#
# ui-tooling-data is reflectively accessed only — never imported. ktor's
# CIO / Android / Java / Jetty / curl engines are referenced by class
# loaders we never invoke (we use OkHttp). slf4j is a soft-optional dep
# of ktor that consumers typically don't ship.

-dontwarn androidx.compose.ui.tooling.data.**
-dontwarn io.ktor.client.engine.cio.**
-dontwarn io.ktor.client.engine.android.**
-dontwarn io.ktor.client.engine.java.**
-dontwarn io.ktor.client.engine.jetty.**
-dontwarn io.ktor.client.engine.curl.**
-dontwarn io.ktor.network.tls.**
-dontwarn org.slf4j.**

# Coroutines: ServiceLoader lookups for the main dispatcher. The rules
# bundled with kotlinx-coroutines-android already cover this, but
# duplicate them here so consumers on older toolchains aren't bitten.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
