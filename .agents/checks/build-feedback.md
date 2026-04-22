---
name: android-build-feedback
description: Ensures the local Android verification loop stays agent-friendly and device-independent.
severity-default: high
tools: [Bash, Read]
---

Verify that the documented local checks still run without a connected device:

1. `gradle help`
2. `gradle :app:compileDebugKotlin`
3. `gradle :app:lintDebug`
4. `gradle :app:testDebugUnitTest`
5. `gradle :app:assembleDebug`

Flag any command that:

- stops emitting actionable file paths or line numbers on failure
- fails before Gradle reaches the app module tasks
- depends on an online Android device for local verification
- hides Rust binding/native build failures behind generic Gradle errors
