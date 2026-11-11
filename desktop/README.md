# MindUnlock Windows Desktop — Proof of Concept

**Status:** Minimal runnable desktop project added; Windows CI validation is pending.

## Purpose

Prove the Windows desktop technology path without changing the Android application, its Room schemas, its database files, or its release workflow.

Target platform: Windows 10/11 x64.

## Current implementation

- Independent Gradle project in this directory; it does not inherit or modify Android's Gradle configuration.
- Kotlin JVM 2.4.21, Compose Multiplatform 1.12.2, Compose compiler plugin 2.4.21.
- Gradle 8.14.3 and JDK 17 in the Windows CI workflow.
- Minimal Persian desktop window with an RTL layout-direction provider and navigation labels.
- One JUnit 5 unit test verifies the initial navigation model.
- Windows CI runs the tests and attempts to package a per-user MSI installer.

These are pinned candidate versions for the PoC, not a production dependency policy. See `docs/adr/ADR-0001-windows-desktop-poc.md`.

## Non-goals of this first PoC

- No production user data.
- No Android database access or shared SQLite file.
- No account, cloud service, or network sync.
- No migration of Android source into this directory.
- No claim of feature completeness or production readiness.
- No SQLite implementation or Jalali calendar implementation yet.

## PoC acceptance criteria

The first executable milestone must demonstrate all of the following on a Windows machine or Windows VM:

1. A Compose Multiplatform Desktop window starts successfully.
2. Persian text renders correctly with true RTL paragraph/layout behavior.
3. Window resizing and Windows DPI scaling do not break the layout.
4. Jalali date display is correct for known test dates and timezone boundaries.
5. A local SQLite database can be created under the current user's AppData directory.
6. A sample record can be created, closed, reopened, queried, edited, and deleted.
7. The app runs with networking disabled.
8. A per-user installer can install, launch, upgrade, and uninstall without deleting user data.
9. Unit and integration tests execute and report non-zero test counts.
10. A build artifact and a short Windows test report are attached to CI.

## Suggested first screen

A deliberately small RTL shell:

- Window title: MindUnlock
- Sidebar: خانه، Inbox، یادگیری، برنامه‌ریزی، ژورنال، دانش، تنظیمات
- Main panel: a Persian welcome heading and a local-storage status indicator
- Quick action: add a test Inbox item
- Footer/status: offline mode and database path (path can be hidden behind diagnostics)

The current screen is only a shell; the storage status is explicitly marked as not yet implemented.

## Architecture constraints

- Keep UI, domain logic, and persistence separate.
- Do not depend on Android APIs in desktop code.
- Do not use Android's Room database file from Windows.
- Do not extract shared modules from Android until source-of-truth and migration risks are resolved.
- Do not add a plugin version by guesswork; record the chosen version and reason in an ADR.
- Keep user data outside the installation directory.

## CI

Workflow: `.github/workflows/windows-desktop-poc.yml`

It runs on `windows-latest`, installs JDK 17 and Gradle 8.14.3, executes desktop unit tests, and packages an MSI. A passing workflow is required before describing this PoC as Windows-build-verified.

## Work sequence

1. Validate the pinned toolchain on Windows CI.
2. Add RTL-specific rendering and keyboard-navigation tests.
3. Add Jalali date implementation and boundary tests.
4. Add local SQLite persistence and lifecycle tests.
5. Add installer upgrade/uninstall tests while preserving user data.
6. Expand the shell into the full MindUnlock module set only after the foundations pass.

## Current verification state

The project files and workflow have been committed to the isolated `windows-desktop-poc` branch. The Windows workflow result has not yet been verified. No Windows executable, validated installer, SQLite implementation, or Windows runtime test is claimed at this stage.
