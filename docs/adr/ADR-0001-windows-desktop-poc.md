# ADR-0001: Windows Desktop Proof of Concept

- **Status:** Proposed for validation; not an approved production architecture.
- **Date:** 2026-11-11
- **Branch:** `windows-desktop-poc`
- **Scope:** Technology evaluation for an isolated Windows desktop proof of concept.

## Context

MindUnlock has an Android application written in Kotlin and Jetpack Compose. The effective Android source pipeline is currently generated and patched by CI, and the tracked Android tree is not yet a verified source of truth. The existing Android database migration and HTTP sync paths also require remediation.

The Windows edition must ultimately cover the Android feature set and the approved MindRebuild/MindUnlock modules, remain offline-first, support Persian RTL and Jalali dates, and keep user data private.

## Decision under evaluation

Evaluate **Kotlin + Compose Multiplatform Desktop** as the first Windows UI/runtime candidate because it aligns with the Android language and Compose ecosystem.

This is a candidate, not a final selection. Do not begin extracting Android code into shared modules until the Windows PoC and Android source stabilization criteria are met.

## Persistence

Use a separate Windows-local SQLite database. The database file must be stored under the current user's application data directory. Android's Room database files must never be opened or shared directly by Windows.

The SQLite access library and schema migration strategy remain undecided until a small Windows build proves compatibility, lifecycle behavior, and testability.

## Version and toolchain policy

The repository's Android build currently declares Kotlin 2.0.21 and Gradle wrapper 8.9 in the tracked Android project. Those versions are reference information only; they do not establish compatibility with a particular Compose Multiplatform Desktop plugin or Windows packaging plugin.

Before selecting versions:

1. Check the official compatibility matrix for Kotlin, Compose Multiplatform, Gradle, and the selected JDK.
2. Pin plugin and dependency versions explicitly.
3. Build the minimal desktop target on a Windows runner.
4. Record the JDK and Gradle versions in CI output.
5. Do not use dynamic versions such as `+`.

## Security and data rules

- PoC has no network sync and no cloud dependency.
- Do not put real journal, notes, or private user data in PoC fixtures.
- Do not store passwords or tokens in plain-text settings.
- Do not modify Android databases or migration code from this branch.
- Keep user data outside the installation directory.
- Any future import/export path must validate format version, file paths, record counts, and integrity before committing data.

## Alternatives

### Native Windows UI (e.g. .NET)

Could provide a strong Windows-native experience, but would create a second primary application stack and increase the amount of domain logic that must be maintained separately.

### JavaFX

A viable JVM desktop option, but Compose Multiplatform is a closer conceptual fit with the existing Kotlin/Compose Android application. Reconsider if Compose Desktop fails RTL, accessibility, packaging, or compatibility tests.

### Electron

Would introduce a separate web runtime and a larger distribution footprint. Not preferred for the first PoC unless Kotlin desktop fails key requirements.

## Consequences

Positive:
- Potential reuse of Kotlin domain logic after it is extracted safely.
- One language across Android and Windows.
- Desktop UI can be tested independently of Android.

Risks:
- Compose Desktop behavior, accessibility, RTL, TTS, and packaging need Windows verification.
- Shared code cannot be assumed merely because both apps use Kotlin.
- The current Android source divergence prevents safe direct extraction at this time.

## Exit criteria

This ADR can be accepted only after evidence is attached for:

- A working Windows 10/11 x64 desktop build.
- Correct Persian RTL rendering and keyboard navigation.
- Jalali date tests, including timezone edge cases.
- SQLite create/read/update/delete and reopen tests.
- Offline launch and basic operation.
- Per-user install/upgrade/uninstall test that preserves user data.
- CI unit/integration tests with actual executed test cases.

If one or more critical criteria fail, reassess JavaFX or a native Windows client before committing to a long-term architecture.
