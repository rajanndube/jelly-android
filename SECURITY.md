# Security Policy

## Threat model

Jelly for Android is a **debug-only** QA-annotation SDK, meant to be linked into
debug builds via `debugImplementation` and never shipped in release. It inspects
the host app's live UI at runtime (the Jetpack Compose semantics tree and Android
`View` hierarchy) and renders screenshots of the host window. All of that stays
**on the device** unless the developer deliberately wires up egress:

- **No remote endpoint by default.** `JellyConfig.endpoint` is `null` until the
  developer sets it (typically by scanning the QR code on a local
  [jelly-local-sync](https://github.com/rajanndube/jelly-local-sync) viewer).
  Only then are annotations POSTed, to that one developer-chosen MCP `/sessions`
  URL. There is no baked-in or fallback host.
- **Sync egress is scoped to that endpoint.** While Sync is enabled the SDK also
  sends a lightweight heartbeat (`POST /hello`) carrying non-sensitive device
  metadata (platform, model, manufacturer, OS version, app name, SDK version)
  and best-effort uploads the baked screenshot for each synced annotation. All
  of it goes only to the developer-chosen endpoint, nowhere else.
- **Clipboard and share sheet are user-initiated.** Output leaves the device
  only when the QA tester explicitly copies or shares it.
- **Local storage is ephemeral.** Annotations live in a DataStore Preferences
  store with a 7-day TTL, scoped to the host app's sandbox.

Because captures may contain whatever is on screen (including sensitive user
data), treat exported markdown and baked screenshots as you would any QA
artifact, and do not enable sync against an untrusted endpoint.

The QR endpoint scanner uses the Google Play Services Code Scanner, whose UI runs
out-of-process in Play Services, so the **host app does not need to declare the
`CAMERA` permission**. Camera access is used solely to read the sync landing
page's QR code and is never recorded or transmitted.

## Supported versions

Only the latest tagged release is supported. There are no LTS branches.

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

- Use GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
  on this repository (Security, then Report a vulnerability), or
- Email **rajan.reachme@gmail.com** with details and reproduction steps.

You'll get an acknowledgement within a few days. Since this is a small,
dependency-free, debug-only SDK, the attack surface is limited, but reports are
still welcome.
