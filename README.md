# Parental Monitor

A Flutter Android app that runs in the background, captures screenshots at configurable intervals, and sends them via email using SMTP. Designed for parental monitoring on your own devices.

> **Important:** This app requires the Android `MediaProjection` API which means a system dialog will appear asking for screen capture permission when you start monitoring. The app cannot silently capture screenshots without user awareness — this is by design on Android.

---

## Features

| Feature | Details |
|---|---|
| **Background Screen Capture** | Uses Android `MediaProjection` + `VirtualDisplay` + `ImageReader` to capture full-screen screenshots |
| **Foreground Service** | Runs as a persistent foreground service with a notification (required on Android 14+) |
| **Configurable Interval** | Set capture frequency from 1 minute up to 24 hours |
| **Automated Email Delivery** | Sends screenshots via SMTP (Gmail, Outlook, SendGrid, or any SMTP server) |
| **Settings Persistence** | SMTP configuration is saved locally using `SharedPreferences` |
| **Material 3 UI** | Clean, modern interface with light/dark theme support |

---

## How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                       Flutter Layer                          │
│                                                              │
│  HomeScreen    ──►  MonitorService (MethodChannel)           │
│  (UI/Config)         (Dart platform channel bridge)          │
└───────────────────────────┬─────────────────────────────────┘
                            │
                    MethodChannel
                    (com.parentalmonitor/screenshot)
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                      Native Android Layer                     │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐   │
│  │  MainActivity                                          │   │
│  │  • Handles MediaProjection permission dialog           │   │
│  │  • Launches/stops ScreenshotService                    │   │
│  │  • Persists config across activity recreation          │   │
│  └──────────────┬────────────────────────────────────────┘   │
│                 │                                              │
│                 ▼                                              │
│  ┌───────────────────────────────────────────────────────┐   │
│  │  ScreenshotService (Foreground Service)                 │   │
│  │                                                         │   │
│  │  • MediaProjection + VirtualDisplay + ImageReader       │   │
│  │  • Periodic capture via Handler (configurable interval) │   │
│  │  • PNG compression + file storage                       │   │
│  │  • Email sending via Jakarta Mail (smtp)                │   │
│  └───────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
```

### Step-by-step flow

1. **User configures** SMTP settings and capture interval in the Flutter UI
2. **Taps "Start Monitoring"** — sends config via `MethodChannel` to native `MainActivity`
3. **Android system dialog** appears asking for screen capture permission
4. **Permission granted** — `ScreenshotService` starts as a foreground service
5. **On each interval** — `ImageReader` captures the current screen content via `VirtualDisplay`, saves it as a PNG
6. **After each capture** — the screenshot is sent via SMTP email using Jakarta Mail (JavaMail)
7. **"Stop Monitoring"** — stops the service and releases all resources

---

## Prerequisites

| Requirement | Version |
|---|---|
| Flutter SDK | ^3.11.3 (or compatible) |
| Dart SDK | ^3.11.3 |
| Android Studio / IntelliJ | Latest recommended |
| Android Device | API 26+ (Android 8.0+) |
| Google Chrome (for web only) | Latest |

---

## Getting Started

### 1. Clone / Navigate to the project

```bash
cd /home/atef/projects/android-snapshots
```

### 2. Install Flutter dependencies

```bash
flutter pub get
```

### 3. Run on a connected device or emulator

```bash
flutter run
```

The app will build and install on your connected Android device or running emulator.

> **Recommended:** Use a physical device for testing screen capture — emulators may have limited `MediaProjection` support.

---

## Running on the Web

> ⚠️ **This app does not support running as a web application.**

The app relies on the following Android-specific APIs that have no web equivalent:

| Component | Android API | Web Alternative |
|---|---|---|
| `MethodChannel` | Native bridge | ❌ Not available |
| `MediaProjection` | Screen capture | ❌ Not available |
| `VirtualDisplay` + `ImageReader` | Frame buffer capture | ❌ Not available |
| `Foreground Service` | Background execution | ❌ Not available |
| `Jakarta Mail` | SMTP email | ❌ Not available |

If you attempt to run `flutter run -d chrome`, the build will fail or the app will crash at runtime when it tries to invoke the native platform channels.

---

## Building an APK

### Debug APK (for testing)

```bash
flutter build apk --debug
```

The APK will be generated at:

```
build/app/outputs/flutter-apk/app-debug.apk
```

### Release APK (for distribution)

```bash
flutter build apk --release
```

The APK will be generated at:

```
build/app/outputs/flutter-apk/app-release.apk
```

### Split APK per architecture (smaller file sizes)

```bash
flutter build apk --release --split-per-abi
```

This produces separate APKs for each supported CPU architecture:

| ABI | File |
|---|---|
| ARM 64-bit | `build/app/outputs/flutter-apk/app-arm64-v8a-release.apk` |
| ARM 32-bit | `build/app/outputs/flutter-apk/app-armeabi-v7a-release.apk` |
| x86_64 | `build/app/outputs/flutter-apk/app-x86_64-release.apk` |

> **Tip:** For most modern Android devices (2020+), you only need the `arm64-v8a` variant.

### App Bundle (recommended for Google Play Store)

```bash
flutter build appbundle --release
```

The AAB will be generated at:

```
build/app/outputs/bundle/release/app-release.aab
```

### Signing the Release APK

The current `android/app/build.gradle.kts` uses the **debug signing config** for release builds:

```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("debug")
    }
}
```

**For production distribution, you should configure a release keystore.** See the [Flutter Android signing documentation](https://docs.flutter.dev/deployment/android#signing-the-app) for detailed instructions.

---

## Configuration

### SMTP Settings

| Field | Description | Example |
|---|---|---|
| **SMTP Host** | Your email provider's SMTP server | `smtp.gmail.com` |
| **SMTP Port** | SMTP port (587 for TLS, 465 for SSL) | `587` |
| **SMTP Username** | Your email address | `you@gmail.com` |
| **SMTP Password** | Your app-specific password | *(see below)* |
| **Recipient Email** | Where screenshots are sent | `parent@example.com` |

### Getting a Gmail App Password

For Gmail accounts with 2-factor authentication enabled:

1. Go to https://myaccount.google.com/apppasswords
2. Select "Mail" as the app and "Other" as the device
3. Generate a 16-character app password
4. Use this in the **SMTP Password** field (not your regular Gmail password)

> **Note:** If you don't have 2FA enabled on your Gmail account, you may need to enable "Less secure app access" or use an app password.

### Other SMTP Providers

| Provider | SMTP Host | Port | Notes |
|---|---|---|---|
| **Gmail** | `smtp.gmail.com` | 587 | Requires app password |
| **Outlook/Hotmail** | `smtp-mail.outlook.com` | 587 | May require OAuth2 |
| **Yahoo Mail** | `smtp.mail.yahoo.com` | 587 | Requires app password |
| **SendGrid** | `smtp.sendgrid.net` | 587 | Use API key as password |
| **Custom SMTP** | Your server | 587/465 | Your credentials |

---

## Permissions

The app requests the following Android permissions:

| Permission | Purpose |
|---|---|
| `FOREGROUND_SERVICE` | Required to run a foreground service (Android 9+) |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Required for MediaProjection foreground service (Android 14+) |
| `POST_NOTIFICATIONS` | Required to show the persistent notification (Android 13+) |
| `INTERNET` | Required for sending emails via SMTP |

The screen capture permission is requested at runtime via the system `MediaProjection` dialog when you tap "Start Monitoring".

---

## Project Structure

```
android-snapshots/
├── android/
│   ├── app/
│   │   ├── build.gradle.kts          # Android build config + dependencies
│   │   └── src/main/
│   │       ├── AndroidManifest.xml    # Permissions & service declarations
│   │       └── kotlin/.../
│   │           ├── MainActivity.kt    # MethodChannel + permission handling
│   │           └── ScreenshotService.kt  # Foreground service + capture + email
│   ├── build.gradle.kts              # Root Android build config
│   └── settings.gradle.kts           # Android project settings
├── lib/
│   ├── main.dart                     # App entry point + Material 3 theming
│   ├── screens/
│   │   └── home_screen.dart          # Full settings UI
│   └── services/
│       └── monitor_service.dart      # MethodChannel bridge to native
├── test/
│   └── widget_test.dart              # Basic widget test
├── pubspec.yaml                      # Flutter dependencies
├── analysis_options.yaml             # Dart linter config
└── README.md                         # This file
```

---

## Technical Limitations

| Limitation | Explanation |
|---|---|
| **FLAG_SECURE** | Apps/games that set `FLAG_SECURE` (banking apps, password managers, streaming services like Netflix) will appear as a black screen and cannot be captured |
| **User consent** | Android requires a system dialog for screen capture every time the service starts |
| **OEM battery optimization** | Xiaomi, Huawei, Samsung, and other OEMs aggressively kill background services — you may need to disable battery optimization for this app in system settings |
| **Google Play Store** | Apps using `MediaProjection` for continuous screen capture are typically banned from the Play Store (personal/sideload use only) |
| **Battery drain** | Frequent screenshotting is resource-intensive (CPU, memory, disk I/O for capture + network for email) |
| **Single capture per interval** | The service captures one screenshot per interval, not a video stream |
| **No iOS support** | This app is Android-only (uses Android-specific APIs) |

---

## Troubleshooting

### Build fails with "minSdk 26"

Ensure your Flutter SDK and Gradle are up to date. The app targets Android 8.0+.

### Screenshots show a black screen

The app or screen you're trying to capture likely has `FLAG_SECURE` enabled. This is common with:
- Banking apps
- Password managers
- Video streaming apps (Netflix, Disney+, etc.)
- Payment screens

### Service stops unexpectedly

OEM battery optimization is usually the cause:
1. Go to **Settings → Apps → Parental Monitor → Battery**
2. Select **"Unrestricted"** or disable battery optimization
3. On Samsung: **Settings → Device Care → Battery → Background Usage Limits → Never Sleeping Apps** → Add Parental Monitor

On some Chinese phones (Xiaomi, Huawei, OnePlus), you may need to navigate deep into the settings to find the "Auto-start" manager.

### Email sending fails

- Verify your SMTP credentials
- For Gmail: ensure you're using an [App Password](https://myaccount.google.com/apppasswords), not your regular password
- Check that the SMTP port and host are correct
- Ensure the device has internet connectivity
- Some SMTP providers block connections from unfamiliar IPs

### Permission dialog doesn't appear

- Ensure you're running on Android 8.0+ (API 26+)
- If the app was previously denied screen capture permission, you may need to clear app data or reinstall
- Check that the notification permission was granted

---

## FAQ

**Q: Can the app run when the phone is locked?**  
Yes — the foreground service keeps running even when the screen is off. However, a screenshot of the lock screen will be captured if the device is locked.

**Q: Will this work on a Samsung/ Xiaomi/ Huawei phone?**  
Yes, but you may need to disable battery optimization and enable auto-start for the app in system settings.

**Q: Can I use this without an internet connection?**  
Screenshots will be captured and saved, but cannot be emailed until the device is back online (email sending will fail silently).

**Q: How much storage do screenshots use?**  
Each screenshot is ~200KB–1MB (PNG compressed). At a 5-minute interval, that's roughly 12–60MB per hour (before deletion). Screenshots are stored in the app's internal storage and accumulate until you stop the service.

**Q: Is this app available on the Play Store?**  
No. Apps using `MediaProjection` for continuous background screen capture generally violate Google Play Store policies. This app is designed for personal use via sideloading.

---

## License

This project is provided for personal/educational use. Please respect the privacy of others and comply with all applicable laws and regulations in your jurisdiction regarding screen recording and monitoring.
