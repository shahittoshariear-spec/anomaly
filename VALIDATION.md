# Build validation — Anomaly 1.0.1

Completed on the build machine (Windows, Android SDK in `tools/android-sdk`).

| Check | Result |
| --- | --- |
| `:app:assembleRelease` | Passed |
| Fresh clone of the public repo + submodules, `:app:assembleRelease` | Passed |
| JVM unit tests (`:app:testReleaseUnitTest`) | Passed (3 test classes) |
| Android 15 emulator (x86_64): app launches | Passed |
| Emulator: PSN Sign In page loads in the WebView | Passed |
| Emulator: Settings and Controller test open | Passed |
| APK v1/v2/v3 signature | Valid, `CN=Anomaly` |
| 16 KB page alignment (`zipalign -c -P 16 4`) | Passed |
| Package name / version | `app.anomaly.remoteplay`, `1.0.1` (versionCode 2) |
| Launcher label | `Anomaly` |
| Native libraries | `libchiaki-jni.so`, `liboboe.so`, `libc++_shared.so` for arm64-v8a and armeabi-v7a |
| New activities present in manifest | `PsnLoginActivity`, `ControllerTestActivity` |

**SHA-256 of `Anomaly-1.0.1.apk`:**

```
81707065ddc425cbbb09df10877f5ee2c281f24f97d0c931c5daa7558b7c0a95
```

- Built ABIs: arm64-v8a and armeabi-v7a
- GitHub release: <https://github.com/shahittoshariear-spec/anomaly/releases/tag/v1.0.1>

## Fixes in 1.0.1

- **Launch crash fixed.** Two `app:layout_behavior` values in `activity_main.xml` used the
  relative class names `.main.FloatingActionButtonBackgroundBehavior` and
  `.main.FloatingActionButtonSpeedDialBehavior`. Those resolve against the manifest package,
  which is the application id (`app.anomaly.remoteplay`), not the code namespace
  (`com.metallic.chiaki`). The resulting `ClassNotFoundException` killed the app while
  inflating the main layout. Both attributes now use fully-qualified class names.
- Added `fitsSystemWindows` to the activity layouts so toolbars sit below the status bar on
  Android 15's edge-to-edge display.
- Fixed the controller test device filter, which matched touchscreens because
  `SOURCE_GAMEPAD` shares the button-class bit.

## Not verified

- No physical Android device was connected. The APK was tested on an Android 15 x86_64
  emulator; the ARM release APKs contain the same code.
- PSN authentication, PS5 registration and session startup require the owner's account and
  console and were not executed here. Sony's login page loads successfully in the app.
- Controller event mapping for the GameSir X5 Lite was implemented against Android's input
  API; confirm it on the device with **Settings → Controller test**.
- Internet play via PSN holepunch is not part of this Android build (see README).
