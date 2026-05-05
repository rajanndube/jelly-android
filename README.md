# Jelly for Android

A debug-only QA-annotation toolbar for Android Compose apps. Long-press any element on screen, drop a comment, share to Slack / clipboard / your MCP server as structured markdown + a baked image.

```
┌─────────────────────────────┐
│                             │
│     [Host app content]      │     Long-press → live stroke rectangle
│                             │     Release → comment popup
│                             │     Share → markdown + image
│                       (FAB) │
└─────────────────────────────┘
```

- **Zero per-screen wiring** — install once at the `Application` level, every activity gets the toolbar automatically.
- **Compose + XML View hit-testing** — works on screens that mix Compose composables and Android Views.
- **Debug-only** — gated to debug builds via `debugImplementation`. Never ships in release.
- **Source attribution** — automatic via stack-walk; per-screen overrides via `Modifier.jellySource("File.kt", 42)`.
- **Self-contained shareable image** — element bounds, label, source line, comment all baked into the screenshot.

---

## Integration in five steps

The whole integration is roughly **40 lines** spread across five files. Copy-paste blocks below.

### Prerequisites

- Android app using Compose (any module structure / build flavors).
- An `Application` subclass (or willingness to add one).
- AGP, Kotlin, Compose BOM, compileSdk, and Gradle wrapper aligned with `jelly-android/gradle/libs.versions.toml`. Bump the SDK side to match the host if needed — composite builds **cannot** mix AGP versions.

### Step 1 — Composite build

In the host's root `settings.gradle.kts`, **before** any other `include(":...")` lines:

```kotlin
includeBuild("../jelly-android") {
    dependencySubstitution {
        substitute(module("com.rajandube:jelly"))
            .using(project(":jelly"))
    }
}
```

Adjust the relative path if `jelly-android` lives somewhere else (`../../sdks/jelly-android`, etc.).

### Step 2 — Dependency

In the app module's `build.gradle.kts` (or `<module>.gradle.kts` if the project uses custom build-file naming):

```kotlin
dependencies {
    debugImplementation("com.rajandube:jelly:0.1.0")
}
```

`debugImplementation` keeps it out of release. If you want it in a `qa` flavor too, add `qaImplementation(...)` — or rely on `matchingFallbacks += listOf("debug")` if the flavor already falls back to debug.

### Step 3 — Source-set wiring for the no-op stub

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

List **every** non-debug build type your project defines (`release`, `qa`, `staging`, `dev_test`, …). Use `kotlin.srcDir(…)` if the project's source folders are `kotlin/` rather than `java/`.

> **Why a `notDebug/` folder, not just `release/`?** AGP **adds** `src/<buildType>/` to `src/main/`; it doesn't replace. If we put the real wrapper in `main/` and overrode in `debug/`, debug builds compile *both* and fail with "Conflicting overloads". Keeping a no-op stub in a custom `notDebug/` folder, wired into every non-debug source set, gives each variant exactly one definition.

### Step 4 — Two stub files

Pick a package — convention is `<your.package>.devtools`. Create these two files (replace `your.package` with yours):

**`app/src/notDebug/java/your/package/devtools/QaInstaller.kt`** — no-op stub:

```kotlin
package your.package.devtools

import android.app.Application

fun installQaTools(application: Application) {
    // No-op in non-debug builds.
}
```

**`app/src/debug/java/your/package/devtools/QaInstaller.kt`** — real install:

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

The same function name in both source sets means call-site code compiles regardless of variant; the linker picks the right one. The `dev.jelly.*` import only resolves in debug, which is exactly what you want.

### Step 5 — Call from `Application.onCreate`

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

That's it. Build and install:

```bash
./gradlew :app:installDebug
```

The toolbar appears as a draggable FAB in the bottom-right of every activity.

---

## Smoke test

1. Launch the debug build. A small dark pill with a location-pin icon should be at the bottom-right.
2. Drag the pill anywhere — release. It springs to whichever screen edge (left or right) it's closer to.
3. Tap the pin → toolbar expands (review / settings / close).
4. Tap the pin again → annotate-mode on (pin turns accent color).
5. Long-press any UI element. A live stroke rectangle should follow your finger, snapping to the deepest semantic element under it. Drag to refine selection.
6. Release → screenshot captures, popup appears with the captured region preview.
7. Type a comment, choose intent / severity, tap Add. A numbered marker appears at the captured spot.
8. Open the toolbar's review screen → see all annotations with thumbnails. Share → image + markdown go to Slack / wherever.

If the FAB doesn't appear: check that `com.rajandube:jelly` is on the classpath at runtime via
`./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep jelly`.

---

## Configuration

`JellyConfig` accepts:

```kotlin
JellyConfig(
    detailLevel = DetailLevel.Standard,        // Compact / Standard / Detailed / Forensic
    accentColor = AccentColor.Indigo,          // 7 colors available
    endpoint = "https://your-mcp.example.com", // optional MCP /sessions sync
    sessionId = null,                          // resume a known session
    webhookUrl = null,                         // optional outbound webhook
    copyToClipboard = true,
    captureScreenshots = true,
    screenKey = null,                          // override the per-activity storage key
)
```

Most settings are also exposed as a runtime UI in the toolbar's settings sheet — what you pass in `JellyConfig` is just the default before the user changes it.

---

## Source attribution

Every annotation includes a `Source: SomeFile.kt:42` line. Three resolvers, in priority order:

1. **`Modifier.jellySource("LoginScreen.kt", 42)`** — manual, most precise. Tag a screen-root composable; everything inside inherits.
   ```kotlin
   Column(Modifier.jellySource("LoginScreen.kt", 42)) { … }
   ```
2. **`ui-tooling-data` slot tree** — debug-only, best-effort reflective lookup of composable name + source.
3. **Activity stack-walk** — automatic fallback. Walks the call stack at first composition, returns the first frame outside `dev.jelly.*` / Compose / Kotlin internals. Result: `Source: MainActivity.kt:36` shows up automatically with zero per-screen tagging.

You don't need to do anything for source attribution to work. Reach for `Modifier.jellySource()` only when you want sub-screen precision.

---

## How element identification works

The SDK runs **two parallel hit-tests** on long-press and picks whichever is more specific:

1. **Compose semantics tree** — rich content (text, role, contentDescription) but only sees nodes with explicit semantics modifiers.
2. **`ui-tooling-data` slot tree** — captures every composable scope regardless of semantics, useful for server-driven UIs where widgets are built from plain `Box`/`Column` layouts without semantic hints.

When the slot-tree hit's bounds are notably tighter than the semantic hit's, it's preferred — this is what makes the toolbar work on apps where a `verticalScroll` Column would otherwise swallow every press as "the whole scroll container".

For XML / `View`-based screens, a separate **View-tree walk** finds the deepest visible View at the press point and extracts class name, resource id, content description, and text directly. This means apps that mix Compose and XML get precise hits on whichever paradigm owns the pressed element.

Hidden views (alpha=0, `visibility != VISIBLE`, zero-sized layouts, or "transparent shell" container ViewGroups whose children are all invisible) are filtered out — a backend-flag-driven hidden overlay can't steal hits from the visible widget underneath.

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

Plus a screenshot with the element bounds drawn in the accent color and a caption strip baking the metadata into the image — so receivers that drop `EXTRA_TEXT` (Slack, WhatsApp) still see the context.

The format is identical to the web version's `generateOutput()`, so the same downstream agents work for both.

---

## Known limitations

- **Bottom sheets cover the FAB.** The toolbar lives in a `TYPE_APPLICATION_PANEL` sub-window of the activity (no permissions required), so its layer is `parent.layer + small_offset`. A `BottomSheetDialog` is `TYPE_APPLICATION` at the application base layer, which ends up *higher*. The clean fix requires either hosting the FAB inside a `Dialog` of our own (gets a fresh window token, shares the dialog layer space) or using `TYPE_APPLICATION_OVERLAY` with `SYSTEM_ALERT_WINDOW` permission. Workaround for now: dismiss the bottom sheet first, then use the FAB.
- **No annotation while a Dialog/BottomSheet is open.** Long-press goes to the dialog's window, not the activity's decor view where our capture overlay lives. Same workaround.
- **React Native bridge screens are invisible to the SDK.** It only reads Compose semantics and Android Views. RN content rendered in a host RN view appears as "the RN container" — that's a fundamental limitation of where Jelly can introspect.
- **`ui-tooling-data` is unstable across Compose UI versions.** All reflective access is wrapped in `try/catch` so a version-skew failure degrades to "no source line" rather than crashing.

---

## Versioning + composite build vs maven

For active SDK development, the composite-build approach above is fastest — edits to `jelly-android/jelly/src/...` rebuild on the host's next build. No publish step.

For consumers that want a frozen artifact, the SDK is set up to publish (`group = "dev.jelly"`, `version = "0.1.0"`). Add a `maven-publish` plugin block and publish to your internal Maven; consumers swap `includeBuild` for `mavenLocal()` / your repo URL.

---

## For agents

The above is the human-readable guide. There's also a self-serve agent skill at `~/.claude/skills/jelly-android/SKILL.md` that gives an LLM-driven Claude Code agent the exhaustive procedure (discovery, version alignment, composite build, smoke test, common-error → fix table, etc.). Useful when you want to ask an agent to do the integration end-to-end.

---

## Status

`v0.6` — Application-level install pattern, draggable FAB with edge-snap, two-window architecture (capture overlay in decor view + toolbar in `TYPE_APPLICATION_PANEL` window), View + Compose multi-paradigm hit-testing, slot-tree fallback for sparse-semantics apps, baked share images.

See [`CLAUDE.md`](CLAUDE.md) for repo-internal architecture notes and the original design plan at `~/.claude/plans/squishy-tinkering-wreath.md`.
