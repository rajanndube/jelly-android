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

- `agentation/src/main/kotlin/dev/agentation/Agentation.kt` — public composable that wraps `content()` and overlays the toolbar.
- `agentation/.../capture/SemanticsCapture.kt` — long-press hit-test against `SemanticsOwner.rootSemanticsNode`.
- `agentation/.../capture/CompositionInspector.kt` — debug-only `ui-tooling-data` source-location lookup (added in v0.4).
- `agentation/.../output/OutputGenerator.kt` — 1:1 port of the web `generateOutput()` markdown.
- `agentation/.../storage/AnnotationStore.kt` — DataStore Preferences with 7-day expiry parity.
- `agentation/.../model/Annotation.kt` — mirrors `package/src/types.ts:5–69`.
- `sample/` — minimal Compose app for live testing, not published.

## What ports vs. what doesn't

Ports cleanly: element identification (semantics tree), bounds, accessibility, output markdown, storage shape, MCP `/sessions` API.

Doesn't port: shadow DOM piercing, CSS class introspection, animation freeze (would require VM-level patching), keyboard shortcuts, design-mode CSS mutation, multi-select drag, drawing strokes. See plan file for full list.

## Source of truth

The full design plan is at `~/.claude/plans/squishy-tinkering-wreath.md`. Read it for context, decisions log, and phasing.

## Phasing

- v0.1 — toolbar, annotate-mode, hit-test, popup, clipboard, DataStore.
- v0.2 — detail levels, settings sheet, share sheet, region screenshot, accent colors.
- v0.3 — MCP `/sessions` sync, webhook URL.
- v0.4 — `ui-tooling-data` for source file:line in debug builds.
- v0.5 — `SYSTEM_ALERT_WINDOW` Service for cross-app QA.

## Build

```bash
./gradlew :agentation:assembleDebug          # build library
./gradlew :sample:installDebug               # install demo on connected device
```

Or open this folder as a project in Android Studio.

## Web reference

Cross-references in source code (`package/src/...:NNN`) point at files in the sibling `../agentation` repo. Read those alongside this code when porting new features — the markdown contract and `Annotation` schema are the load-bearing parity points.
