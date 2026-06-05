# Integrating Jelly into an Android app

For human readers: see [`README.md`](README.md) — quick-start integration is a five-step copy-paste.

For agents (or agent-assisted integration): the full step-by-step procedure is captured as a Claude Code skill at:

```
~/.claude/skills/jelly-android/SKILL.md
```

Both stay in sync; the skill is the agent-runnable version of the same material with extra discovery / common-error patterns surfaced.

## Reference integration

The vance-android integration on the `jelly-test` branch is the canonical example. Files touched:

- `settings.gradle.kts` — composite build
- `app/app.gradle.kts` — `debugImplementation` + `notDebug` source-set wiring
- `app/src/main/.../VanceApp.kt` — `installQaTools(this)` from `Application.onCreate`
- `app/src/notDebug/java/.../devtools/QaInstaller.kt` — no-op stub (NEW)
- `app/src/debug/java/.../devtools/QaInstaller.kt` — real `Jelly.install()` (NEW)

Total: 3 modified files + 2 new files in the consumer. No jelly source copied.

## Pairing with `jelly-local-sync` (cleartext caveat)

The local-sync viewer serves over plain `http://` on `localhost` (via `adb reverse`) or a LAN IP. Android 9+ blocks all cleartext HTTP at the `OkHttp` socket layer by default — Ktor sync calls return success with no body, the SDK's `runCatching` swallows the failure, and nothing appears on the browser.

Host apps that want to pair must enable cleartext **for the debug build only**:

```xml
<!-- src/debug/AndroidManifest.xml -->
<manifest>
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:usesCleartextTraffic="true"
        tools:replace="android:usesCleartextTraffic" />
</manifest>
```

Or, if you want a tighter whitelist, ship a `network_security_config.xml` that permits cleartext only on specific hostnames you actually test against (note: `<domain>` accepts exact hostnames/IPs, not CIDR — so you'd list each LAN IP or just `localhost` / `127.0.0.1` if you only use `adb reverse`).

The sample app at `sample/src/main/AndroidManifest.xml` uses the app-wide flag for simplicity since it's debug-only.
