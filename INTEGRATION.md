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
