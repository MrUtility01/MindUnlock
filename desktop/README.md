# MindUnlock Windows Desktop — Proof of Concept

**Status:** Planning scaffold only. This directory is not yet a runnable Windows application.

## Purpose

Prove the Windows desktop technology path without changing the Android application, its Room schemas, its database files, or its release workflow.

Target platform: Windows 10/11 x64.

## Non-goals of this first PoC

- No production user data.
- No Android database access or shared SQLite file.
- No account, cloud service, or network sync.
- No migration of Android source into this directory.
- No claim of feature completeness or production readiness.

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

The sample Inbox item is disposable PoC data, not a production domain model.

## Architecture constraints

- Keep UI, domain logic, and persistence separate.
- Do not depend on Android APIs in desktop code.
- Do not use Android's Room database file from Windows.
- Do not extract shared modules from Android until source-of-truth and migration risks are resolved.
- Choose SQLite library and Compose Multiplatform versions only after compatibility validation against the repository's Gradle/JDK baseline.
- Do not add a plugin version by guesswork; record the chosen version and reason in an ADR.

## Work sequence

1. Record the technology/version decision in `docs/adr/ADR-0001-windows-desktop-poc.md`.
2. Add the smallest runnable Compose Desktop application.
3. Add RTL and Persian font verification.
4. Add Jalali date tests.
5. Add local SQLite persistence and lifecycle tests.
6. Add Windows packaging only after the app runs reliably.
7. Keep all work on `windows-desktop-poc` until a Windows-capable CI runner verifies it.

## Current state

Only this planning scaffold exists. No Windows executable, installer, SQLite implementation, or Windows runtime test has been produced yet.
