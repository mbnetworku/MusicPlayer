# Music Player

A small, fast music player for Android 8.0 and newer, with a clean look inspired by Apple Music: white pages (black in dark mode), a red accent, large titles and a tab bar at the bottom. It is made for phones and for **car head units** (Android car stereos): it finds every song in every folder, including USB drives and SD cards, it has a real equalizer that can also read and control the device's own, and it can **update itself** from this repository's releases.

* **Tiny:** the installable APK is about 170 KB. No libraries, no ads, no tracking.
* **Private:** your music never touches the network. The only thing the app ever asks the internet is "is there a newer release on GitHub?", at most once a day, and you can turn even that off.
* **Every folder, every song:** Songs, Folders, Albums and Artists tabs, search, and a folder browser that shows each storage volume.
* **Light, dark or automatic:** by default the app **follows your device** and switches when it does. You can also force Light or Dark.
* **Liquid glass:** an optional look with frosted glass panels. Behind them you choose a plain white or black page, the blurred cover art, **your own picture**, or **your device wallpaper**.
* **Glass pop-ups:** every permission question starts with a liquid-glass message that says why, with **Allow** and **Not allowed**.
* **Delete songs for good:** from a song's more button, or a whole album, artist or folder. You confirm first; the files are then gone at once.
* **Favorites:** a heart on the Now Playing screen and in every song's menu, a **Favorites** tab with Play and Shuffle, and a small heart on favorite songs in every list.
* **Moving equalizer bars:** big, thin bars over the cover on Now Playing, and small ones on the playing song in every list, in the mini player and as the Equalizer button. They rise and fall with the music: if you allow it they follow the **real sound**, otherwise they move on their own.
* **Equalizer:** presets, a slider per band, bass boost, surround and volume boost. The screen also shows what the **device's own equalizer** says, and can copy it or control it where Android allows.
* **Widget and icon:** a home-screen widget with cover and playback buttons (Follow my device, Light or Dark), and a new app icon.
* **Updates in Settings:** check for, download, verify and install a new version without leaving the app.
* **Built for the car:** in landscape on a wide screen the player and the library share one screen, with large touch targets. Steering-wheel, Bluetooth and headset media keys are handled, and can start your last queue when the app is closed.

## Screenshots

These are renders of the app's real layouts and real code with sample (fictional) songs and generated cover art. They are not photos of a device.

The looks. **Follow my device** picks Dark or Light for you. **Liquid glass** can be switched on in either, over a plain page, the cover art, or your own picture:

<img src="docs/screenshots/looks.png" alt="Songs and Now playing in six looks: dark, light, liquid glass on dark and on light, glass over cover art, and glass over a picture">

Favorites: the heart on Now Playing, **Add to Favorites** in every song's menu, hearts in the lists, and the Favorites tab:

<img src="docs/screenshots/favorites.png" alt="Songs with hearts, the more menu with Add to Favorites, Now Playing with the heart, and the Favorites tab, on dark and light">

The moving equalizer bars: big thin bars over the cover on Now Playing while it plays, settled while it is paused, bars that follow the sliders on the Equalizer screen, and small bars on the song that is playing. The animation is the real Now Playing screen, drawn at 16 moments in a row:

<img src="docs/screenshots/bars.png" alt="Now Playing with big bars playing and paused, the Equalizer screen with bars following the sliders, and the song list with the moving bars, on dark and light">

<img src="docs/screenshots/equalizer-motion.gif" width="270" alt="The Now Playing screen with the equalizer bars moving over the cover">

Pop-ups and menus are liquid glass too, in every look. The music permission, the question before the bars may follow the sound, the delete confirmation and the more menu:

<img src="docs/screenshots/popups.png" alt="The music permission question, the question about the bars following the music, the delete confirmation and the more menu, in glass, on dark and light">

Settings, with the update card in four of its states:

<img src="docs/screenshots/settings.png" alt="The Settings screen, and its Updates card showing an update available, a download in progress, the one-time install permission, and up to date">

The home-screen widget in its three styles, and the app icon:

<img src="docs/screenshots/widget.png" alt="The widget with the cover, title and buttons in follow-device, light and dark styles, and the app icon as a square, a circle and as a themed icon">

Phone and car head unit (1280 × 720, landscape):

<img src="docs/screenshots/overview.png" alt="Songs, Now playing, Favorites and Equalizer on a phone; the player with the library, and the equalizer, on a car head unit">

Single screens: [Songs](docs/screenshots/phone-songs.png) ([light](docs/screenshots/phone-songs-light.png)), [Favorites](docs/screenshots/phone-favorites.png) ([light](docs/screenshots/phone-favorites-light.png)), [Folders](docs/screenshots/phone-folders.png), [Albums](docs/screenshots/phone-albums.png), [Now playing](docs/screenshots/phone-now-playing.png) ([light](docs/screenshots/phone-now-playing-light.png)), [Equalizer](docs/screenshots/phone-equalizer.png), [Up next](docs/screenshots/phone-queue.png), [Settings](docs/screenshots/phone-settings.png), [Car library](docs/screenshots/car-library.png) ([in glass](docs/screenshots/car-library-glass.png)), [Car equalizer](docs/screenshots/car-equalizer.png).

## Install

1. Get the APK:
   * **From a release:** open this repository's **Releases** page and download `MusicPlayer-<version>.apk` (it exists once a release has been published, see [Publishing an update](#publishing-an-update)), or
   * **From a build:** open the **Actions** tab, pick the latest **Build APK** run and download the `MusicPlayer-release-apk` artifact (a zip containing `app-release.apk`), or
   * **Build it yourself:** see [Build from source](#build-from-source).
2. Copy the APK to the device. On a head unit, use a USB stick and the unit's file manager.
3. Open the file and allow **Install unknown apps** for the app you opened it with when Android asks.
4. Or, from a computer with `adb`: `adb install -r app-release.apk`

On first start the app asks for permission to read music (and, on Android 13+, to show the playback notification). The first time music plays on the Now Playing screen it also asks, once, whether the moving bars may follow the real sound (see [Moving equalizer bars](#moving-equalizer-bars)); the app works the same if you say no.

**Coming from version 1.0.0?** Uninstall it first. It was signed with a throw-away key, and Android never updates an app across keys. Once releases are signed with a stable key (see [Signing and updates](#signing-and-updates)), updates install over the top and keep your settings.

## Looks

Open the menu (⋮) → **Settings** → **Appearance**.

| Option | What it does |
|---|---|
| **Follow my device** (default) | Uses your phone's or head unit's own dark or light setting, and changes by itself when that changes (at sunset, or when you flip the quick setting). Settings shows which mode the device is using right now. |
| **Light** / **Dark** | Always light or always dark, whatever the device says. |
| **Liquid glass** (switch) | Rounded, frosted glass panels, a floating glass tab bar and mini player. Works with light, dark and "Follow my device". |

The flat look is the Apple Music-inspired one: white pages in light mode and true black in dark mode, a red accent, large titles, artwork rows with a more (⋯) button, a mini player above a tab bar, and Settings in grouped cards.

### What is behind the glass

Under **Liquid glass background** in Settings:

| Background | What it shows |
|---|---|
| **White or black** (default) | A clean page that follows your theme. |
| **Cover art** | A soft blur of the song that is playing, fading from one song to the next. |
| **My picture** | A picture you pick from your gallery. It is copied into the app, scaled down, so the app never needs access to your gallery again. |
| **Device wallpaper** | Your wallpaper. Android lets an app read the wallpaper *picture* only with some permissions (up to Android 12 the storage permission, none at all on the newest versions). Where it does not, the wallpaper's three main **colours**, which Android always shares, are used as a soft gradient, and Settings says so. |

Pictures and covers are softened and kept inside a brightness range, so text stays readable on top of any of them: bright ones are darkened at night and dark ones lightened by day.

How it is built, in case you want to change it:

* Colours are Android resources. The dark palette is the default in `values/`, and the light palette is in `values-notnight/`. "Follow my device" simply leaves Android's own dark/light choice alone, so it works on Android 8 as well, where apps have no dark-mode switch of their own. Light and Dark override that choice for this app only. The red accent is one colour, `accent`.
* The glass look is plain graphics code, no blur library. Layouts opt in with a tag such as `android:tag="glass:card"`; `Glass` reads the tags and gives those views glass backgrounds, so there is one set of layouts for every look. Over a plain page the panels get a fill of their own so they can be seen; over a cover or picture they are mostly see-through. Pop-ups blur what is behind them on Android 12 and newer.

## Settings

| Section | Option | What it does |
|---|---|---|
| Appearance | Theme | Follow my device, Light or Dark. |
| Appearance | Liquid glass | The glass look above. |
| Appearance | Bars follow the music | Let the moving bars follow the real sound. Needs the permission Android calls microphone, which the app only uses to read its own music. See [Moving equalizer bars](#moving-equalizer-bars). |
| Liquid glass background | White or black, Cover art, My picture, Device wallpaper | What shows behind the glass panels. |
| Library | Show all audio files | Include ringtones, voice notes and other short sounds. |
| Library | Scan storage for music | Index new files on internal storage, SD cards and USB drives. |
| Updates | Version, **Check for updates** | See [Updates](#updates). |
| Updates | Check automatically | Look for a new version once a day when the app opens. On by default. |
| About | Source code and releases | Opens this repository on GitHub. |

## Pop-ups before permissions

Android versions differ in what they need, so the app asks in one place, and always starts with a glass pop-up that says why: **Allow** shows Android's own question, **Not allowed** leaves the feature off and the screen explains what is missing.

| When | What is asked | On which Android |
|---|---|---|
| First start | Read your music: `READ_MEDIA_AUDIO` (13 and newer) or `READ_EXTERNAL_STORAGE` (12 and older). Android 13 and newer also ask to show the playback controls (`POST_NOTIFICATIONS`). | all |
| Deleting a song | Write access to delete files, once. | 10 and older only. Android 11 and newer show their own "allow deleting?" screen for each delete instead. |
| Installing an update | "Install unknown apps" for Music Player, once. | all |
| The first time music plays on Now Playing | "Make the bars follow the music?" The permission Android calls microphone (`RECORD_AUDIO`), used only to read the app's own sound. | all. Asked once; Settings has a switch. |
| Using your wallpaper | Reading the wallpaper picture. If Android says no, its colours are used. | storage permission on 12 and older; not available on 13 and newer |
| After Android stops asking | A pop-up with **Open settings** for this app's page. | all |

## Deleting songs

Tap a song's **⋯** (or long-press anything in a list): **Delete…**. For an album, artist or folder it says **Delete all these songs…**. The same menu is on the Now Playing screen. A glass pop-up asks once ("The files are deleted from this device for good, right away. This cannot be undone."). There is no recycle bin.

* **Android 11 and newer:** apps may not delete other apps' media silently, so after your confirmation Android shows its own "Allow Music Player to delete this audio file?" screen. After you allow it, the files are deleted at once.
* **Android 10 and older:** the app asks for write access once, then deletes directly.
* Deleted songs are removed from the play queue. If the song that was playing is deleted, playback moves on.

## Favorites

Tap the **heart** on the Now Playing screen, or **⋯ → Add to Favorites** on a song. On an album, artist or folder the same menu says **Add all to Favorites**. The **Favorites** tab, the last one in the tab bar, lists them newest first, with **Play all** and **Shuffle**; tapping one plays the favorites in that order. A small heart marks a favorite song in every list.

* A favorite is remembered by its **file path**, not by the library's internal number, so it survives scanning the library again. If the file is gone the song is hidden, and it comes back if the file does.
* A song you delete from the app is taken off the list.
* Favorites are saved on the device only.

## Moving equalizer bars

Bars that rise and fall while music plays and settle when you pause:

* **Big bars over the cover** on the Now Playing screen: many thin white bars, tall for the bass and short for the treble, like a music spectrum.
* **Small bars** on the song that is playing in every list, in the mini player, and as the **Equalizer button** at the top of the library and the player (tap it to open the equalizer).
* **Equalizer screen:** a row of bars at the top whose heights follow the sliders: a boosted band lets its bars reach the top, a cut band keeps them low.

**Following the real sound.** The app reads the sound of its own player through Android's Visualizer effect and turns it into 48 bars from bass to treble (an FFT, scaled against the loudest recent sound so it looks the same at any volume, with bars that fall back gradually). Android only allows this with the permission it calls **microphone** (`RECORD_AUDIO`), although the app never uses the microphone: it listens only to the audio session of its own player, and nothing is recorded or sent anywhere. The app asks **once**, in a glass pop-up, the first time music plays on the Now Playing screen. **Settings → Bars follow the music** switches it on or off later.

Without the permission, or on a device that refuses (many head units do), the bars keep a motion of their own. It looks like a spectrum but is **not** measured from the sound. Android may show its microphone indicator while the bars follow the sound (not verified).

**Battery.** One listener serves every bar on screen. It runs only while a bar is visible and music is playing, and it stops when you pause, hide the screen or leave. With **Remove animations** switched on in Android, the bars stand still.

## Equalizer and the device equalizer

The equalizer works on this player's own audio, so other apps keep their own sound. A row of [moving bars](#moving-equalizer-bars) at the top follows the sliders. At the top of the screen a **Device equalizer** card says what Android's own equalizer is doing: on or off, and which preset is selected.

* **Read from device** copies the device's on/off state and curve into this player's equalizer.
* **Control the whole device** makes the sliders change the equalizer of the whole device instead (every app is affected, and this player's own equalizer steps aside so the sound is not shaped twice). It stays on only while Music Player runs.

How much of this works depends on the device. Android lets an app reach the device equalizer (the "output mix") only where the manufacturer allows it, and only one app can control it at a time. When it is not allowed, the card says so, and the equalizer applies to this player only. This has not been tried on real devices yet.

## Widget and icon

Long-press the home screen → **Widgets** → **Music Player**. The widget shows the cover, the title and artist, and **previous / play or pause / next**; tapping it opens the app. When you add it, you choose its style: **Follow my device**, **Light** or **Dark**. The widget follows playback while Music Player runs, and shows the last song after a restart. The app icon is a red glass tile with a white note, and it also has a single-colour version for Android 13's themed icons.

## Updates

**Settings → Updates** shows the installed version and one button whose label follows what is going on: **Check for updates**, **Download and install**, **Cancel**, **Install now** or **Try again**.

1. **Check.** The app asks GitHub for this repository's latest release. If it is newer than the installed version you see its version, its size and its "What's new" notes.
2. **Download.** The file comes straight from the release over HTTPS (a link that is not HTTPS is refused), with a progress bar and a Cancel button.
3. **Verify.** Before Android sees the file, the app checks its SHA-256 checksum against the one published with the release (releases made by the workflow always have one), that the file is this app (`com.musicplayer.lite`), that its version is newer than the installed one, and that it is signed with the **same key** as the installed copy. If anything is off, the file is deleted and the screen says what was wrong in plain words. Android then does its own checks again when it installs.
4. **Install.** Android's own installer asks you to confirm. Your queue and settings are kept. The first time, Android also asks you to allow installs from Music Player (Settings → Apps → Special access → Install unknown apps); the app takes you there and carries on when you come back.

A **banner** on the library screen shows when a newer version is known: **Update** opens Settings, and ✕ hides that version.

**Automatic checks** happen at most once a day, when the app is opened, and only while **Check automatically** is on (the default). A check contacts GitHub and nothing else. It sends no information about you or your music, only an ordinary web request that names the app and its version (and, as with any web request, GitHub can see your IP address). When a check fails, for example offline, the app stays quiet and tries again later; a check you start yourself shows the problem.

**Permissions.** Updates are the reason the app has the `INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions. Playing music does not use the network.

## Using it

| You want to | Do this |
|---|---|
| Play a song | Tap it. The list it came from (all songs, a folder, an album) becomes the queue. |
| Play a whole folder, album or artist | Open it and tap **Play all** or **Shuffle**. |
| Add to the queue, delete, see details | Tap the **⋯** at the end of a song, album, artist or folder (or long-press it): **Play next**, **Add to queue**, **Song info**, **Delete…**. |
| See or edit the queue | Open the player, then the queue icon. Tap to jump, long-press to remove. |
| Search | Tap the magnifier. It searches title, artist, album and file name. |
| Use the equalizer | Tap the equalizer icon. Picking a preset or moving a slider turns it on. |
| Change the look, the glass background, or check for updates | Menu (⋮) → **Settings**. |
| Find music that is not listed | Menu (⋮) → **Scan storage for music** (also in Settings → Library). |
| See ringtones and voice notes too | Menu (⋮) → **Settings** → **Show all audio files**. |

The app remembers your queue and position and carries on where you left off.

### On a car head unit

* Install the APK from a USB stick, then put your music on a USB drive or SD card. If it does not appear after plugging it in, use **Scan storage for music**.
* On screens at least 640 dp wide and 400 dp tall in landscape (most head units), the player sits on the left and the library on the right. Rows are 72 dp tall and the play button is 96 dp.
* Steering-wheel keys, Bluetooth and headset buttons send standard media keys, which the app handles. When the app is not running they should start your last queue (not yet verified on a real head unit).
* The equalizer uses the audio effects built into Android. Some head units process sound in their own DSP instead; the screen says so when Android's equalizer is not available, and offers a shortcut to the system sound settings when one exists.
* Updates need the head unit to be online and to let apps install packages. Many head units block installs from unknown sources or have no internet; there, install new versions from a USB stick as before.

## Build from source

You need JDK 17 or newer and the Android SDK with platform 35 (Android Studio installs both). The Robolectric tests below need JDK 21 or newer; Android Studio's bundled JDK is fine.

```bash
./gradlew assembleRelease        # macOS / Linux
```

```bat
gradlew.bat assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. If you build without Android Studio, create `local.properties` containing `sdk.dir=/path/to/Android/sdk`.

The version comes from one place. `app/build.gradle` has a default, and `-PappVersion=1.3.0` overrides it (the release workflow does this). `versionCode` is derived from it: `major × 10000 + minor × 100 + patch`.

### Tests

```bash
./gradlew testDebugUnitTest lintRelease
```

* Plain JVM tests for the play queue (shuffle, repeat, editing), the folder tree and volume naming, equalizer presets, text formatting and version comparison.
* Robolectric tests that open every screen against a fake MediaStore, play through a fake `MediaPlayer`, and check each look (dark, light, and glass over each kind of background) including how the glass backdrop keeps text readable on any cover. They also cover Favorites (adding from the menu and the heart, the tab, the order, saving, deleting), the moving bars (their motion, when they run and when they stop, the bars shaped like a spectrum, the car layout), the sound analysis with made-up sound, when the app listens to the sound, the permission question and the Settings switch, and they go through the "more" menu and the delete confirmation for each Android version's way of deleting, the glass pop-ups and the permission questions, choosing a picture or the wallpaper for the glass, the widget and its style screen, and what the equalizer screen says about the device equalizer (with a pretend device equalizer, since a test runtime has none). The updater tests exercise it against a local test server (finding a release, being rate-limited or offline, downloading, a wrong checksum, a file the verifier rejects, refusing plain HTTP, cancelling, remembering a release while offline, the once-a-day limit, the install permission), the file checks (another app, an old version, a different signing key), and every state of the update card.
* Robolectric downloads an Android SDK jar from Maven Central on first run.

The GitHub Actions workflow in `.github/workflows/build.yml` builds the APK on every push (and uploads it first), then runs the tests and lint.

## Publishing an update

For whoever owns the repository. An installed app offers an update when this repository's **latest GitHub release** has a higher version than the one installed.

**Once, before the first release:** put your signing key into four repository secrets, as described in [Signing and updates](#signing-and-updates). The workflow stops with a clear message if they are missing, because an update signed with a throw-away key could never be installed over the previous version.

**For each release:**

1. Add an entry for the new version to [CHANGELOG.md](CHANGELOG.md), for example `## [1.4.0] - 2027-01-15`, and push it to `main`. Its text becomes the release notes the app shows under "What's new", so keep it short and in plain sentences.
2. On GitHub open **Actions → Publish release → Run workflow**, type the version (`1.4.0`) and run it.

The workflow checks that the version is valid and newer, runs the tests and lint, builds the APK with that version, signs it, checks the result with `apksigner` and `aapt2`, and creates the release `v1.4.0` with `MusicPlayer-1.4.0.apk` and `MusicPlayer-1.4.0.apk.sha256` attached. Nothing else is needed.

* It only runs on the default branch, so an update never ships unreviewed work.
* It refuses a version that is not higher than the latest release, because installed apps only move forward. Drafts and prereleases are ignored by the app, which uses GitHub's "latest release".
* Minor and patch numbers go up to 99, because of how `versionCode` is derived.

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

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+), `READ_EXTERNAL_STORAGE` (Android 12 and older) | Read your music files. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep playing with the screen off. |
| `POST_NOTIFICATIONS` | Show the playback controls in the notification shade (Android 13+). Playback works if you decline. |
| `WAKE_LOCK` | Stop the CPU sleeping in the middle of a song. |
| `MODIFY_AUDIO_SETTINGS` | Standard permission for apps that use audio effects. |
| `WRITE_EXTERNAL_STORAGE` (Android 10 and older only) | Delete songs. Android 11 and newer never grant it and ask per delete instead. The app also sets `requestLegacyExternalStorage` so Android 10 lets it delete files directly. |
| `RECORD_AUDIO` | Only for the bars that follow the music: Android's Visualizer needs it, although the app never uses the microphone and records nothing. Asked once in a glass pop-up; the app works without it. |
| `INTERNET` | Only to look for updates and download them from GitHub. |
| `REQUEST_INSTALL_PACKAGES` | Only so Android lets the app hand a downloaded update to the installer. Android still asks you to confirm every install. |

## Status and limits

* **Checked:** 324 automated tests, Android lint, an R8-shrunk release build whose version, signature and manifest were inspected with `apksigner` and `aapt2`, and renders of every screen in all eight looks (dark and light, flat and over each glass background).
* **Not yet run on real hardware or an emulator.** Actual sound output, the hardware equalizer effects, notifications, head-unit specifics (USB mounting, key mapping), how the glass look performs on slow head units, the real Android installer dialog during an update, Android's own delete confirmation on Android 11 and newer, the wallpaper on different versions, and the widget on different launchers all need hands-on testing. If something misbehaves, please open an issue with the device model and Android version.
* **Following the real sound is untested on real devices.** Android's Visualizer behaves differently from device to device and many head units do not support it; the app then keeps the bars' own motion. The sound analysis itself is tested with made-up sound only, and whether Android shows its microphone indicator has not been checked.
* The device equalizer is best effort: many devices do not let an app see or change it, and the card then says so. Controlling it affects every app and lasts only while Music Player runs.
* Reading the wallpaper *picture* is not possible on the newest Android versions; the wallpaper's colours are used instead.
* Updating needs a published GitHub release, signed with a stable key. Until the first one exists, "Check for updates" says no update has been published yet.
* Gapless playback is not implemented; there is a short gap between songs.
* Android Auto and Android Automotive OS are not supported. The app targets Android devices that run the full Android UI, which covers most aftermarket head units.
* Opening a single audio file from a file manager ("Open with") is not supported yet. Songs must be in MediaStore.

Ideas for later: playlists, sleep timer, gapless playback, Android Auto browsing, delta updates, a resizable widget with a progress bar.

## Credits

Icons are from [Material Icons](https://fonts.google.com/icons) (Apache License 2.0). Screenshots use generated cover art and fictional song names.
