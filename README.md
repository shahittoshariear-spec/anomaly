# Anomaly

Anomaly is an Android PlayStation Remote Play client for using **third-party controllers**
with a PS5 or PS4. The official PlayStation Remote Play app only accepts Sony controllers;
Anomaly reads standard Android gamepad input, so pads such as the **GameSir X5 Lite** work
for streaming.

It is a modified build of [Chiaki-ng](https://github.com/chiaki-ng/chiaki-ng), with a PSN
sign-in flow and a controller diagnostic screen added.

- **Download the APK:** [latest release](https://github.com/shahittoshariear-spec/anomaly/releases/latest)
- **Package:** `app.anomaly.remoteplay`
- **Version:** 1.0.0
- **Android:** 7.0+ (API 24)
- **ABIs:** arm64-v8a, armeabi-v7a

> Anomaly is not a copy of PX Play and contains no PX Play code or resources. It is an
> independent build of an open-source remote-play client.

## Features

- PS5 and PS4 Remote Play on the local network
- Any standard Android HID gamepad, including Xbox-style pads and the GameSir X5 Lite
- In-app PSN sign-in that fills the PSN Account ID during console registration
- Controller test screen showing live buttons, axes, triggers and ranges
- Analog sticks and triggers, D-pad, rumble, and controller motion sensors when exposed
- 1080p / 60 FPS streaming, H.264/H.265 hardware decoding, on-screen touch controls
- Wake-on-LAN for a console in standby

## Requirements

- A PS5 or PS4 with **Remote Play enabled**
- The console and phone on the **same network** (5 GHz Wi-Fi recommended)
- A PSN account linked to the console
- The 8-digit PIN shown by the console's **Link Device** screen

## Install

Download `Anomaly-1.0.0.apk` from the [latest release](https://github.com/shahittoshariear-spec/anomaly/releases/latest)
and open it on the phone. Allow installation from the browser/file manager when prompted.

Or with ADB:

```sh
adb install -r Anomaly-1.0.0.apk
```

## Set up the PS5

1. On the PS5: **Settings → System → Remote Play**, enable **Enable Remote Play**.
2. Choose **Link Device** and keep the 8-digit PIN on screen.
3. Open Anomaly and use the **+** menu → **Register Console**.
4. Leave the host at `255.255.255.255` and broadcast enabled, or enter the PS5 IP manually
   (PS5: **Settings → Network → View Connection Status**).
5. Select **PS5**, tap **Sign in to PlayStation Network**, and sign in with the PSN account
   that owns the console. The Account ID field fills automatically.
6. Enter the 8-digit PIN and tap **Register**.

Then select the console on the main screen. If the PS5 asks for a login PIN, enter the PIN
shown on the TV.

### Controller test

Open **Settings → Controller test**. Connect the gamepad, then press every button and move
the sticks and triggers. The screen shows exactly what Android reports. If a button does
not appear there, Android is not delivering it and no app can map it.

Any Android-compatible GameSir controller can be used for testing while waiting for an X5
Lite; the app does not depend on a specific GameSir model. Use the controller's Android/HID
mode if it has a mode switch.

## Limitations

- **Local network only.** Internet play through PSN holepunch (a Chiaki-ng desktop feature)
  is not exposed by this Android build yet. A VPN back to the home network is the simplest
  workaround; port forwarding is untested.
- Anomaly streams the console's Remote Play output; it does not bypass PlayStation Network
  authentication or console registration.
- HDR and some DualSense-specific features depend on the upstream Chiaki-ng implementation.

## Build from source

The build is validated on Windows. The native sources are portable, but the bundled host
tool and signing setup target Windows.

### Prerequisites

- JDK 17
- Android SDK with:
  - platform `android-35`
  - build-tools `35.0.0`
  - NDK `28.2.13676358`
  - CMake `3.22.1`
- Git

### Checkout

```sh
git clone https://github.com/shahittoshariear-spec/anomaly.git
cd anomaly
git submodule update --init --depth 1 \
  third-party/nanopb third-party/jerasure third-party/gf-complete \
  third-party/curl android/app/src/main/cpp/oboe
```

The repository vendors the Android OpenSSL 1.1.1w static libraries and a Windows `protoc`
under `android/app/src/main/cpp/`, so no OpenSSL build or system protoc is needed.

### Build

```bat
set JAVA_HOME=C:\path\to\jdk-17
set ANDROID_HOME=C:\path\to\android-sdk
cd android
gradlew.bat :app:assembleRelease --no-daemon
```

The APK is written to `android/app/build/outputs/apk/release/app-release.apk`.
Without a signing configuration, `assembleRelease` produces an unsigned APK; use
`assembleDebug` for a locally installable build, or configure signing as below.

### Signing

Create a keystore and add `android/local.properties`:

```properties
sdk.dir=C:/path/to/android-sdk
chiakiKeystore=your.keystore
chiakiKeystorePW=your-store-password
chiakiKeyAlias=your-key-alias
chiakiKeyPW=your-key-password
```

`chiakiKeystore` is resolved relative to `android/app/`. Never commit the keystore or
`local.properties`.

## Changes from Chiaki-ng

See [MODIFICATIONS.md](MODIFICATIONS.md) for the full list. The main additions are the PSN
OAuth sign-in activity, the registration-screen integration, generic gamepad axis/key
handling, the controller test screen, Anomaly branding, and Windows build fixes.

## License

Anomaly is distributed under the **AGPL-3.0-only with an OpenSSL exception**, inherited
from Chiaki-ng. See [COPYING](COPYING) and [LICENSES](LICENSES). The source is based on
Chiaki-ng commit `43728af2e639fecb8a7af1cd68c29820a40de973`.

Third-party components and their licenses are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

PlayStation, PS5 and PSN are trademarks of Sony Interactive Entertainment. This project is
not affiliated with or endorsed by Sony. PX Play is a separate commercial product by
Grill2010; its APK and resources are not used here.
