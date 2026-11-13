# Notes for Claude and other helpers

This is a small Android music player (plain Java, no libraries). The owner is not a developer. They want
working APKs they can download from GitHub, and they want to see the version on GitHub every time the app
changes. Talk to them in short, plain sentences.

## Every time the app changes and a new APK is made

1. Raise the default `appVersion` in `app/build.gradle` (patch for fixes, minor for features) and add an entry
   at the top of `CHANGELOG.md`, in plain sentences.
2. Build and check: `./gradlew -PappVersion=<version> testDebugUnitTest lintRelease assembleRelease` (JDK 21), then
   `aapt2 dump badging` (package and version) and `apksigner verify --print-certs` (signer) on
   `app/build/outputs/apk/release/app-release.apk`.
3. Put the APK in `download/`: `MusicPlayer-<version>.apk`, `MusicPlayer-<version>.apk.sha256` (from `sha256sum`),
   and replace `download/MusicPlayer.apk` with the same file. Publish only a version that works. A version with
   a serious bug stays out of `download/` and is marked "withdrawn" in the README's Versions table.
4. Update `README.md`: the version and file in the Download table, the Versions table, What's new, and the size
   and test-count badges. Update `download/README.md`.
5. Commit, push to `main`, then tag `v<version>` and push the tag. Wait for the GitHub build to pass.
6. Send the APK to the owner (SendUserFile) and say which version it is and where it is on GitHub.

## Things to know

- This kind of session cannot create, edit or delete GitHub releases through the API (HTTP 403). Do not try to
  get around that. The owner can publish a release themselves with one click: Actions, then
  "Publish release from the APK in download/", then Run workflow, then the version.
- Never commit signing keys (`*.jks`, `keystore.properties`). Builds without a key are signed with the build
  machine's debug key, which is the same key for every build made on the same machine, so a newer APK installs
  over an older one. Check the signer digest before publishing a new version.
- A test that calls `performItemClick` or `performClick` does not prove that a finger can use the screen. Taps are
  tested with real touch events in `RealTouchTest`. A row of a `ListView` must not contain an explicitly
  focusable child (a button) unless the row sets `android:descendantFocusability="blocksDescendants"`: on
  Android 8 and newer the list then ignores taps on the row (this broke every tap in versions 1.2.0 and 1.3.0).
- Everything outside the app's own code (screenshots, the live preview page) is made in scratch projects and is not
  part of this repository, except the pictures in `docs/screenshots/`.
- The owner added their own signing key to GitHub (four `RELEASE_*` secrets) on 2026-11-13. Versions from 1.7.1 are
  built and signed on GitHub by the "Publish release" workflow (owner runs `tools/Publish-Signed-Update.bat`).
  The APKs in `download/` up to 1.7.0 are signed with the sandbox debug key and do not install over a 1.7.1 or newer
  build: say so in the README. Do not put a sandbox-built APK in `download/` as the newest version any more.
