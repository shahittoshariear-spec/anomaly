# Build validation — Anomaly 1.2.0

Completed on the build machine (Windows, Android SDK in `tools/android-sdk`) and an
Android 15 x86_64 emulator.

| Check | Result |
| --- | --- |
| `:app:assembleRelease` | Passed |
| `:app:lintRelease` | Passed, 0 errors (148 warnings: RTL/deprecation/style) |
| JVM unit tests (`:app:testReleaseUnitTest`) | Passed (3 test classes) |
| Emulator: fresh install defaults to Electric Violet | Passed |
| Emulator: Electric Violet and Emerald themes applied app-wide | Passed |
| Emulator: theme switching recreates Settings and Main correctly | Passed |
| Emulator: main screen, empty state, manual console card | Passed |
| Emulator: registration screen, PSN button visibility and PSN page | Passed |
| Emulator: Settings, virtual-control modes, fullscreen switch | Passed |
| Emulator: launch-animation preview and minified release launch | Passed |
| APK v1/v2/v3 signature | Valid, `CN=Anomaly` |
| 16 KB page alignment (`zipalign -c -P 16 4`) | Passed |
| Package / version | `app.anomaly.remoteplay`, `1.2.0` (versionCode 4) |
| Native libraries | arm64-v8a and armeabi-v7a |

**SHA-256 of `Anomaly-1.2.0.apk`:**

```
50fb1ec8d13e68c936e95b8400f2aa9da718ccbde07daa192bed83227a3a0ae0
```

- GitHub release: <https://github.com/shahittoshariear-spec/anomaly/releases/tag/v1.2.0>

## Hardware

- The project owner confirmed controller input works with a **GameSir Nova Lite** on an
  earlier build. 1.2.0 input changes are additive, so that path is unchanged.
- The **GameSir X5 Lite** and a live PS5 stream were not available during this validation.

## 1.2.0 changes

- Added seven themes, Electric Violet as default.
- Back gesture/swipe now explicitly stops the session and returns home with a toast; no
  new stop button.
- Added fullscreen toggle in the stream overlay and Settings.
- Motion sensors are opt-in and sample at 50 Hz for lower battery use and heat.
- Fixed toolbar theme inheritance, overlay animation conflicts, and the PSN button color.

## Not verified on hardware

- Live PS5/PS4 registration and streaming (no console/PSN account in this environment).
- Fullscreen toggle and back-stop while an actual stream is running; both paths compile,
  lint clean, and are exercised by the same window/session APIs used elsewhere.
- X5 Lite hardware.
- Internet play via PSN holepunch is not part of this Android build (see README).
