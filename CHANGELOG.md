# Changelog

All notable changes to the Jelly Android SDK are documented here. This project adheres to [Semantic Versioning](https://semver.org) and the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

## [Unreleased]

_No unreleased changes yet._

## [0.1.0] — 2026-05-05

First public release of the Jelly Android SDK — a debug-only QA-annotation toolbar for Jetpack Compose apps, published to Maven Central as `com.rajandube:jelly:0.1.0`.

### Added
- **Single-call install.** `Jelly.install(application, JellyConfig(...))` from `Application.onCreate` wires the toolbar into every activity — no per-screen modifiers required.
- **Draggable FAB with edge-snap.** A dark pill overlay that drags freely and springs to the nearest screen edge on release.
- **Two-window architecture.** The capture overlay lives in the activity's decor view; the toolbar lives in a `TYPE_APPLICATION_PANEL` sub-window, so no runtime permissions are needed.
- **Multi-paradigm hit-testing.** Parallel Compose semantics-tree walk plus a `ui-tooling-data` slot-tree walk, picking the tighter hit. A separate View-tree walk handles XML / `View`-based screens, so apps mixing Compose and Views get precise hits on whichever paradigm owns the pressed element. Hidden / zero-alpha / transparent-shell views are filtered out.
- **Press-drag-release live preview.** A stroke rectangle follows the finger and snaps to the deepest semantic element under it, with haptics.
- **3-tier source attribution.** `Modifier.jellySource("File.kt", 42)` → `ui-tooling-data` slot tree → activity stack-walk fallback. Zero per-screen wiring required for the common case.
- **`OutputGenerator`.** Produces markdown matching the web and iOS SDKs' output contract, so the same downstream agents work across all three clients.
- **Baked share images.** Element bounds drawn in the accent color plus a caption strip baking Element / Location / Source / Feedback / tags into the screenshot, so receivers that drop `EXTRA_TEXT` (Slack, WhatsApp) still see the context.
- **Annotation review screen, settings sheet, detail levels** (Compact / Standard / Detailed / Forensic), **and 7 accent colors.**
- **MCP `/sessions` sync** and optional outbound webhook URL.
- **DataStore Preferences storage** with a 7-day TTL, matching the web SDK.
- **Forced-dark zinc theme** on the overlay UI only — it does not bleed into host content.

### Requirements
- `minSdk` 26 (Android 8.0+), `compileSdk` 35.
- Kotlin 2.1.0, AGP 8.7.0, Compose BOM 2025.12.00.
- A Jetpack Compose host app.
- Debug-only by design — gate the dependency behind `debugImplementation` (or a QA flavor) so it never ships in release.

[Unreleased]: https://github.com/rajanndube/jelly-android/compare/main...HEAD
[0.1.0]: https://central.sonatype.com/artifact/com.rajandube/jelly/0.1.0
