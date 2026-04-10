# MusiCast

A Kotlin Multiplatform podcast player for Android and iOS with **automatic music detection** — the app detects when music is playing and temporarily reverts to 1x speed, then returns to your chosen speed when speech resumes. Perfect for music-heavy podcasts where you want to speed through discussion but enjoy the songs at normal tempo.

## Features

- **Add podcasts via RSS feed URL** — paste any podcast RSS feed to subscribe
- **Variable playback speed** — 0.5x to 3.0x in 0.1x increments, with quick presets (1x, 1.2x, 1.5x, 2x)
- **Automatic music detection** — pre-analyzes episodes to build a speech/music timeline, then auto-adjusts speed during playback
- **Visual segment map** — see music sections highlighted on the seek bar
- **Episode downloads** — download episodes for offline listening
- **Playback position persistence** — resume where you left off
- **System media integration** — lock screen controls, notification player with custom buttons (speed toggle, skip -15s/+30s), audio focus management, and Samsung Now Bar / Dynamic Island support
- **Cross-platform** — shared codebase for Android and iOS using Kotlin Multiplatform + Compose Multiplatform

## How Music Detection Works

When you download an episode, the app analyzes the audio in the background using **Google's YAMNet** — a pre-trained deep learning model that runs on-device via TensorFlow Lite:

1. The audio is decoded to 16 kHz mono PCM and streamed through a window buffer
2. Each 0.975-second window (15,600 samples) is fed to the YAMNet TFLite model
3. YAMNet returns scores for 521 audio classes (speech, music, instruments, singing, etc.)
4. Speech and music class scores are aggregated to label each window
5. A median filter smooths the labels, and short segments are merged into a clean timeline
6. During playback, the app checks your position against the timeline and adjusts speed automatically

The analysis runs with constant memory regardless of episode length — audio is streamed in chunks and only one window is held in memory at a time.

## Tech Stack

| Layer                  | Technology                                                       |
|------------------------|------------------------------------------------------------------|
| Shared logic & UI      | Kotlin Multiplatform + Compose Multiplatform                     |
| Android playback       | Media3 / ExoPlayer                                               |
| iOS playback           | AVFoundation / AVPlayer                                          |
| Database               | SQLDelight                                                       |
| Networking             | Ktor Client                                                      |
| RSS parsing            | rss-parser (prof18)                                              |
| Dependency injection   | Koin                                                             |
| Audio analysis         | TensorFlow Lite + YAMNet (on-device ML)                          |
| Media session          | Media3 MediaSessionService (lock screen, notifications, Now Bar) |
| iOS TFLite integration | `kotlin-cocoapods` Gradle plugin (`pod("TensorFlowLiteC")`)      |

## Building

### Prerequisites

- JDK 17+
- Android SDK (set `sdk.dir` in `local.properties`)
- For iOS: macOS with Xcode and CocoaPods installed

### Android

```bash
./gradlew :composeApp:assembleDebug
```

The APK will be at `composeApp/build/outputs/apk/debug/`.

Install directly on a connected device:

```bash
./gradlew :composeApp:installDebug
```

### iOS

On macOS, first let Gradle generate the Podfile and install the TensorFlowLiteC pod:

```bash
./gradlew :composeApp:podInstall
```

Then either open `iosApp/iosApp.xcodeproj` in Xcode and run, or build from the command line:

```bash
./gradlew :composeApp:compileKotlinIosArm64
```

Make sure `iosApp/iosApp/yamnet.tflite` is included in the Xcode target's **Copy Bundle Resources** build phase.

## Project Structure

```
├── composeApp/                          # Single KMP module (shared code + Android app)
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/musicast/musicast/
│       │   │   ├── audio/               # Music detection pipeline (YAMNet TFLite, classifier)
│       │   │   ├── player/              # Playback management & speed control
│       │   │   ├── data/                # Repository, local DB, RSS service
│       │   │   ├── domain/              # Data models
│       │   │   ├── download/            # Episode downloader
│       │   │   ├── di/                  # Koin dependency injection
│       │   │   └── ui/                  # Compose Multiplatform screens & components
│       │   └── sqldelight/              # Database schema
│       ├── androidMain/
│       │   ├── kotlin/com/musicast/musicast/  # Android platform impls, PlaybackService, MainActivity
│       │   ├── assets/yamnet.tflite     # Bundled YAMNet model
│       │   └── res/                     # Icons, themes, strings
│       └── iosMain/
│           └── kotlin/com/musicast/musicast/  # iOS platform impls (AVPlayer, TFLite via cocoapods)
├── iosApp/                              # iOS application shell (SwiftUI + generated Podfile)
└── gradle/                              # Gradle wrapper & version catalog
```

## Usage

1. Launch the app
2. Tap **+** to add a podcast by pasting its RSS feed URL
3. Tap a podcast to see its episodes
4. Download an episode (analysis runs automatically after download)
5. Play the episode — music sections appear highlighted on the seek bar
6. Set your preferred speed (e.g. 2x) and enable music detection
7. The app automatically drops to 1x during music and returns to 2x for speech

## License

This project is for personal use.
