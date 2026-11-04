# Build validation — Anomaly 1.3.0

Completed on the build machine (Windows, Android SDK in `tools/android-sdk`) and an
Android 15 x86_64 emulator.

| Check | Result |
| --- | --- |
| `:app:assembleRelease` | Passed |
| `:app:lintRelease` | Passed, 0 errors (161 warnings: RTL/deprecation/style) |
| JVM unit tests (`:app:testReleaseUnitTest`) | Passed (3 test classes) |
| Emulator: theme picker grid with live swatches, instant switch | Passed |
| Emulator: crossfade when changing theme and when returning to Settings | Passed |
| Emulator: seven palettes applied to app chrome, dialogs and popup menus | Passed |
| Emulator: direct settings icon in the app bar | Passed |
| Emulator: Settings without export/import section | Passed |
| Emulator: manual console card, status chip layout, overflow menu | Passed |
| Emulator: rename dialog, persistence after saving | Passed |
| Emulator: modernised manual-console entry screen | Passed |
| Emulator: launch-animation preview (converge, shockwave, burst, fade) | Passed |
| APK v1/v2/v3 signature | Valid, `CN=Anomaly` |
| 16 KB page alignment (`zipalign -c -P 16 4`) | Passed |
| Package / version | `app.anomaly.remoteplay`, `1.3.0` (versionCode 5) |
| Native libraries | arm64-v8a and armeabi-v7a |

**SHA-256 of `Anomaly-1.3.0.apk`:**

```
a27999ff93063cac9fdd50bca5d84435c59604f7b8c51e5b0591378bfc4fac56
```

## Hardware

- The project owner confirmed controller input works with a **GameSir Nova Lite** on an
  earlier build. Input handling is unchanged in 1.3.0.
- The **GameSir X5 Lite** and a live PS5 stream were not available during this validation.

## 1.3.0 changes

- New full-screen theme picker with palette previews; selecting a theme writes the
  preference and crossfades the current screen, so the change is visible immediately.
- Theme switching mechanism reworked: `ThemeManager` resolves palettes for previews, and
  each palette now has a matching dialog style so alerts and popups no longer fall back to
  Material purple.
- App bar overflow replaced by a direct settings icon.
- Settings export/import section removed.
- Added console renaming. `custom_name` columns were added to `registered_host` and
  `manual_host` with a Room migration (database version 3); names are editable from the
  card menu and shown in Settings.
- Redesigned console cards (tinted icon badge, status chip for Ready/Standby, rounded
  surfaces), empty state glow, modernised manual-console entry screen.
- Launch animation rewritten: staggered converging streaks, impact rings, orbital arcs,
  glow, spark burst, theme-aware accent, tap-to-skip.
- Popup menus now use a themed surface with rounded corners.
- Minor fixes: settings scrollbar hidden, FAB speed dial collapses when leaving the screen,
  edit-manual flow preserves a custom console name.

## Not verified on hardware

- Live PS5/PS4 registration and streaming (no console/PSN account in this environment).
- Fullscreen toggle and back-stop while an actual stream is running; both paths compile,
  lint clean, and are exercised by the same window/session APIs used elsewhere.
- X5 Lite hardware.
- Internet play via PSN holepunch is not part of this Android build (see README).
- Database upgrade from 1.2.0 (version 2) to 1.3.0 (version 3) is a plain `ALTER TABLE`
  migration; it was not run against a populated 1.2.0 install here.
