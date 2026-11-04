# Modifications to Chiaki-ng

This project is a modified build of Chiaki-ng commit
`43728af2e639fecb8a7af1cd68c29820a40de973` (see `work/chiaki-ng`). The original source
is available at <https://github.com/streetpea/chiaki-ng>.

## Branding

- `android/app/build.gradle`: application id `app.anomaly.remoteplay`, version 1.2.0,
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

## 1.2.0 features and fixes

- Seven selectable themes (`common/Preferences.Theme`, `common/ThemeManager.kt`,
  `common/ThemedActivity.kt`, `ThemeOverlay.Anomaly.*` styles): Electric Violet (default),
  Anomaly Cyan, Graphite, Emerald, Rust, Honey and Lapiz. Activities apply the overlay
  before `super.onCreate` and recreate if the theme changes in Settings.
- Stream exit is now the stop action: an `OnBackPressedDispatcher` callback shuts the
  session down, shows a brief "Remote Play stopped" toast and finishes. No stop button was
  added to the stream UI.
- Fullscreen toggle: a Settings switch and an in-stream overlay icon control immersive
  system bars. Tapping the video surface shows the overlay, so controls remain reachable
  with fullscreen disabled.
- Battery/thermal improvements: motion sensors are now opt-in (default off) and sample at
  50 Hz instead of 250 Hz when enabled; the stream already uses hardware decoding and a
  SurfaceView, and still stops decoding when the activity pauses.
- Fixed overlay show/hide animations fighting each other.
- Fixed the PSN outlined button rendering with the dark primary color instead of the accent.
- Moved the PSN sign-in button above the Account ID field so it is visible without scrolling.
- Guarded the PSN WebView teardown if the activity was never fully created.
- Stopped using a per-toolbar theme context so toolbar colors inherit the selected theme.

## 1.1.0 features and bug fixes

- Virtual controls preference with four modes: always show, hide when a physical
  controller is connected, always hide, and ask every time Remote Play starts. The mode is
  resolved when a stream opens and reacts to controller connect/disconnect events.
- In-stream virtual-control switch is now session-only; it no longer overwrites the saved
  mode.
- `stream/LaunchAnimationView.kt`: animated logo entry transition (converging lines, pulse,
  radial burst and fade) wired into `StreamActivity`, plus a Settings preview.
- Registration progress/success screen replaces the live registration log. Success shows
  "Signed in successfully" and returns home after 1.6 seconds; failure keeps the log behind
  a "View details" button.
- Modern UI pass: dark surfaces, cyan accents, rounded cards, a redesigned empty state, a
  proper app bar with subtitle, restyled registration screen, and a manual-console card.
- Fixed duplicate registration setting `RESULT_OK` before the overwrite/discard choice, so
  discarding could still report success.
- Wrapped duplicate overwrite (delete + insert + manual-host assignment) in a Room
  transaction so it cannot delete a registration without saving the replacement.
- Fixed both touch-control fragments cancelling each other's input collector; each fragment
  now has its own job.
- Fixed the login-pin / quit / create-error dialogs being assigned to a shadowed local, which
  left the activity's dialog reference null and could stack dialogs.
- Ignored intentional session-quit events while the activity is paused, so backgrounding the
  stream no longer closes the activity instead of reconnecting on resume.
- Cleared virtual-control touch state when the controls are hidden, preventing stuck buttons.
- Moved the controller-test device filter to require the full `SOURCE_GAMEPAD` flag and use
  the single-argument `getMotionRange`, so it no longer lists touchscreens or misses ranges.
- Let volume keys through in the controller test screen.
- Fixed the duplicate `android:checked="true"` radio button that left both PS5 and PS4 8+
  visually selected.
- Refreshed the auto-bitrate flow when FPS or codec changes, not only resolution.
- Guarded the PSN sign-in WebView/thread against callbacks after the activity is destroyed.
- Made virtual-control visibility observation robust when fragments are restored before the
  stream ViewModel is initialised.

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

## 1.3.0 interface changes

- `common/ThemeManager.kt`: palette resolution for previews and a shared overlay lookup.
- `settings/ThemePickerActivity.kt`, `settings/ThemePreviewAdapter.kt` and the associated
  layouts: full-screen theme picker with live preview.
- `common/ThemedActivity.kt`: captures the current screen and crossfades it into the
  rebuilt activity whenever the theme changes, including on return from Settings.
- `res/values/styles.xml`: seven per-palette alert-dialog themes and a themed popup menu,
  so dialogs and card menus follow the selected accent instead of Material defaults.
- `common/RegisteredHost.kt`, `common/ManualHost.kt`, `common/AppDatabase.kt`: optional
  `custom_name` columns, DAO updates and database migration 2 → 3.
- Console rename action in the card menu (`main/MainActivity.kt`,
  `main/DisplayHostRecyclerViewAdapter.kt`, `main/MainViewModel.kt`).
- `stream/LaunchAnimationView.kt`: rewritten launch sequence with theme-aware colors,
  impact rings, sparks and tap-to-skip.
- `main/MainActivity.kt`, `settings/SettingsActivity.kt` and layouts: direct settings icon,
  redesigned console cards, modernised settings and manual-entry screens, and no export
  section in preferences.

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
