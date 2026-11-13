# Changelog

The newest version is first. The release workflow publishes a version's entry as that
release's notes, and the app shows those notes under Settings > Updates. Keep each entry
short and in plain sentences: the app shows it as plain text.

## [1.22.0] - 2026-11-13

- New: a round glass search button next to the mini player at the bottom, like in Apple Music. It opens the same search as the search button at the top.

## [1.21.0] - 2026-11-13

- Changed: closer to the iPhone Music look. The back button on the player, the queue, Settings and the equalizer is a round glass button. Shuffle and repeat are white instead of red, dim when off and bright when on.

## [1.20.0] - 2026-11-13

- Changed: "Vibrate with the music" now follows the music itself. The phone buzzes on the strong low notes of the song, the drums and the bass. Heavy music gives a strong buzz, and quiet parts give none. The steady pulse is gone.
- How it works: when a song starts, the app reads the song file once in the background and measures its bass. It does not use the microphone and records nothing. A song the phone cannot read gives no buzz. The option is off until you turn it on in Settings.

## [1.19.0] - 2026-11-13

- New: the ambient background is alive. When "Ambient background" is on, the colours from the song drift slowly, like in Apple Music. They only move while the screen is in use, and not at all if the phone's animations are switched off.

## [1.18.0] - 2026-11-13

- New: an "Ambient background" switch in Settings. When it is on, the colours of the song that is playing fill the background, like in Apple Music. When it is off, the background is pure black or pure white. It is off by default.
- Fixed: after the Settings screen was rebuilt, for example after you turned the screen, one switch could copy the position of another and flip a setting such as Liquid Glass by itself. Each switch now keeps its own setting.

## [1.17.0] - 2026-11-13

- Changed: the background is pure black in dark mode and pure white in light mode, with no colour. The colourful background from the song cover is no longer the default. You can still choose it, a picture or your wallpaper in Settings.

## [1.16.0] - 2026-11-13

- Changed: closer to the iPhone Music look. The song list and the Now Playing controls no longer sit inside big glass boxes. The rows and the controls are now directly on the colour background, like on an iPhone.

## [1.15.0] - 2026-11-13

- Changed: the Songs list follows the iPhone Music look. Play and Shuffle sit at the top, and a big letter (A, B, C…) starts each group of songs when the list is sorted by title. The quick letter index on the right still works.

## [1.14.0] - 2026-11-13

- New: swipe the cover left or right to change song, like on an iPhone. The cover follows your finger and the next one slides in. Tapping the cover no longer skips songs. You can turn swiping off in Settings under "Swipe the cover to change song".
- New: drag the Now Playing screen down to close it, like on an iPhone. The sliders keep their own drag.
- Changed: the volume slider only shows on screens at least 600dp tall, so the cover never gets squeezed on small phones.

## [1.13.0] - 2026-11-13

- Changed: the Songs screen follows the iPhone Music look. Search, the equalizer and the menu now sit together in one glass capsule at the top, the menu dots are horizontal, and the song pictures are a little larger. Nothing was removed and the equalizer button is still there.

## [1.12.0] - 2026-11-13

- New: a volume slider on the Now Playing screen, with small speaker icons. It follows the phone's music volume, also when you press the volume buttons. It is hidden on car screens, which have their own volume.
- Changed: new play, pause, previous and next icons in the iPhone style. The layout stays the same, and the equalizer button is still there.
- Changed: the glass panels are clearer, so more of the colour behind them shows through.

## [1.11.0] - 2026-11-13

- Changed: a more complete Liquid Glass look. Panels are clearer glass with bright edges at two opposite corners and a soft glow just inside, like a thick pane of glass.
- Changed: the background now takes its colours from the song's cover by default, and shows a soft coloured glow when nothing is playing. You can still choose white or black, a picture or your wallpaper in Settings.

## [1.10.0] - 2026-11-13

- Changed: the look is now Liquid Glass by default, like the newest iPhone look. You can still switch it off in Settings.
- Changed: switches are green like on an iPhone, and the descriptions in Settings are shorter.
- Fixed: on some screens the moving bars were wider than the cover on the Now Playing screen. They now match the cover exactly.
- New: updates are published automatically. When the version changes on GitHub, GitHub builds, signs and publishes it by itself.

## [1.9.0] - 2026-11-13

- Changed: Settings is fully redesigned. Small coloured icons, a slim switch for Device, Light or Dark, small picture tiles for the background and the equalizer design, and a new "Playing" group for vibration and tap-the-cover. Everything is tighter and smoother.
- New: an update pop-up. When a new version is out, a small pop-up appears while you use the app, once for each version. "Update now" downloads it and opens Settings to install. "Later" closes it, and the Update banner stays.

## [1.8.0] - 2026-11-13

- Changed: the Settings screen has a tidier design. Each option has a small coloured icon, the rows are tighter and the text is a little smaller, so more fits on the screen.

## [1.7.2] - 2026-11-13

- Changed: the app no longer shows its own pop-up before Android asks for permission to read your music. Android's own question still appears once, because Android requires it.
- Changed: the app no longer asks for the notification permission at the start.
- Changed: pop-ups are smaller and tidier, with a small icon, a shorter message and shorter buttons.

## [1.7.1] - 2026-11-13

- This is the first version signed with your own private key, built by GitHub. Nothing in the app changed. Because the key is new, Android will not install it over an older version: uninstall the old app once, then install this one. From now on every update installs over it.

## [1.7.0] - 2026-11-13

- Removed: the microphone permission. The app no longer asks for it and no longer has "Bars follow the music". The moving bars are an animation. The app reads no audio.
- New: Vibration. In Settings, "Vibration" gives a short buzz when you tap a song, a button or a tab. It is on by default and follows Android's own touch feedback setting.
- New: "Vibrate while music plays" gives a soft pulse about twice a second while a song plays. It is off until you turn it on. It is a steady pulse, not matched to the beat of the song.

## [1.6.0] - 2026-11-12

- New: tap the cover to skip. Turn it on in Settings under "Tap the cover to skip". On the Now Playing picture, tap the left side for the previous song, the right side for the next song, and the middle to play or pause. It is off until you turn it on.

## [1.5.0] - 2026-11-12

- New: you can sort your songs. Tap the menu (three dots) and choose "Sort songs". You can order by title, artist, album, recently added or longest first, and reverse the order. The choice is kept, and it is used in Songs, in search and inside folders.

## [1.4.0] - 2026-11-12

- New: you can choose how the moving equalizer looks. Open Settings and pick Bars, Dots, Mirror or Wave under "Equalizer design". It changes the bars on the player, the lists and the equalizer screen.
- Checked again: tapping a song, a folder, a Play all button and every tab works in portrait, landscape and on a wide car screen.

## [1.3.1] - 2026-11-12

- Fixed: tapping a song, a folder, an album or an artist did nothing on Android 8 and newer, so no song could be started by touch. This began in version 1.2.0. Taps now work on the whole row, in portrait, in landscape and on the car screen. If you installed 1.2.0 or 1.3.0, please update.
- Fixed: a song stayed silent when Android refused the sound to the app, as some car stereos do. It now plays. If a phone call has the sound, the song waits and starts when the call ends.

## [1.3.0] - 2026-11-12

- Favorites. Tap the heart on the Now Playing screen, or choose Add to Favorites in the more (...) menu of a song. For an album, an artist or a folder the same menu adds all its songs at once. The new Favorites tab lists them, newest first, with Play and Shuffle, and a small heart marks a favorite song in every list. A song you delete is taken off the list.
- Moving equalizer bars. Big, thin bars rise over the cover on the Now Playing screen, tall for the bass and short for the treble. Small ones show on the song that is playing in every list, in the mini player, and as the Equalizer button at the top, which opens the equalizer. On the Equalizer screen a row of bars follows your sliders.
- The bars can follow the real sound. The first time music plays on the Now Playing screen, a glass pop-up asks if they may. Android calls this the microphone permission, but the app only reads the sound of its own music and never uses the microphone. If you say Not allowed, the bars still move on their own. Settings has a switch: Bars follow the music.
- The bars stand still if you turned animations off in Android, and they stop whenever the screen is not showing, so they do not use battery in the background.
- Good to know: this version adds the microphone permission, only for the bars and asked once. Many head units do not let apps read the sound; there the bars keep their own motion. Favorites are remembered by file, so they stay even if the library is scanned again.

## [1.2.0] - 2026-11-12

- A new look, like Apple Music: white pages (black in dark mode), a red accent, large titles, a tab bar at the bottom, round artwork and a clean Now Playing screen. Every earlier feature is still there.
- Liquid glass is now neutral by default. You choose what is behind the glass: a plain white or black page, the cover art, your own picture, or your device wallpaper. Where Android does not let apps read the wallpaper picture, its colours are used.
- Every permission question now starts with a liquid-glass pop-up that says why it is needed, with Allow and Not allowed.
- Delete songs for good: from a song's more button, or for a whole album, artist or folder. You confirm first, and the files are gone at once. On Android 11 and newer Android shows its own confirmation as well.
- The equalizer screen shows what the device's own equalizer says (on or off, preset). You can copy those settings into the player, or let the player control the whole device's equalizer where Android allows it.
- A home-screen widget with the cover, the song and previous, play and next. Choose Follow my device, Light or Dark for each widget.
- A new app icon.
- Good to know: Android 10 and older ask for permission to delete files once. Android 13 and newer do not let apps read the wallpaper picture, so the wallpaper's colours are used.

## [1.1.0] - 2026-11-11

- Light and dark themes. "Follow device" is the default: the app uses whatever your phone or head unit is set to, and changes by itself when that changes. You can also force Light or Dark.
- Liquid glass style. The background becomes a blurred, colourful copy of the cover art that is playing, and the panels become frosted glass. It works in light and dark, and stays readable with any cover.
- A Settings screen (menu, then Settings) with Appearance, Library and Updates.
- Updates from inside the app. Settings > Updates checks GitHub for a newer release, downloads it, checks its SHA-256 and signature, and hands it to Android to install. The app also looks once a day and shows a banner when a new version is out; you can turn that off.
- Good to know: the app now has the internet permission. It is used only to look for updates and to download them. Your music never uses the network.
- Good to know: Android only installs an update over an app signed with the same key. Version 1.0.0 was signed with a throw-away test key, so uninstall it once before installing 1.1.0. From 1.1.0 on, updates install over the top and keep your settings.

## [1.0.0] - 2026-11-11

- First version: every folder and song, the Songs, Folders, Albums and Artists tabs, search, the queue, an equalizer with presets, bass boost, surround and volume boost, and a landscape layout for car head units.
