# Music Player

A small, fast, **offline** music player for Android 8.0 and newer. It is made for phones and for **car head units** (Android car stereos): it finds every song and every folder on the device, including USB drives and SD cards, and it has a real equalizer.

* **Tiny:** the installable APK is under 100 KB. No libraries, no ads, no tracking.
* **Private:** the app has no internet permission. Nothing leaves the device.
* **Every folder, every song:** Songs, Folders, Albums and Artists tabs, search, and a folder browser that shows each storage volume.
* **Equalizer:** presets, a band slider per frequency, bass boost, surround and volume boost.
* **Built for the car:** in landscape on a wide screen the player and the library share one screen, with large touch targets. Steering-wheel, Bluetooth and headset media keys are handled, and can start your last queue when the app is closed.

## Screenshots

These are renders of the app's real layouts with sample (fictional) songs and generated cover art. Phone screens are on top, and a 1280 × 720 car head unit in landscape is below.

<img src="docs/screenshots/overview.png" alt="Songs, Folders, Now playing and Equalizer on a phone; the player with the library, and the equalizer, on a car head unit">

Single screens: [Songs](docs/screenshots/phone-songs.png), [Folders](docs/screenshots/phone-folders.png), [Albums](docs/screenshots/phone-albums.png), [Now playing](docs/screenshots/phone-now-playing.png), [Equalizer](docs/screenshots/phone-equalizer.png), [Up next](docs/screenshots/phone-queue.png), [Car library](docs/screenshots/car-library.png), [Car equalizer](docs/screenshots/car-equalizer.png).

## Install

1. Get the APK:
   * **From GitHub:** open the **Actions** tab, pick the latest **Build APK** run and download the `MusicPlayer-release-apk` artifact (a zip containing `app-release.apk`), or
   * **Build it yourself:** see [Build from source](#build-from-source).
2. Copy `app-release.apk` to the device. On a head unit, use a USB stick and the unit's file manager.
3. Open the file and allow **Install unknown apps** for the app you opened it with when Android asks.
4. Or, from a computer with `adb`: `adb install -r app-release.apk`

On first start the app asks for permission to read music (and, on Android 13+, to show the playback notification).

## Using it

| You want to | Do this |
|---|---|
| Play a song | Tap it. The list it came from (all songs, a folder, an album) becomes the queue. |
| Play a whole folder, album or artist | Open it and tap **Play all** or **Shuffle**. |
| Add to the queue | Long-press a song, folder, album or artist: **Play next** or **Add to queue**. |
| See or edit the queue | Open the player, then the queue icon. Tap to jump, long-press to remove. |
| Search | Tap the magnifier. It searches title, artist, album and file name. |
| Use the equalizer | Tap the equalizer icon. Picking a preset or moving a slider turns it on. |
| Find music that is not listed | Menu (⋮) → **Scan storage for music**. This indexes new files on internal storage, SD cards and USB drives. |
| See ringtones and voice notes too | Menu (⋮) → **Show all audio files**. |

The app remembers your queue and position and carries on where you left off.

### On a car head unit

* Install the APK from a USB stick, then put your music on a USB drive or SD card. If it does not appear after plugging it in, use **Scan storage for music**.
* On screens at least 640 dp wide and 400 dp tall in landscape (most head units), the player sits on the left and the library on the right. Rows are 72 dp tall and the play button is 96 dp.
* Steering-wheel keys, Bluetooth and headset buttons send standard media keys, which the app handles. When the app is not running they should start your last queue (not yet verified on a real head unit).
* The equalizer uses the audio effects built into Android. Some head units process sound in their own DSP instead; the screen says so when Android's equalizer is not available, and offers a shortcut to the system sound settings when one exists.

## Build from source

You need JDK 17 or newer and the Android SDK with platform 35 (Android Studio installs both). The Robolectric tests below need JDK 21 or newer; Android Studio's bundled JDK is fine.

```bash
./gradlew assembleRelease        # macOS / Linux
```

```bat
gradlew.bat assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. If you build without Android Studio, create `local.properties` containing `sdk.dir=/path/to/Android/sdk`.

**Release signing.** Without any setup, the release APK is signed with the local debug key, which is enough to install it. To sign with your own key, create `keystore.properties` next to `build.gradle` (it is git-ignored) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Android only updates an installed app when the new APK is signed with the same key.

### Tests

```bash
./gradlew testDebugUnitTest lintRelease
```

* Plain JVM tests for the play queue (shuffle, repeat, editing), the folder tree and volume naming, equalizer presets and text formatting.
* Robolectric smoke tests that open every screen against a fake MediaStore and play through a fake `MediaPlayer`. They download an Android SDK jar from Maven Central on first run.

The GitHub Actions workflow in `.github/workflows/build.yml` builds the APK on every push (and uploads it first), then runs the tests and lint.

## How it works

| File | Role |
|---|---|
| `Library`, `Track` | Reads all audio from MediaStore on a background thread and groups it into songs, albums, artists and folders. Refreshes itself when new files are indexed. |
| `FolderTree` | Builds the folder tree from file paths, collapses empty single-child chains, and names volumes ("Internal storage", "SD card", "USB drive"). |
| `PlaybackService`, `PlayQueue` | A foreground service around `MediaPlayer`, with audio focus, headphone-unplug pause, a media session for lock screen and car controls, and saved queue state. |
| `EffectsController`, `EqPresets` | Attaches the platform Equalizer, BassBoost, Virtualizer and LoudnessEnhancer to the player's audio session. Presets are interpolated onto whatever bands the device offers. |
| `LibraryActivity`, `PlayerActivity`, `QueueActivity`, `EqualizerActivity` | The screens. `NowPlayingView` is shared between the full-screen player and the car panel. |
| `StorageScanner` | Walks readable storage for audio MediaStore does not know and asks the system scanner to index it. |

Only Android framework APIs are used, and the app is written in Java. That is why the APK is so small.

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+), `READ_EXTERNAL_STORAGE` (Android 12 and older) | Read your music files. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing with the screen off. |
| `POST_NOTIFICATIONS` | Show the playback controls in the notification shade (Android 13+). Playback works if you decline. |
| `WAKE_LOCK` | Stop the CPU sleeping in the middle of a song. |
| `MODIFY_AUDIO_SETTINGS` | Standard permission for apps that use audio effects. |

## Status and limits

* **Checked:** 63 automated tests, Android lint, an R8-shrunk release build, and renders of every screen.
* **Not yet run on real hardware or an emulator.** Actual sound output, the hardware equalizer effects, notifications and head-unit specifics (USB mounting, key mapping) need hands-on testing. If something misbehaves, please open an issue with the device model and Android version.
* Gapless playback is not implemented; there is a short gap between songs.
* Android Auto and Android Automotive OS are not supported. The app targets Android devices that run the full Android UI, which covers most aftermarket head units.
* Opening a single audio file from a file manager ("Open with") is not supported yet. Songs must be in MediaStore.

Ideas for later: playlists, sleep timer, gapless playback, Android Auto browsing, a light theme.

## Credits

Icons are from [Material Icons](https://fonts.google.com/icons) (Apache License 2.0). Screenshots use generated cover art and fictional song names.
