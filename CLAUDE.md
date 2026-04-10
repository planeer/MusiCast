# CLAUDE.md - Project Guide for AI Assistants

## Project Overview

Kotlin Multiplatform (KMP) podcast player app targeting Android and iOS. The key differentiating feature is **automatic music detection** that reverts playback speed to 1x during music sections, then returns to the user's chosen speed for speech — solving a real pain point for music-heavy podcasts.

## Build & Run

```bash
# Compile shared code for Android
./gradlew :composeApp:compileDebugKotlinAndroid

# Build the Android app
./gradlew :composeApp:assembleDebug

# Install on a connected device
./gradlew :composeApp:installDebug

# iOS targets are disabled on non-macOS.
# On macOS: open MusiCast/iosApp/iosApp.xcodeproj in Xcode, or
# ./gradlew :composeApp:compileKotlinIosArm64
# ./gradlew :composeApp:podInstall   # regenerates the Podfile + installs TensorFlowLiteC
```

**Requirements**: Android SDK at path in `local.properties` (`sdk.dir=...`), JDK 17+, Gradle 8.10+. For iOS builds: Xcode + CocoaPods on macOS.

## Architecture

### Module Structure

- **`:composeApp`** — single KMP module combining all business logic, Compose Multiplatform UI, and the Android application shell. Source sets: `commonMain`, `androidMain`, `iosMain`.

### Key Patterns

- **expect/actual** for platform code: `AudioPlayer`, `PcmDecoder`, `EpisodeDownloader`, `DatabaseDriverFactory`
- **Interface + Koin** for platform code with asymmetric constructors: `YamNetClassifier` (Android needs `Context`, iOS does not)
- **Koin DI** — configured in `di/CommonModule.kt` + platform modules
- **SQLDelight** — schema in `composeApp/src/commonMain/sqldelight/com/musicast/musicast/db/PodcastDatabase.sq`
- **Compose Multiplatform** — single UI codebase in `ui/` package
- **StateFlow** — all state management via Kotlin coroutines flows

### Music Detection Pipeline

The core feature lives in `composeApp/src/commonMain/kotlin/com/musicast/musicast/audio/` and uses **Google's YAMNet** model running on-device via TensorFlow Lite for accurate speech/music classification.

1. **`PcmDecoder`** (platform-specific) — streams raw PCM audio in chunks (mono, 16kHz)
2. **`StreamingWindowBuffer`** — accumulates PCM into 0.975-second windows (15,600 samples)
3. **`YamNetClassifier`** (platform-specific) — runs YAMNet TFLite inference per window, returns 521 AudioSet class scores
4. **`SegmentClassifier`** — maps YAMNet scores to SPEECH/MUSIC, applies median filter + segment merging
5. **`MusicDetector`** — orchestrates the pipeline, returns `List<AudioSegment>`

**YAMNet** is a pre-trained audio classification model (521 classes from Google AudioSet). Speech-related class scores (indices 0-5, 12) are summed against music-related scores (indices 24-31 singing, 132-276 instruments/genres) to determine each window's label.

The pipeline uses **streaming processing** to avoid OOM on long episodes. The TFLite model file (`yamnet.tflite`, ~16MB) is bundled in `composeApp/src/androidMain/assets/` for Android and in `iosApp/iosApp/yamnet.tflite` for iOS.

Segments are persisted in the `segments_data` column as `"startMs:endMs:TYPE;..."` format.

### Playback

- **`PlaybackManager`** — central coordinator. Manages `userSpeed` vs `currentSpeed`, checks segments at current position, auto-adjusts speed. Saves playback position every 10 seconds.
- Android: Media3 ExoPlayer (`AndroidAudioPlayer`)
- iOS: AVFoundation AVPlayer (`IosAudioPlayer`)

### Android Media Session & Notification

The Android app integrates with the system media infrastructure via Media3's `MediaSessionService`:

- **`PlaybackService`** (`composeApp/src/androidMain/kotlin/com/musicast/musicast/PlaybackService.kt`) — `MediaSessionService` subclass that manages the `MediaSession`, foreground notification, and custom notification actions.
- **Lock screen / notification controls**: Custom button layout — skip back 15s, play/pause, skip forward 30s, speed toggle (renders current speed like "1.5x" as a dynamically generated bitmap icon via `IconCompat.createWithBitmap()`).
- **`SpeedAwareNotificationProvider`** — custom `DefaultMediaNotificationProvider` subclass that overrides `addNotificationActions()` to handle both `SessionCommand` (custom actions) and `playerCommand` (play/pause) buttons.
- **Audio focus**: ExoPlayer configured with `setAudioAttributes()` + `handleAudioFocus=true` — starting playback automatically pauses other media apps.
- **Samsung Now Bar / Dynamic Island**: Requires the service to be truly foreground. `MainActivity` calls `startForegroundService()` when playback starts, triggering `onStartCommand()` which calls `startForeground()` with a `MediaStyle` notification linked to the session. The `MediaSession` also needs `setSessionActivity()` with a `PendingIntent` so Samsung can resolve the tap target.
- **Metadata**: Set immediately on `onMediaItemTransition` so the system UI shows episode title/podcast name before playback fully starts.

### Data Flow

```
RSS Feed → RssFeedService → PodcastRepository → LocalDataSource → SQLDelight DB
                                                      ↕
Episode download → EpisodeDownloader → local file → PcmDecoder (16kHz) → StreamingWindowBuffer
    → YamNetClassifier (TFLite) → SegmentClassifier → segments saved to DB
                                                                  ↓
PlaybackManager ← loads segments on play ← LocalDataSource
```

## Important Source Files

Paths are relative to `composeApp/src/` unless noted otherwise. All Kotlin packages live under `com.musicast.musicast`.

| File | Purpose |
|------|---------|
| `commonMain/kotlin/com/musicast/musicast/audio/YamNetClassifier.kt` | Common interface for TFLite YAMNet inference |
| `androidMain/kotlin/com/musicast/musicast/audio/AndroidYamNetClassifier.kt` | Android TFLite interpreter implementation |
| `iosMain/kotlin/com/musicast/musicast/audio/IosYamNetClassifier.kt` | iOS TFLite C API implementation (uses `cocoapods.TensorFlowLiteC`) |
| `commonMain/kotlin/com/musicast/musicast/audio/MusicDetector.kt` | Orchestrates decode → YAMNet → classify pipeline |
| `commonMain/kotlin/com/musicast/musicast/audio/SegmentClassifier.kt` | Post-processes YAMNet scores into speech/music segments |
| `commonMain/kotlin/com/musicast/musicast/audio/StreamingWindowBuffer.kt` | Buffers PCM into 0.975s windows for YAMNet |
| `commonMain/kotlin/com/musicast/musicast/audio/AudioConstants.kt` | Shared constants (sample rate, window size, class count) |
| `commonMain/kotlin/com/musicast/musicast/player/PlaybackManager.kt` | Speed management, music detection during playback |
| `commonMain/kotlin/com/musicast/musicast/ui/viewmodel/EpisodeListViewModel.kt` | Download, analysis, and play orchestration |
| `commonMain/kotlin/com/musicast/musicast/data/local/LocalDataSource.kt` | All DB operations including segment persistence |
| `commonMain/sqldelight/com/musicast/musicast/db/PodcastDatabase.sq` | Database schema (SQLDelight) |
| `commonMain/kotlin/com/musicast/musicast/App.kt` | Main Compose entry point, navigation, Koin injection |
| `androidMain/kotlin/com/musicast/musicast/PlaybackService.kt` | MediaSessionService with notification controls, speed bitmap, Samsung Now Bar support |
| `androidMain/kotlin/com/musicast/musicast/MainActivity.kt` | Activity shell, notification permission, foreground service launch |
| `androidMain/kotlin/com/musicast/musicast/PodcastApplication.kt` | Android Application class that starts Koin |

## Common Pitfalls

- **SQLDelight dialect**: Uses `sqlite_3_18` — no `RETURNING` clause. Use `SELECT last_insert_rowid()` instead.
- **rss-parser API**: Feed item audio is at `rawEnclosure?.url`, not `enclosures`.
- **Composable scope**: `koinInject()` must be called at composable scope level, not inside `remember{}`.
- **iOS native targets**: Won't compile on Linux/Windows. Build warnings are expected and suppressed via `kotlin.native.ignoreDisabledTargets`.
- **Lifecycle version**: Currently pinned to `2.10.0` via the template's `libs.versions.toml`. The previous project required `2.8.4` for `lifecycle-viewmodel-compose` — if you hit runtime crashes or ViewModel scoping issues after an upgrade, try reverting to `2.8.4`.
- **Memory**: Audio analysis uses streaming pipeline — never accumulate full decoded audio in memory.
- **TFLite input resize**: YAMNet's TFLite model has a dynamic input shape. Must call `resizeInput(0, [15600])` + `allocateTensors()` after creating the interpreter.
- **TFLite multiple outputs**: YAMNet has 3 output tensors (scores, embeddings, spectrogram). Use `runForMultipleInputsOutputs` with ByteBuffer placeholders for unused outputs.
- **PcmDecoder resampling**: The resampler accumulator can go negative between chunks — guard `idx0 >= 0` before array access.
- **MediaSession notification ID**: Must use the same notification ID (1001) in both `onStartCommand()` and Media3's `MediaNotificationManager`. Using different IDs causes duplicate notifications (one plain, one media).
- **Samsung Now Bar**: Requires a true foreground service (`startForeground()`, not just `NotificationManager.notify()`), a `MediaStyle` notification with the session token, and a non-null `PendingIntent` (`setSessionActivity()` on the session + `setContentIntent()` on the notification).
- **Custom notification buttons**: All custom buttons must use `SessionCommand` (not `playerCommand`) to appear in the notification. The `onConnect` callback must remove default prev/next player commands and add custom session commands. Speed icon uses `IconCompat.createWithBitmap()` but still needs `setIconResId()` on the `CommandButton` for the `PlaybackStateCompat` compat layer (crashes with `IllegalArgumentException` otherwise).
- **iOS TFLite wiring**: TensorFlowLiteC is integrated via the `kotlin-cocoapods` Gradle plugin (`pod("TensorFlowLiteC", "~> 2.14.0")` in `composeApp/build.gradle.kts`). The Kotlin-side imports live under `cocoapods.TensorFlowLiteC.*`. On first-time setup on macOS, run `./gradlew :composeApp:podInstall` to generate the Podfile and install the pod before opening the Xcode project.
- **iOS framework name**: The shared framework is `ComposeApp` (not `shared`). Swift sources must `import ComposeApp`.

## Tuning Music Detection

The classifier in `SegmentClassifier.kt` uses these key parameters:

- **Speech indices**: 0-5, 12 (Speech, Child speech, Conversation, Narration, Babbling, Speech synthesizer, Whispering)
- **Music indices**: 24-31 (singing), 132-276 (music, instruments, genres)
- Classification: per-window sum of speech scores vs music scores — music wins if `musicScore > speechScore`
- `MIN_SEGMENT_DURATION_MS = 3000` — segments shorter than this get merged with neighbors
- Median filter kernel size = 5 windows — smooths classification noise

If detection is too aggressive or not sensitive enough, adjust the class index sets or add a bias factor to the score comparison in `SegmentClassifier.classify()`.
