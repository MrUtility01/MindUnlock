# Android build request

This commit triggers the Android CI workflow on `main`.

## Build inputs

- Android source archive: `MindUnlock-Android-v0.3.1-source.zip`
- Build workflow: `.github/workflows/android.yml`
- Target app version: `0.5.1` (versionCode 6)
- Output: debug APK for validation; not a signed production release.

## CI validation

The workflow runs JVM unit tests, assembles the debug APK, validates APK archive integrity, and verifies the expected application package ID. These checks do not claim physical-device installation, UI automation, migration-from-existing-installation, or end-to-end server synchronization tests.

## Security limitation

The current Windows sync transport is HTTP for trusted LAN use only. Do not expose it to the public internet. Production deployment requires authenticated TLS and server-side authorization.
