# Contributing to Jelly for Android

Thanks for your interest in improving Jelly. This is a small, focused SDK — contributions that keep it that way are very welcome.

## Ground rules

- **`main` is protected.** Open a pull request; direct pushes are rejected and history is linear (squash-merge, conversations resolved). Keep PRs scoped to one change.
- **Preserve the markdown contract.** `OutputGenerator` produces output that is byte-identical to the iOS and web SDKs. That contract is load-bearing for the downstream agents that consume Jelly's output, so any change to `OutputGenerator`'s format must be mirrored across all three clients. There is currently no automated parity test on the Android side, so verify changes by hand against the web reference (`generateOutput()`) and the iOS `OutputGenerator` before sending them.
- **Debug-only.** The SDK is meant to be linked into debug builds only via `debugImplementation`. Don't add anything that assumes it ships in release.

## Getting set up

```bash
git clone https://github.com/rajanndube/jelly-android.git
cd jelly-android
```

You need JDK 17+ and an Android SDK with `compileSdk` 35 installed. Point Gradle at your SDK by creating `local.properties` (gitignored) with:

```properties
sdk.dir=/path/to/Android/sdk
```

Build the library and the sample app:

```bash
./gradlew :jelly:assembleDebug :sample:assembleDebug
```

Run the full build (assemble + unit tests for every variant):

```bash
./gradlew build
```

The sample app under `sample/` is a minimal Compose host for live testing; install it on a connected device or emulator with:

```bash
./gradlew :sample:installDebug
```

## Before you open a PR

1. `./gradlew :jelly:assembleDebug :sample:assembleDebug` is green.
2. Code matches the surrounding style (naming, comment density, idiom).
3. Anything touching `OutputGenerator` keeps the markdown byte-identical to the iOS / web reference (see Ground rules).
4. Update [`CHANGELOG.md`](CHANGELOG.md) under the "Unreleased" heading if your change is user-facing.

## Architecture

See [`CLAUDE.md`](CLAUDE.md) for the file-by-file map of the codebase and the key parity points.

## Reporting bugs

Open an issue with: device / Android version, whether the host screen is Compose or XML Views, and — if an element isn't selectable — a description of the element and its surrounding layout.
