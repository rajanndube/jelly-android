# Agentation Android (Kotlin Compose port)

Standalone Android Gradle project — sibling to the web monorepo at `../agentation`. Ports the Agentation toolbar to native Kotlin Compose so QA can long-press any UI element in an Android app, capture structured feedback, and hand it to AI coding agents in the same markdown format the web version produces.

v0.1 was scaffolded inside `../agentation/android/` for session continuity; this is the extracted standalone repo. Status as of extraction: v0.1 MVP code complete, `:agentation:assembleDebug` and `:sample:assembleDebug` both green.

## What this is

A Compose library that QA/designers wrap around their app's root. Long-press any UI element while annotate-mode is on → the library inspects the runtime semantics tree → a popup captures a comment → output goes to clipboard / share sheet / MCP `/sessions` endpoint as markdown.

The markdown contract is shared with the web version (`package/src/utils/generate-output.ts`) so the same downstream agents work for both.

## Integration (host app perspective)

```kotlin
setContent {
    Agentation(config = AgentationConfig(endpoint = MCP_URL)) {
        MyAppRoot()  // existing app code unchanged
    }
}
```

Add `qaImplementation("dev.agentation:agentation-android:0.1.0")` to gate it to QA builds. No per-screen `Modifier.testTag` plumbing required — the library hits the semantics tree at long-press time.

## Architecture (key files)

- `agentation/src/main/kotlin/dev/agentation/Agentation.kt` — public composable that wraps `content()` and overlays the toolbar. Captures the host source via `detectHostSource()` at first composition.
- `agentation/.../capture/SemanticsCapture.kt` — long-press hit-test against the unmerged `SemanticsOwner` tree, with live-preview drag and `boundsInRoot`-based coords.
- `agentation/.../capture/AgentationSourceRegistry.kt` — `Modifier.agentationSource(file, line)` for manual tagging + `detectHostSource()` for automatic activity-level detection via stack walking.
- `agentation/.../capture/CompositionInspector.kt` — additional fallback via `ui-tooling-data` (best-effort; `agentationSource` takes priority when present, then this, then the auto-detected host source).
- `agentation/.../capture/BakedImage.kt` — bakes stroke rect + structured caption (Element / Location / Source / Feedback / tags) into the saved image.
- `agentation/.../output/OutputGenerator.kt` — 1:1 port of the web `generateOutput()` markdown.
- `agentation/.../storage/AnnotationStore.kt` — DataStore Preferences with 7-day expiry parity.
- `agentation/.../model/Annotation.kt` — mirrors `package/src/types.ts:5–69`.
- `agentation/.../theme/AgentationTheme.kt` — internal dark theme (zinc palette) that all SDK overlay UI renders in, regardless of host theme.
- `sample/` — minimal Compose app for live testing, not published.

## Source location (`Source: Foo.kt:42`)

Three paths populate `Annotation.sourceFile`, in priority order — first non-null wins:

1. **`Modifier.agentationSource("File.kt", 42)`** — manual tag on the screen root or a key composable. Use when you want screen-level precision instead of the activity-level fallback.
   ```kotlin
   @Composable
   fun LoginScreen() {
       Column(Modifier.agentationSource("LoginScreen.kt", 42)) { ... }
   }
   ```
   `SemanticsCapture` walks the hit node's ancestors for the closest tag — tagging a Card is enough; every annotation inside it inherits.

2. **`CompositionInspector` (ui-tooling-data, debug only)** — reflective slot-tree walk. Best-effort; fragile across Compose UI versions.

3. **`detectHostSource()` — automatic activity-level fallback.** Walks the call stack at first composition of `Agentation { }`, finds the first frame outside `dev.agentation.*` / `androidx.compose.*` / Kotlin/Java internals, returns its `(file, line)`. Result: every annotation gets `Source: MainActivity.kt:36` automatically with zero per-screen tagging.

This combination means **zero integration code is required for source attribution** in the common case — wrap `Agentation { }` and source populates. Devs only reach for `Modifier.agentationSource()` when they want sub-screen precision.

## What ports vs. what doesn't

Ports cleanly: element identification (semantics tree), bounds, accessibility, output markdown, storage shape, MCP `/sessions` API.

Doesn't port: shadow DOM piercing, CSS class introspection, animation freeze (would require VM-level patching), keyboard shortcuts, design-mode CSS mutation, multi-select drag, drawing strokes. See plan file for full list.

## Source of truth

The full design plan is at `~/.claude/plans/squishy-tinkering-wreath.md`. Read it for context, decisions log, and phasing.

## Phasing

- v0.1 — toolbar, annotate-mode, hit-test, popup, clipboard, DataStore.
- v0.2 — detail levels, settings sheet, share sheet, region screenshot, accent colors.
- v0.3 — MCP `/sessions` sync, webhook URL.
- v0.4 — `ui-tooling-data` for source file:line (best-effort fallback).
- v0.5 — press-drag-release live preview; haptics; `AgentationTheme` dark UI; `BakedImage` self-contained share artifacts; manual `Modifier.agentationSource()` for screen-level precision; **automatic activity-level Source via `detectHostSource()` stack walk** — zero-config source attribution.
- v0.6+ — `SYSTEM_ALERT_WINDOW` Service for cross-app QA.

## Build

```bash
./gradlew :agentation:assembleDebug          # build library
./gradlew :sample:installDebug               # install demo on connected device
```

Or open this folder as a project in Android Studio.

## Web reference

Cross-references in source code (`package/src/...:NNN`) point at files in the sibling `../agentation` repo. Read those alongside this code when porting new features — the markdown contract and `Annotation` schema are the load-bearing parity points.
