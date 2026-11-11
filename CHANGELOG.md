# Changelog

The newest version is first. The release workflow publishes a version's entry as that
release's notes, and the app shows those notes under Settings > Updates. Keep each entry
short and in plain sentences: the app shows it as plain text.

## [1.1.0] - 2026-11-11

- Light and dark themes. "Follow device" is the default: the app uses whatever your phone or head unit is set to, and changes by itself when that changes. You can also force Light or Dark.
- Liquid glass style. The background becomes a blurred, colourful copy of the cover art that is playing, and the panels become frosted glass. It works in light and dark, and stays readable with any cover.
- A Settings screen (menu, then Settings) with Appearance, Library and Updates.
- Updates from inside the app. Settings > Updates checks GitHub for a newer release, downloads it, checks its SHA-256 and signature, and hands it to Android to install. The app also looks once a day and shows a banner when a new version is out; you can turn that off.
- Good to know: the app now has the internet permission. It is used only to look for updates and to download them. Your music never uses the network.
- Good to know: Android only installs an update over an app signed with the same key. Version 1.0.0 was signed with a throw-away test key, so uninstall it once before installing 1.1.0. From 1.1.0 on, updates install over the top and keep your settings.

## [1.0.0] - 2026-11-11

- First version: every folder and song, the Songs, Folders, Albums and Artists tabs, search, the queue, an equalizer with presets, bass boost, surround and volume boost, and a landscape layout for car head units.
