# Modifications to Chiaki-ng

This project is a modified build of Chiaki-ng commit
`43728af2e639fecb8a7af1cd68c29820a40de973` (see `work/chiaki-ng`). The original source
is available at <https://github.com/streetpea/chiaki-ng>.

## Branding

- `android/app/build.gradle`: application id `app.anomaly.remoteplay`, version 1.0.1,
  arm64-v8a and armeabi-v7a, local signing.
- `android/settings.gradle`: project name `Anomaly`.
- `android/app/src/main/res/values/strings.xml`: app name and main title.
- `android/app/src/main/res/values/colors.xml`: dark/cyan theme matching the icon.
- `android/app/src/main/res/mipmap-*`, `ic_launcher_background.xml`: supplied Anomaly icon.
- File provider authority, log file prefix and exported-settings filename renamed to
  Anomaly.
- Added an **About Anomaly** entry with version, credits and license information.

## 1.0.1 crash and layout fixes

- `android/app/src/main/res/layout/activity_main.xml`: use fully-qualified class names for
  the two CoordinatorLayout behaviors. The previous `.main.*` names resolved against the
  application id (`app.anomaly.remoteplay`) instead of the code namespace
  (`com.metallic.chiaki`), causing a `ClassNotFoundException` while inflating the main
  activity.
- Added `android:fitsSystemWindows="true"` to the main, registration, manual console,
  registration progress, settings, PSN login and controller test layouts so Android 15
  edge-to-edge does not place toolbars under the status bar.
- `settings/ControllerTestActivity.kt`: require the full `SOURCE_GAMEPAD` flag instead of
  any matching source bit, so touchscreens are not listed as gamepads.

## New features

- `regist/PsnLoginActivity.kt` and `res/layout/activity_psn_login.xml`: in-app PSN OAuth
  sign-in. The redirect authorization code is exchanged for a token, the PSN user id is
  converted to the 8-byte account id and returned to the registration screen.
- `regist/RegistActivity.kt`, `res/layout/activity_regist.xml`: **Sign in to PlayStation
  Network** button which fills the PSN Account ID automatically.
- `settings/ControllerTestActivity.kt` and `res/layout/activity_controller_test.xml`:
  diagnostic screen showing connected gamepads, axis ranges and live key/motion events.
  Added to `res/xml/preferences.xml` and `SettingsFragment.kt`.
- `session/StreamInput.kt`: support for third-party HID gamepads:
  - D-pad events delivered as `KEYCODE_DPAD_*` key events
  - right-stick axes reported as `AXIS_RX`/`AXIS_RY` instead of `AXIS_Z`/`AXIS_RZ`
  - triggers reported as `AXIS_GAS`/`AXIS_BRAKE`
  - trigger ranges normalized from each axis's reported minimum/maximum, so pads that
    rest at -1 behave correctly

## Windows build fixes

These only affect the build environment; streaming behavior is unchanged.

- `CMakeLists.txt`, `lib/CMakeLists.txt`: use prebuilt OpenSSL static libraries from
  `android/app/src/main/cpp/openssl` instead of building OpenSSL as an external project.
- `cmake/JsoncExternalProject.cmake`, `cmake/MiniupnpcExternalProject.cmake`: copy headers
  into the include bridge instead of creating symbolic links, which fail without Windows
  developer mode.
- `lib/protobuf/CMakeLists.txt`: do not rewrite `PATH` when cross-compiling on a Windows
  host; CMake splits the Windows `PATH` on semicolons and the command fails.
- `android/app/build.gradle`: pass the bundled host `protoc` to CMake.
- Vendored host `protoc.exe` and generated `third-party/nanopb/generator/proto/nanopb_pb2.py`
  so the protocol bindings build without a system protoc.

## Not yet implemented

- PSN holepunch / internet Remote Play (the desktop Chiaki-ng feature). The Android JNI
  and UI do not expose it in this build.

## Repository packaging

- Upstream `README.md` was preserved as `README.chiaki-ng.md`; the Anomaly README is the
  repository front page.
- Upstream `.github` workflows, issue templates and funding metadata were removed so that
  tagging this repository does not run Chiaki-ng's CI or release pipeline.
- The About dialog links to this repository instead of mentioning a local source folder.
- Both arm64-v8a and armeabi-v7a APK splits are built.
- `lib/protobuf/nanopb_pb2.py` is bundled and copied into the nanopb generator directory at
  configure time, because a fresh submodule checkout does not contain it and generating it
  requires protoc on `PATH`.
- OpenSSL 1.1.1w static libraries and a Windows `protoc` binary are vendored under
  `android/app/src/main/cpp/` for self-contained builds. Their licenses are included.
