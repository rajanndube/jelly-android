# Changelog

All notable changes to the Jelly Android SDK are documented here. This project adheres to [Semantic Versioning](https://semver.org) and the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format.

## [Unreleased]

_No unreleased changes yet._

## [0.2.0] — 2026-06-05

Local-sync pairing, hardening, and a release-blocking lint fix. Adds the integration the sibling [`jelly-local-sync`](https://github.com/rajanndube/jelly-local-sync) dashboard relies on (device heartbeat, binary screenshot upload, QR scanner), reworks the manual-sync flow to be idempotent, and ships a long backlog of robustness fixes uncovered by real QA usage.

### Added
- **QR scanner in settings.** A trailing-icon button on the Endpoint field invokes the Play Services Code Scanner; no `CAMERA` permission required in the host manifest (the scanner UI runs out-of-process in Play Services). Scans a `jelly-local-sync` QR and fills the Endpoint + toggles Sync on.
- **Device heartbeat (`POST /hello`).** While Sync is enabled, the SDK pings the endpoint every 12 s with `{platform, model, manufacturer, osVersion, appName, sdkVersion}`. Powers the dashboard's "Pixel 7 · Android 14 — Connected" pill and per-device attribution. 12 s cadence pairs with the dashboard's 15 s manual-probe window so a single beat always lands inside one probe.
- **Image upload extension.** `JellyApi.uploadAnnotationImage(annotationId, bytes, contentType)` POSTs the baked screenshot to `/annotations/:id/image`. Called best-effort right after a successful `syncAnnotation`. Falls back silently on hosted MCP servers that don't implement the endpoint.
- **"Push N annotations" button** in the settings sheet. Always re-pushes every locally-stored annotation to the current endpoint (the server idempotently dedupes by `id`), so refreshing the dashboard or pointing the SDK at a new endpoint can be followed by a single tap to repopulate the new room. Fully-rounded accent-filled capsule when actionable.
- **Done button** in the settings sheet header. Tonal accent pill in the top right that dismisses the sheet — toggle changes are auto-saved via `onSettingsChange`, the button just provides the affordance users expect.
- **Confirmation dialog on Clear All** in the annotations review screen. Cancel auto-focused so an accidental Enter never wipes data; "Delete all" rendered in `colorScheme.error`.
- **Screenshot cache cap.** `JellyCacheCleanup.trim` runs once on `Jelly.install` and enforces a 100 MB ceiling on `cacheDir/jelly/`, oldest-first eviction. Prevents unbounded growth across QA sessions.
- **HTTP timeouts.** Ktor `HttpTimeout` installed on the default client (15 s connect, 30 s socket, 60 s request). Hung MCP endpoints can no longer block the sync coroutine indefinitely.
- **Sync-error logging.** Every previously-silent `runCatching` around `createSession`, `syncAnnotation`, `uploadAnnotationImage`, and `sayHello` now logs the underlying exception via `Log.w("JellySync", ...)`. `AnnotationStore` decode failures log via `Log.w("JellyStore", ...)`. Logcat audits replace silent failure debugging.

### Changed
- **Push semantics: "all" instead of "pending".** Bulk sync now re-uploads every locally-stored annotation regardless of `syncedTo`. The server keys by annotation `id` and overwrites, so re-pushing is harmless and lets users repopulate a fresh `jelly-local-sync` room without forcing the SDK to track "last synced endpoint" state.
- **`Session` model is now tolerant.** `url`, `status`, and `createdAt` are nullable (`String? = null`). Matches the iOS sibling, which already lets minimal MCP servers return `{id}` alone. Strict decode previously surfaced any missing field as "couldn't reach endpoint", which was misleading and triggered on `jelly-local-sync` until `status: "active"` was added server-side.
- **Cleartext HTTP enabled in the sample manifest.** Android 9+ blocks plain `http://localhost` traffic by default. The sample now declares `usesCleartextTraffic="true"` for the debug build, documented in [`INTEGRATION.md`](INTEGRATION.md) as a host-app requirement for pairing with `jelly-local-sync`. Production hosts should scope this to `src/debug/AndroidManifest.xml`.
- **`BakedThumbnail` decodes with sub-sampling.** Two-pass `BitmapFactory.Options.inSampleSize` decode at a 1080-px max dim and `RGB_565` config. A 30-card review panel now holds ~10 MB of decoded bitmaps instead of ~180 MB.

### Removed
- **`JellyConfig.webhookUrl`** and the corresponding `Settings.webhookUrl`, `SettingsStore` key, and the "Webhook URL (optional)" text field in the settings sheet. The field was never used in practice and added a confusing knob next to the actual sync endpoint. **Breaking change for consumers passing `webhookUrl = ...` to `JellyConfig` — remove the argument.**

### Fixed
- **Release build crashed on lint.** AGP 8.7's bundled `NonNullableMutableLiveDataDetector` is incompatible with the Kotlin 2.1 Analysis API (`Found class KaCallableMemberCall, but interface was expected`). The detector isn't relevant to this library; disabled in [`jelly/build.gradle.kts`](jelly/build.gradle.kts) and [`sample/build.gradle.kts`](sample/build.gradle.kts). `:jelly:assembleRelease`, `publishToMavenLocal`, and `publishToMavenCentral` work again.
- **`ViewTreeLifecycleOwner not found` crash** when the QR scanner activity launched. The previous guard checked `activity is ComponentActivity` to skip non-Compose activities, but Play Services' `GmsBarcodeScanningDelegateActivity` **is** a `ComponentActivity` — it just never calls `setContentView`, so the decor view never gets the lifecycle owner. The guard now checks `findViewTreeLifecycleOwner()` on the decor view directly.
- **Double-submit on the annotation popup** could save two annotations with the same `id`, then crash `AnnotationsScreen`'s `LazyColumn` with "Key already used" on the next open. `state.pending` is now cleared with a reference-identity guard before the submission coroutine launches.
- **`AnnotationStore` dedupes by `id`** in both `save` and `decode` as defense-in-depth. Existing DataStore content with pre-fix duplicates loads cleanly instead of crashing the panel forever.
- **`BakedThumbnail` no longer early-returns from a `@Composable`.** Conditional `?: return` corrupted the slot table when an annotation referenced a screenshot file that no longer existed (cache evicted, prior clear-all). Now uses `if (bitmap != null) Image(...)`.
- **`bumpToolbarToTop` race.** A re-entrancy `isBumping` flag prevents two `WindowManager.removeView` + `addView` cycles from queuing on rapid focus changes (which crashed with `BadTokenException` on the second add).
- **Doc cleanup.** Removed stale `webhookUrl = null` from the `JellyConfig` example in `README.md`. Updated heartbeat cadence references to the 12-s cadence and clarified iOS pairing is Wi-Fi-only (`iproxy` goes Mac→Device, not the direction we'd need).

### Requirements
- `minSdk` 26 (Android 8.0+), `compileSdk` 35.
- Kotlin 2.1.0, AGP 8.7.0, Compose BOM 2025.12.00.
- A Jetpack Compose host app.
- For pairing with `jelly-local-sync`: host's debug manifest must declare `<uses-permission android:name="android.permission.INTERNET" />` and `<application android:usesCleartextTraffic="true" ...>` (or a network-security-config equivalent permitting localhost / LAN IPs).

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
