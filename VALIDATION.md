# Build validation — Anomaly 1.1.0

Completed on the build machine (Windows, Android SDK in `tools/android-sdk`) and an
Android 15 x86_64 emulator.

| Check | Result |
| --- | --- |
| `:app:assembleRelease` | Passed |
| `:app:lintRelease` | Passed, 0 errors (143 warnings: RTL/deprecation/style) |
| JVM unit tests (`:app:testReleaseUnitTest`) | Passed (3 test classes) |
| Fresh clone of the public repo + submodules (1.0.1 baseline) | Passed |
| Emulator: app launches, no crash | Passed |
| Emulator: main screen, empty state, manual console card | Passed |
| Emulator: registration screen and PSN sign-in page | Passed |
| Emulator: Settings and all four virtual-control modes | Passed |
| Emulator: launch-animation preview (converge, logo, burst, fade) | Passed |
| Emulator: minified release build launches and navigates | Passed |
| APK v1/v2/v3 signature | Valid, `CN=Anomaly` |
| 16 KB page alignment (`zipalign -c -P 16 4`) | Passed |
| Package / version | `app.anomaly.remoteplay`, `1.1.0` (versionCode 3) |
| Native libraries | arm64-v8a and armeabi-v7a |

**SHA-256 of `Anomaly-1.1.0.apk`:**

```
8ad2b64fb4fc0f39a45f16d9ee3c72d0f19d6950d244279204633f248a4e119f
```

- GitHub release: <https://github.com/shahittoshariear-spec/anomaly/releases/tag/v1.1.0>

## Hardware

- The project owner confirmed controller input works with a **GameSir Nova Lite** on the
  1.0.1 build. The 1.1.0 input changes are additive (axis fallbacks only), so the Nova Lite
  path is unchanged.
- The **GameSir X5 Lite** was not available during this validation; test with
  **Settings → Controller test**.

## 1.1.0 fixes

- Launch crash from 1.0.0 was fixed in 1.0.1 (package-relative layout behavior names).
- Registration now shows a progress/success screen instead of the raw log, then returns
  home automatically.
- Virtual controls now support four modes and detect controller hot-plug.
- Fixed duplicate-registration success result, Room transaction safety, dialog stacking,
  session pause/quit handling, touch-control collector cancellation, stuck virtual controls,
  controller-test filtering/ranges, volume keys, duplicate radio-button selection, bitrate
  refresh on FPS/codec change, and PSN WebView lifecycle callbacks.
- Modern UI and animated Remote Play entry transition added.

## Not verified

- PS5/PS4 registration and streaming require a console and PSN account and were not
  executed here. The PSN login page loads successfully in the app.
- The X5 Lite hardware was not available.
- Internet play via PSN holepunch is not part of this Android build (see README).
