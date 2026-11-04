# Build validation — Anomaly 1.0.0

Completed on the build machine (Windows, Android SDK in `tools/android-sdk`).

| Check | Result |
| --- | --- |
| `:app:assembleRelease` | Passed |
| Fresh clone of the public repo + submodules, `:app:assembleRelease` | Passed |
| `:app:lintVitalRelease` (part of release build) | Passed |
| JVM unit tests (`:app:testReleaseUnitTest`) | Passed (3 test classes) |
| APK v1/v2/v3 signature | Valid, `CN=Anomaly` |
| 16 KB page alignment (`zipalign -c -P 16 4`) | Passed |
| Package name | `app.anomaly.remoteplay` |
| Launcher label | `Anomaly` |
| Native libraries | `libchiaki-jni.so`, `liboboe.so`, `libc++_shared.so` for arm64-v8a and armeabi-v7a |
| New activities present in manifest | `PsnLoginActivity`, `ControllerTestActivity` |

**SHA-256 of `Anomaly-1.0.0.apk`:**

```
20afb2d72682edff28ede1957531a02fb8de4f3d8c486385c0b26c2701f23e03
```

- Built ABIs: arm64-v8a and armeabi-v7a
- GitHub release: <https://github.com/shahittoshariear-spec/anomaly/releases/tag/v1.0.0>

## Not verified

- No Android device was connected to ADB during the build, so the APK was not installed or
  exercised on hardware.
- PSN authentication and PS5 registration/session startup require the owner's account and
  console and were not executed here. The OAuth endpoint was confirmed reachable and the
  account-id conversion follows Chiaki-ng's desktop implementation.
- Controller event mapping for the GameSir X5 Lite was implemented against Android's input
  API; confirm it on the device with **Settings → Controller test**.
- Internet play via PSN holepunch is not part of this Android build (see README).

## Unit test note

The first test invocation failed because of a Windows environment quirk: a PATH entry
containing spaces was parsed as part of the test worker classpath. Running Gradle with a
minimal PATH succeeded. This does not affect the APK build.
