# Jelly for Android

[![Maven Central](https://img.shields.io/maven-central/v/com.rajandube/jelly.svg?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/com.rajandube/jelly)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](#prerequisites)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.12-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/github/license/rajanndube/jelly-android)](LICENSE)

A debug-only QA-annotation toolbar for Android Compose apps. Long-press any element on screen, drop a comment, and share to Slack, the clipboard, or your MCP server as structured markdown plus a baked image.

Jelly for Android is the native Kotlin / Jetpack Compose member of the Jelly family, alongside the [iOS](https://github.com/rajanndube/jelly-swift) and web SDKs. The output markdown contract and `/sessions` API are byte-identical across all three clients, so the same downstream agents work everywhere.

> **Published on Maven Central:** `com.rajandube:jelly:0.1.0`. [Browse on Sonatype](https://central.sonatype.com/artifact/com.rajandube/jelly) or the [direct repository listing](https://repo1.maven.org/maven2/com/rajandube/jelly/).

```
┌─────────────────────────────┐
│                             │
│     [Host app content]      │     Long-press → live stroke rectangle
│                             │     Release → comment popup
│                             │     Share → markdown + image
│                       (FAB) │
└─────────────────────────────┘
```

- **Zero per-screen wiring.** Install once at the `Application` level, and every activity gets the toolbar automatically.
- **Compose and XML View hit-testing.** Works on screens that mix Compose composables with Android Views.
- **Debug-only.** Gated to debug builds via `debugImplementation`. Never ships in release.
- **Source attribution.** Automatic via stack-walk, with per-screen overrides via `Modifier.jellySource("File.kt", 42)`.
- **Self-contained shareable image.** Element bounds, label, source line, and comment all baked into the screenshot.

---

## Integration in five steps

The whole integration is roughly **40 lines** spread across five files. Copy-paste blocks below.

### Prerequisites

- Android app using Compose (any module structure or build flavors).
- An `Application` subclass (or willingness to add one).
- AGP, Kotlin, Compose BOM, `compileSdk`, and Gradle wrapper aligned with `jelly-android/gradle/libs.versions.toml`. Bump the SDK side to match the host if needed; composite builds **cannot** mix AGP versions.

### Step 1: Repository

Maven Central is included by default in most Android projects, so usually there is nothing to do. If your project's `settings.gradle.kts` has a `dependencyResolutionManagement` block, double-check it lists `mavenCentral()`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()  // Jelly resolves from here
    }
}
```

For local SDK development against an unpublished branch, see [SDK development](#sdk-development) at the bottom of this file. `includeBuild` keeps your edits live without a publish step.

### Step 2: Dependency

In the app module's `build.gradle.kts` (or `<module>.gradle.kts` if the project uses custom build-file naming):

```kotlin
dependencies {
    debugImplementation("com.rajandube:jelly:0.1.0")
}
```

`debugImplementation` keeps Jelly out of release builds. To include it in a `qa` flavor, add `qaImplementation(...)`, or rely on `matchingFallbacks += listOf("debug")` if the flavor already falls back to debug.

### Step 3: Source-set wiring for the no-op stub

Still in the app module's build file, inside `android { sourceSets { … } }`:

```kotlin
android {
    sourceSets {
        // Every non-debug variant gets the no-op stub from src/notDebug/java.
        // Debug uses src/debug/java which overrides with the real install.
        listOf("release", /* + your other non-debug variants */).forEach { variant ->
            getByName(variant).java.srcDir("src/notDebug/java")
        }
    }
}
```

List **every** non-debug build type your project defines (`release`, `qa`, `staging`, `dev_test`, and so on). Use `kotlin.srcDir(…)` if the project's source folders are `kotlin/` rather than `java/`.

> **Why a `notDebug/` folder, not just `release/`?** AGP **adds** `src/<buildType>/` to `src/main/`; it does not replace. If we put the real wrapper in `main/` and overrode it in `debug/`, debug builds would compile *both* and fail with "Conflicting overloads". Keeping a no-op stub in a custom `notDebug/` folder, wired into every non-debug source set, gives each variant exactly one definition.

### Step 4: Two stub files

Pick a package. The convention is `<your.package>.devtools`. Create these two files (replace `your.package` with yours):

**`app/src/notDebug/java/your/package/devtools/QaInstaller.kt`** (no-op stub):

```kotlin
package your.package.devtools

import android.app.Application

fun installQaTools(application: Application) {
    // No-op in non-debug builds.
}
```

**`app/src/debug/java/your/package/devtools/QaInstaller.kt`** (real install):

```kotlin
package your.package.devtools

import android.app.Application
import dev.jelly.Jelly
import dev.jelly.JellyConfig

fun installQaTools(application: Application) {
    Jelly.install(
        application = application,
        config = JellyConfig(),
    )
}
```

Using the same function name in both source sets means call-site code compiles regardless of variant; the linker picks the right one. The `dev.jelly.*` import only resolves in debug, which is exactly what you want.

### Step 5: Call from `Application.onCreate`

In your `Application` subclass:

```kotlin
import your.package.devtools.installQaTools

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installQaTools(this)
    }
}
```

Build and install:

```bash
./gradlew :app:installDebug
```

The toolbar appears as a draggable FAB in the bottom-right of every activity.

---

## Smoke test

1. Launch the debug build. A small dark pill with a location-pin icon should appear at the bottom-right.
2. Drag the pill anywhere, then release. It springs to whichever screen edge (left or right) is closer.
3. Tap the pin. The toolbar expands with review, settings, and close actions.
4. Tap the pin again to enable annotate-mode (the pin turns the accent color).
5. Long-press any UI element. A live stroke rectangle should follow your finger, snapping to the deepest semantic element under it. Drag to refine the selection.
6. Release. The screenshot captures, and a popup appears with the captured region preview.
7. Type a comment, choose intent and severity, then tap Add. A numbered marker appears at the captured spot.
8. Open the toolbar's review screen to see all annotations with thumbnails. Share to send the image and markdown to Slack or wherever else.

If the FAB does not appear, check that `com.rajandube:jelly` is on the classpath at runtime:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep jelly
```

---

## Configuration

`JellyConfig` accepts:

```kotlin
JellyConfig(
    detailLevel = DetailLevel.Standard,        // Compact, Standard, Detailed, or Forensic
    accentColor = AccentColor.Indigo,          // 7 colors available
    endpoint = "https://your-mcp.example.com", // optional MCP /sessions sync
    sessionId = null,                          // resume a known session
    webhookUrl = null,                         // optional outbound webhook
    copyToClipboard = true,
    captureScreenshots = true,
    screenKey = null,                          // override the per-activity storage key
)
```

Most settings are also exposed as a runtime UI in the toolbar's settings sheet. What you pass in `JellyConfig` is just the default before the user changes it.

---

## Source attribution

Every annotation includes a `Source: SomeFile.kt:42` line. Three resolvers run in priority order:

1. **`Modifier.jellySource("LoginScreen.kt", 42)`**. Manual and most precise. Tag a screen-root composable, and everything inside inherits.
   ```kotlin
   Column(Modifier.jellySource("LoginScreen.kt", 42)) { … }
   ```
2. **`ui-tooling-data` slot tree.** Debug-only, best-effort reflective lookup of composable name and source.
3. **Activity stack-walk.** Automatic fallback. Walks the call stack at first composition and returns the first frame outside `dev.jelly.*`, Compose, or Kotlin internals. The result: `Source: MainActivity.kt:36` shows up automatically with zero per-screen tagging.

You do not need to do anything for source attribution to work. Reach for `Modifier.jellySource()` only when you want sub-screen precision.

---

## How element identification works

The SDK runs **two parallel hit-tests** on long-press and picks whichever is more specific:

1. **Compose semantics tree.** Rich content (text, role, `contentDescription`), but only sees nodes with explicit semantics modifiers.
2. **`ui-tooling-data` slot tree.** Captures every composable scope regardless of semantics. This is useful for server-driven UIs where widgets are built from plain `Box` and `Column` layouts without semantic hints.

When the slot-tree hit's bounds are notably tighter than the semantic hit's, it is preferred. This is what makes the toolbar work on apps where a `verticalScroll` Column would otherwise swallow every press as "the whole scroll container".

For XML or `View`-based screens, a separate **View-tree walk** finds the deepest visible View at the press point and extracts class name, resource id, content description, and text directly. This means apps that mix Compose and XML get precise hits on whichever paradigm owns the pressed element.

Hidden views (`alpha=0`, `visibility != VISIBLE`, zero-sized layouts, or "transparent shell" container `ViewGroup`s whose children are all invisible) are filtered out, so a backend-flag-driven hidden overlay cannot steal hits from the visible widget underneath.

---

## Output format

Annotations are exported as markdown with structured fields:

```markdown
**Element:** Button "Submit"
**Location:** Column > Card > Row > Button
**Source:** LoginScreen.kt:78
**Position:** 50% × 612px
**Feedback:** This should be primary, not secondary
**Intent:** change · **Severity:** important
```

The export also includes a screenshot with the element bounds drawn in the accent color and a caption strip baking the metadata into the image, so receivers that drop `EXTRA_TEXT` (Slack, WhatsApp) still see the context.

The format is identical to the web version's `generateOutput()`, so the same downstream agents work for both clients.

---

## Known limitations

- **Bottom sheets cover the FAB.** The toolbar lives in a `TYPE_APPLICATION_PANEL` sub-window of the activity (no permissions required), so its layer is `parent.layer + small_offset`. A `BottomSheetDialog` is `TYPE_APPLICATION` at the application base layer, which ends up *higher*. The clean fix requires either hosting the FAB inside a `Dialog` of our own (which gets a fresh window token and shares the dialog layer space), or using `TYPE_APPLICATION_OVERLAY` with `SYSTEM_ALERT_WINDOW` permission. Workaround for now: dismiss the bottom sheet first, then use the FAB.
- **No annotation while a Dialog or BottomSheet is open.** Long-press goes to the dialog's window, not the activity's decor view where our capture overlay lives. Same workaround.
- **React Native bridge screens are invisible to the SDK.** It only reads Compose semantics and Android Views. RN content rendered in a host RN view appears as "the RN container". That is a fundamental limitation of where Jelly can introspect.
- **`ui-tooling-data` is unstable across Compose UI versions.** All reflective access is wrapped in `try/catch` so a version-skew failure degrades to "no source line" rather than crashing.

---

## SDK development

For active SDK development against an unpublished branch (when you are modifying `jelly-android/jelly/src/...` and do not want to bump versions per change), use a Gradle composite build instead of pulling from Maven Central.

In the host's root `settings.gradle.kts`, **before** any `include(":...")` lines:

```kotlin
includeBuild("../jelly-android") {
    dependencySubstitution {
        substitute(module("com.rajandube:jelly"))
            .using(project(":jelly"))
    }
}
```

Adjust the relative path if `jelly-android` lives elsewhere (`../../sdks/jelly-android`, etc.). Edits to the SDK source rebuild on the host's next build, with no publish step.

To publish a new version: bump `version` in [`jelly/build.gradle.kts`](jelly/build.gradle.kts), run [`./build-central-bundle.sh`](build-central-bundle.sh), and upload the resulting zip from `~/Desktop/jelly-<version>-central-bundle.zip` at https://central.sonatype.com/publishing.

---

## Status

`v0.6`: Application-level install pattern, draggable FAB with edge-snap, two-window architecture (capture overlay in decor view plus toolbar in `TYPE_APPLICATION_PANEL` window), View and Compose multi-paradigm hit-testing, slot-tree fallback for sparse-semantics apps, baked share images.

See [`CLAUDE.md`](CLAUDE.md) for repo-internal architecture notes, and [`CHANGELOG.md`](CHANGELOG.md) for the release history. Contributions are welcome — see [`CONTRIBUTING.md`](CONTRIBUTING.md).
