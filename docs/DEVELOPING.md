# Developing Music Player

This page is for whoever builds, tests and publishes the app. The user guide is the [README](../README.md).

* [Releasing a new version](#releasing-a-new-version)
* [Build from source](#build-from-source)
* [Signing and updates](#signing-and-updates)
* [How it works](#how-it-works)

## Releasing a new version

Where people get the app, and where the version shows:

| Place | What it holds |
|---|---|
| [`download/`](../download) | The APK files. `MusicPlayer.apk` is always the newest one: the Download button in the README points at it. `MusicPlayer-<version>.apk` and its `.sha256` are kept for every version that is published. |
| [README](../README.md) | The Download button, the version badges and the Versions table. |
| Git tag `v<version>` | The version on GitHub's Tags page and in the version badge. |
| [CHANGELOG.md](../CHANGELOG.md) | What changed in each version, in plain sentences. |

**Each time the app changes and a new APK is published:**

1. Raise the default `appVersion` in `app/build.gradle` and add an entry at the top of `CHANGELOG.md`.
2. Build and check it: `./gradlew testDebugUnitTest lintRelease assembleRelease`, then look at the APK with `aapt2 dump badging` (package, version) and `apksigner verify --print-certs` (signer).
3. Copy the APK to `download/MusicPlayer-<version>.apk`, write `download/MusicPlayer-<version>.apk.sha256` (`sha256sum`), and replace `download/MusicPlayer.apk` with the same file.
4. Update the version badge, the download table and the Versions table in the README.
5. Commit, push, then tag: `git tag v<version> && git push origin v<version>`.

Only versions that work are published. A version with a serious bug is left out of `download/` and marked in the README's Versions table.

### The GitHub Releases page (one click, optional)

GitHub's **Releases** page, and the app's own **Settings → Updates**, look at *GitHub releases*, not at the `download/` folder. A release is not made automatically. When you want one, run the workflow **Actions → Publish release from the APK in download/ → Run workflow** and type the version (for example `1.3.1`). It takes the APK that is already in `download/`, checks that it is that version of this app and that its checksum is right, and creates the release `v<version>` with the APK, its checksum, a copy named `MusicPlayer.apk` and the version's notes from the changelog. It builds and signs nothing, so it needs no secrets.

After that, `https://github.com/mbnetworku/MusicPlayer/releases/latest/download/MusicPlayer.apk` is a second direct download link, always to the newest release, and installed apps see the update in Settings.

If you have your own signing key in repository secrets, use **Publish release** instead: it builds the app on GitHub, signs it with your key and creates the release itself (see [Signing and updates](#signing-and-updates)).

## Build from source

You need JDK 17 or newer and the Android SDK with platform 35 (Android Studio installs both). The Robolectric tests below need JDK 21 or newer; Android Studio's bundled JDK is fine.

```bash
./gradlew assembleRelease        # macOS / Linux
```

```bat
gradlew.bat assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. If you build without Android Studio, create `local.properties` containing `sdk.dir=/path/to/Android/sdk`.

The version comes from one place. `app/build.gradle` has a default, and `-PappVersion=1.3.0` overrides it (the "Publish release" workflow does this). `versionCode` is derived from it: `major × 10000 + minor × 100 + patch`.

### Tests

```bash
./gradlew testDebugUnitTest lintRelease
```

* Plain JVM tests for the play queue (shuffle, repeat, editing), the folder tree and volume naming, equalizer presets, text formatting and version comparison.
* Robolectric tests that open every screen against a fake MediaStore, play through a fake `MediaPlayer`, and check each look (dark, light, and glass over each kind of background) including how the glass backdrop keeps text readable on any cover. They also cover Favorites (adding from the menu and the heart, the tab, the order, saving, deleting), the moving bars (their motion, when they run and when they stop, the bars shaped like a spectrum, the car layout), the sound analysis with made-up sound, when the app listens to the sound, the permission question and the Settings switch, and they go through the "more" menu and the delete confirmation for each Android version's way of deleting, the glass pop-ups and the permission questions, choosing a picture or the wallpaper for the glass, the widget and its style screen, and what the equalizer screen says about the device equalizer (with a pretend device equalizer, since a test runtime has none). The updater tests exercise it against a local test server (finding a release, being rate-limited or offline, downloading, a wrong checksum, a file the verifier rejects, refusing plain HTTP, cancelling, remembering a release while offline, the once-a-day limit, the install permission), the file checks (another app, an old version, a different signing key), and every state of the update card.
* Robolectric downloads an Android SDK jar from Maven Central on first run.

The GitHub Actions workflow in `.github/workflows/build.yml` builds the APK on every push (and uploads it first), then runs the tests and lint.

## Signing and updates

Android installs an update only when it is signed with the same key as the app that is already installed. So every release has to be signed with one stable key, and that key has to be yours and kept safe.

**Without a key** (a plain `./gradlew assembleRelease`, or the **Build APK** workflow) the app is signed with a debug key that differs from machine to machine. That is fine for trying the app. Such builds cannot update each other, and the app tells you so instead of failing silently ("signed with a different key").

**With your own key**, create it once:

```bash
keytool -genkeypair -v -keystore release.jks -alias music-player -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass YOUR_PASSWORD -keypass YOUR_PASSWORD -dname "CN=Your Name"
```

Use the same password for the store and the key: the default PKCS #12 key store format requires it. Keep `release.jks` and the password somewhere safe, such as a password manager. If you lose them, every user has to uninstall and reinstall. If someone else gets them, they can make an app that Android accepts as an update of yours.

* **Local builds:** create `keystore.properties` next to the top-level `build.gradle` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. If that file exists the build uses it. Both it and `*.jks` files are git-ignored; never commit them.
* **Release workflow:** add four repository secrets (Settings → Secrets and variables → Actions): `RELEASE_KEYSTORE_BASE64` (the output of `base64 -w0 release.jks`; on Windows run it in Git Bash or WSL), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. The workflow signs with them and refuses to publish without them.

**Changing the key later is a one-way door.** Copies installed from a build signed with one key cannot be updated to a build signed with another. The only fix is to uninstall and reinstall, which resets settings. Decide on the key before you share the app.

## How it works

| File | Role |
|---|---|
| `Library`, `Track` | Reads all audio from MediaStore on a background thread and groups it into songs, albums, artists and folders. Refreshes itself when new files are indexed. |
| `FolderTree` | Builds the folder tree from file paths, collapses empty single-child chains, and names volumes ("Internal storage", "SD card", "USB drive"). |
| `PlaybackService`, `PlayQueue` | A foreground service around `MediaPlayer`, with audio focus, headphone-unplug pause, a media session for lock screen and car controls, and saved queue state. |
| `EffectsController`, `EqPresets` | Attaches the platform Equalizer, BassBoost, Virtualizer and LoudnessEnhancer to the player's audio session. Presets are interpolated onto whatever bands the device offers. |
| `LibraryActivity`, `PlayerActivity`, `QueueActivity`, `EqualizerActivity` | The screens. `NowPlayingView` is shared between the full-screen player and the car panel. |
| `StorageScanner` | Walks readable storage for audio MediaStore does not know and asks the system scanner to index it. |
| `Appearance` | Light, dark and follow-device: wraps each screen's context with the chosen night mode and keeps the system bars readable. Screens rebuild themselves when the look changes. |
| `Glass`, `GlassDrawable`, `BackdropDrawable` | The liquid-glass look: tag-driven decoration of the layouts, translucent panels with a highlight and rim, and the blurred cover-art window background. |
| `SettingsActivity`, `Prefs` | Settings screen and saved choices. |
| `BackgroundStore`, `BackdropDrawable` | The picture behind the glass: copies, scales and loads your picture or the wallpaper, and draws plain, cover, or picture backgrounds cropped and kept readable. |
| `GlassDialog`, `Permissions` | The liquid-glass alert and action sheet, and every permission question, decided per Android version. |
| `MediaDeleter` | Deleting songs: the system request on Android 11 and newer, direct deletion before. |
| `DeviceEq` | The device equalizer (output mix) as far as Android allows, behind a small interface so it can be tested. |
| `Favorites` | The hearted songs: remembered by file path, newest first, saved in the preferences, with listeners that refresh the screens. |
| `EqBarsView`, `SoundBars`, `SpectrumAnalyzer`, `VisualizerSource` | The moving bars: one shared animation clock, the real sound through Android's Visualizer (listening only while bars are visible and playing), the arithmetic that turns an FFT into bars, and the bars' own motion when the sound is not available. |
| `PlayerWidget`, `WidgetConfigActivity` | The home-screen widget and the screen where its style is chosen. |
| `Updater`, `Release`, `ReleaseApi` | The update state machine (check, download, verify, install), GitHub release parsing, and the HTTPS download with a checksum. |
| `ApkVerifier`, `UpdateInstaller`, `InstallReceiver` | Checks the downloaded file (package, version, signing key) and hands it to Android's `PackageInstaller`. |

Only Android framework APIs are used, and the app is written in Java. That is why the APK is so small.
