# Queue Backpressure and Overload Protection Audit

Project: **Music Player** (`com.musicplayer.lite`), an offline Android music player written in plain Java.
Audited version: **1.25.1** (repository state at commit `808ba5e`, plus this new document).
Audit type: **read-only static review**. No code was changed and no test or load run was started for this audit.

> How to read this document
> * **Confirmed** means the behaviour is visible in the source at the file and function named.
> * **Potential risk** means the code allows it, but whether it happens depends on the device or on use.
> * **Unknown** means the source alone cannot settle it.
> * Nothing here was measured on a real phone, car stereo or large music library. Every statement comes from reading the code.

---

## 1. Executive Summary

This app has **no server, no message broker, no database server and no network input**. Work enters only through
one person using the screen, the Android media system (MediaStore) and, once a day at most, one HTTPS request
to GitHub. So the classic "queue overload" problem (a producer faster than a consumer, without a limit)
does not exist in its usual form. What does exist is a small set of **local background queues and loops**.

| Area | Result |
|---|---|
| Distributed queues, brokers, workers | **None.** Not applicable. |
| Local background queues | **Seven** (library reload, cover art, bass reader, storage scan, update check and download, background picture, home-screen widget). |
| Unbounded buffers | **One** real one: the cover-art task queue (`ArtLoader`), softened by newest-first order and a staleness check. The play queue and the saved queue have no size cap either, but only the user adds to them. |
| Worst severity found | **Low.** No Critical, High or Medium finding. |
| Strongest protections | Reload coalescing (`Library.refresh`), a bounded art cache and two-thread pool (`ArtLoader`), generation counters that drop stale playback callbacks (`PlaybackService`), a bounded skip-on-error streak, a once-a-day and once-an-hour limit on update checks (`Updater`), timeouts and size caps on network reads (`ReleaseApi`). |
| Main gaps | No deadline on starting a song (`prepareAsync`), cancelled bass-reader tasks still open a decoder, whole-queue rewrite on every queue change, per-keystroke search on the main thread, no timeout on the storage scan, and **no operational metrics or logs at all**. |

**Counts:** 13 confirmed findings (8 Low, 5 Informational), 6 potential risks, 7 unknowns.
**Production readiness for its own scope** (a single-user offline app): acceptable, with a short hardening list in section 13.

---

## 2. Project Architecture Overview

| Item | What the source shows |
|---|---|
| Language and framework | Java 17 source level, Android framework APIs only. `app/build.gradle` declares no runtime dependencies. |
| Android versions | `minSdk 26`, `targetSdk 35` (`app/build.gradle`). |
| Entry points | `LibraryActivity` (home), `PlayerActivity`, `QueueActivity`, `EqualizerActivity`, `SettingsActivity`, `WidgetConfigActivity`; `PlaybackService` (foreground media service); `PlayerWidget`, `MediaButtonReceiver`, `InstallReceiver` (declared in `AndroidManifest.xml`). |
| Data stores | Android **MediaStore** (read-only, through `ContentResolver`), **SharedPreferences** (`Prefs`, `Favorites` through `Prefs`), two picture files in `getFilesDir()` (`BackgroundStore`), one update file in the app's cache folder (`Updater`). No SQL database of its own. |
| Network | Only `Updater` / `ReleaseApi`: GitHub's release API and the APK download. `INTERNET` permission is declared in `AndroidManifest.xml`. |
| Scheduled work | None on the device (no `WorkManager`, `AlarmManager` or `JobScheduler`). The update check is started from `LibraryActivity.onStart` and is rate limited by `Updater.autoCheckIfDue`. In the repository, GitHub Actions workflows `.github/workflows/build.yml`, `release.yml`, `release-prebuilt.yml` run builds and releases. |
| Deployment | A signed APK published as a GitHub release. No server deployment. |

Threads that exist (all verified in source):

* The **main thread**: all UI, `Handler` timers, and every callback.
* `Library.io`: a single-thread executor (`Library.java`, field `io`).
* `ArtLoader.pool`: a `ThreadPoolExecutor(2, 2, 20 s, new LifoQueue())`.
* `BeatBuzz.worker`: a single-thread executor named `bass-reader`, created on demand.
* `StorageScanner`: a new thread named `storage-scan` for each scan.
* `Updater.io`: a single-thread executor.
* `BackgroundStore.IO`: a static single-thread executor.
* Two raw `new Thread(...)` blocks in `SettingsActivity` (`readWallpaperNow`, `onActivityResult`).
* The platform's own `MediaPlayer` threads and Android's `MediaScannerConnection`.

---

## 3. Queue and Workload Discovery

There is no workload that arrives from outside over a network. The workloads below are all started by the user,
by the Android media system, or by a timer inside the app.

| # | Workload | Source | Sync or async | Can work accumulate? | Queue or buffer | When capacity is full |
|---|---|---|---|---|---|---|
| W1 | Reload the song list | App start, pull of a new file (MediaStore `ContentObserver`), "Scan" and "Show all audio" | Async on `Library.io` | No: at most one running and one waiting | A boolean pair `loading` / `dirty` in `Library.refresh` | Extra requests are merged into one repeat |
| W2 | Cover pictures | Every list row, the mini player, Now Playing, the backdrop | Async on `ArtLoader.pool` | **Yes** | `LifoQueue`, a `LinkedBlockingDeque` without a limit | Newest request first; stale rows are skipped in `load` only |
| W3 | Bass measurement ("Vibrate with the music", off by default) | Each song change, only when the option is on | Async on `BeatBuzz.worker` | Yes, a little | Executor queue (unbounded) | Older songs are cancelled by a flag |
| W4 | Storage scan | The user taps "Scan storage" | Async, new thread | No: guarded by a `scanning` flag in `LibraryActivity` and `SettingsActivity` | None | A second tap is ignored |
| W5 | Update check and download | `autoCheckIfDue` or the user | Async on `Updater.io` | No: `Updater.busy()` guard | None | A second request is ignored |
| W6 | Background picture or wallpaper read | The user in Settings | Async | Yes, by repeated taps | `BackgroundStore.IO` queue, or a raw thread | Each tap starts more work |
| W7 | Home-screen widget redraw | Every playback change (`PlaybackService.notifyChanged`) | Sync on the main thread | No | None | Each change redraws |
| W8 | Playback itself | The user and the play queue | Async (`MediaPlayer.prepareAsync`) | No: one `MediaPlayer` at a time | The play queue (`PlayQueue`) | Only the user adds to it |
| W9 | Saving the queue and progress | Queue change, and every 15 s while playing | Sync call, asynchronous disk write (`SharedPreferences.apply`) | No | The `SharedPreferences` write queue of Android | Writes are merged by Android |
| W10 | Moving bars and tick timers | Screen on and music playing | Main-thread timers | No | One shared clock (`EqBarsView.RUNNING`) | The clock stops when nothing moves |
| W11 | Deleting songs | The user | Sync or system dialog | No | None | One request at a time |

Not present in this project: HTTP request handling, email or push processing, payment, file upload, webhooks,
event consumers, AI or LLM calls, database-heavy jobs. These were searched for and not found.

---

## 4. Producer-Consumer Flow

```text
  user taps / scroll ──────────────┬──► ListView row binding ──► ArtLoader.load ──► pool(2) ──► main.post ──► ImageView
                                   │                                  └─ cache (LRU, heap/12)   └─ "missing" set
  Android MediaStore change ──► ContentObserver (debounce 1.5 s) ──► Library.refresh ──► io(1) ──► Snapshot ──► listeners
  song changes (option on) ──► BeatBuzz.setTrack ──► worker(1) ──► EnvelopeDecoder.decode ──► envelope cache (6 songs)
                                                                   └─► BeatBuzz.step every 50 ms ──► Vibrator
  "Scan storage" tap ──► StorageScanner.scan ──► Thread ──► MediaScannerConnection ──► MediaStore change ──► (reload above)
  update check (≤ 1/day) ──► Updater.io(1) ──► ReleaseApi (timeouts, 512 KB cap) ──► Prefs
  play / next / queue edit ──► PlayQueue (no size cap) ──► PlaybackService ──► MediaPlayer.prepareAsync ──► onPrepared / onError
                                         └─► Prefs.saveQueue (whole id list) ──► SharedPreferences.apply
```

Completion is reported back to the main thread with `Handler.post`. Stale results are dropped by a
**generation counter** (`PlaybackService.generation`), a **tag on the view** (`ArtLoader.load`), or a **cancel flag**
(`BeatBuzz`, `Updater`).

---

## 5. Backpressure Implementation Analysis

| Mechanism | Where | Present? | Evidence |
|---|---|---|---|
| Reload coalescing | `Library.refresh` | **Yes** | If `loading` is true it only sets `dirty` and returns; one more reload runs after the first finishes. |
| Debounce of change events | `Library` constructor, `ContentObserver.onChange` | Yes, no maximum wait | `removeCallbacks(delayedRefresh)` then `postDelayed(..., 1500)`. |
| Bounded cache | `ArtLoader` constructor | **Yes** | `LruCache` sized at one twelfth of the maximum heap, at least 4096 KB. |
| Bounded workers | `ArtLoader.pool` | Yes (2 threads), **queue unbounded** | `new ThreadPoolExecutor(2, 2, 20, SECONDS, new LifoQueue())`; `LifoQueue extends LinkedBlockingDeque<Runnable>` with default capacity. |
| Newest first | `ArtLoader.LifoQueue.offer` | Yes | `offerFirst(r)`. |
| Skip stale work | `ArtLoader.load` | Yes | The task returns at once if the view's `tag_art` no longer matches its key. `loadAsync` has **no** such check. |
| Remember failures | `ArtLoader.missing` | Yes | A failed decode is not tried again until `forgetMisses()` (called from `Library.refresh`). |
| Request admission | `LibraryActivity.startScan`, `Updater.busy()` | Yes | A second scan or update request is ignored while one runs. |
| Rate limit | `Updater.autoCheckIfDue` | **Yes** | At most one automatic check a day (`DAY_MS`), and at most one attempt an hour after a failure (`HOUR_MS`). Test: `UpdaterTest`. |
| Load shedding | none | No | Nothing is rejected; nothing needs to be. |
| Deadline on starting a song | `PlaybackService.startCurrent` | **No** | `prepareAsync()` is called and only `OnErrorListener` / `OnPreparedListener` are set. |
| Event-loop blocking | main thread | Partly | The song list is rebuilt and searched on the main thread (`LibraryActivity.rebuild`, `Library.search`). |

---

## 6. Queue Capacity and Concurrency

| Component | Threads | Queue capacity | Task time limit | Evidence |
|---|---|---|---|---|
| `Library.io` | 1 | unbounded executor queue, but never more than 1 task in it (coalesced) | none | `Library.java` fields `io`, `loading`, `dirty` |
| `ArtLoader.pool` | 2 (core = max) | **unbounded** | none | `ArtLoader.java` constructor |
| `BeatBuzz.worker` | 1 | unbounded executor queue | none; checks `cancel` only inside the decode loop | `BeatBuzz.setTrack`, `EnvelopeDecoder.decode` |
| `StorageScanner` thread | 1 per scan | not applicable | none; walks at most depth 12; stops adding after 50 000 new files | `StorageScanner.MAX_DEPTH`, `MAX_FILES`, `walk` |
| `Updater.io` | 1 | unbounded, but guarded by `busy()` | HTTP connect 10 s, read 20 s (`ReleaseApi.CONNECT_MS`, `READ_MS`) | `Updater.java`, `ReleaseApi.java` |
| `BackgroundStore.IO` | 1 (static) | unbounded | none | `BackgroundStore.java` field `IO` |
| Raw threads in `SettingsActivity` | 1 per tap | not applicable | none | `readWallpaperNow`, `onActivityResult` |
| `PlayQueue` | not applicable | **no cap** | not applicable | `PlayQueue.add`, `playNext`, `set` |

Concurrency settings are fixed constants in code. There are no configuration files, environment variables or runtime tuning knobs.

---

## 7. Retry and Failure Handling

| Item | Behaviour | Evidence |
|---|---|---|
| Retries | **No automatic retry loop exists** in the app. | Searched for retry, backoff, attempt: none in `java/`. |
| Song fails to play | The next song is tried. After more than `min(queue size, 5)` failures in a row it stops and shows a message. The counter resets when a song prepares. | `PlaybackService.onError`, `errorStreak` |
| Update check fails | Not retried by the app; the next automatic attempt waits at least an hour. A manual check is the user's choice. | `Updater.check`, `Updater.autoCheckIfDue` |
| Download fails | Part file deleted, error shown, user retries. Checksum is verified; a wrong file is deleted. | `Updater.download` |
| Cover art fails | Remembered in `missing`, not retried until the next library reload. | `ArtLoader.load`, `loadAsync` |
| Bass reading fails | No buzz for that song; no retry. | `EnvelopeDecoder.decode` returns `null` on any exception |
| Dead-letter queue | None, and none is needed. | |
| Duplicate processing | Stale callbacks are ignored by the generation counter (`gen != generation`). Cover art for one key can be decoded twice if two views ask at the same moment (no in-flight de-duplication). | `PlaybackService`, `ArtLoader.load` |
| Idempotency | Cover decoding and library queries are read-only, so repeating them is harmless. | |
| Crash recovery | After the process dies, the saved queue is restored once the library has loaded (`PlaybackService.restoreQueue`). Nothing else is durable, so nothing needs replaying. | |
| Poison items | A song that cannot be decoded is skipped (play) or ignored (bass, art). | |

Interaction with queue growth: because there are no retries, a failure cannot feed more work into a queue.
The one repeating cause is **fast skipping** with the bass option on (finding QBP-03).

---

## 8. Resource Protection

| Resource | Protection | Evidence | Gap |
|---|---|---|---|
| Memory for covers | LRU limited to heap / 12; large embedded pictures are sampled (`inSampleSize`) on API < 29; `loadThumbnail(uri, Size)` on API ≥ 29 | `ArtLoader` | On API < 29 the whole embedded picture is read into a `byte[]` first (QBP-13) |
| Memory for backgrounds | Stored picture limited to 1280 px, decoded with `inSampleSize` | `BackgroundStore.MAX_SIDE`, `loadNow` | none found |
| Memory for the song list | One snapshot of all songs, replaced as a whole | `Library.Snapshot` | Grows with the number of songs (expected) |
| Memory for the bass reader | Envelope is about 20 floats per second of song; at most 6 songs cached | `BassEnvelope`, `BeatBuzz.KEEP` | none found |
| CPU | Cover decode on 2 threads; bass decode at background thread priority (`Process.THREAD_PRIORITY_BACKGROUND`) | `ArtLoader`, `BeatBuzz.setTrack` | Search and list rebuild run on the main thread (QBP-06) |
| Wake locks | `MediaPlayer.setWakeMode(PARTIAL_WAKE_LOCK)` | `PlaybackService.startCurrent` | none found |
| Network reads | Connect and read timeouts; JSON capped at 512 KB; HTTPS required unless a test allows HTTP | `ReleaseApi` | No byte cap or total deadline on the APK download (QBP-08) |
| Disk | Free-space check before the APK download (needs size + 5 MB) | `ReleaseApi.download` | none found |
| Database connections | Not applicable. MediaStore queries run one at a time on `Library.io` | `Library.query` | none found |
| File descriptors | `MediaExtractor`, `MediaCodec`, `MediaMetadataRetriever`, cursors and streams are released in `finally` or try-with-resources | `EnvelopeDecoder`, `ArtLoader.decode`, `Library.query`, `ReleaseApi.download` | none found |
| Isolation between workloads | Library, art, bass, update and picture work each have their own executor, so one cannot starve another's thread. They share the device's CPU, disk and codecs. | | Codec sharing between playback and the bass reader is device dependent (P-01) |

---

## 9. Observability and Monitoring

| Signal | Exists? | Where |
|---|---|---|
| Queue depth, waiting time | No | |
| Job duration | No | |
| Worker utilisation | No | |
| Failed or retried job counts | No | |
| Rejected requests | No | |
| Logs | **None.** There are no `Log.d/w/e/i` calls in `app/src/main/java`. | searched |
| Crash or error reporting | None (no dependency or code for it) | `app/build.gradle` |
| User-visible signals | Toasts and notices: "skipped", "can't play", scan result, update state | `PlaybackService.onError`, `LibraryActivity.startScan`, `Updater` |
| Test hooks | `EqBarsView.runningCount()`, `BeatBuzz.buzzes()`, `BeatBuzz.lastStrength()` exist for unit tests only | |
| Alerts, dashboards | No | |

The app has nothing an operator could watch. For an offline single-user app this is normal, but it means a
stuck background task would be invisible except through how the screen behaves.

---

## 10. Real-World Failure Scenarios

All results come from reading the code. None was tested.

| Scenario | Local equivalent in this app | Result | Reasoning |
|---|---|---|---|
| A. Sudden traffic spike | A burst of input: a very fast fling through a long list, or thousands of MediaStore change events during a large copy | **Partially protected** | Reloads are merged (`Library.refresh`) and debounced; cover requests are served newest first and stale ones are skipped, but the cover queue has no limit (QBP-01, QBP-02). |
| B. Worker slows down | A slow SD card or USB drive makes cover decoding or the library query slow | **Partially protected** | Work does not pile up for the library (merged). Cover tasks do queue, newest first. Playback is not behind any of these queues. |
| C. All workers unavailable | A background executor is stuck | **Not applicable / Partially protected** | The app accepts no external work. User actions would still be accepted and queued locally; the screens use cached data. A stuck `prepareAsync` has no deadline (QBP-04). |
| D. External API becomes slow | GitHub does not answer | **Protected** | 10 s connect and 20 s read timeouts; one worker; `busy()` blocks a second request; at most one automatic check a day and one attempt an hour. |
| E. Database connection exhaustion | Many MediaStore queries at once | **Not applicable / Protected** | There is no pool. Library queries run on one thread, and art decoding uses at most 2 threads. |
| F. Retry storm | Failures re-entering a queue | **Protected** | No automatic retries; the skip-on-error streak is bounded; failed covers are remembered. |
| G. Large payloads | A huge embedded cover, a huge picture chosen for the background, a very long audio file for the bass reader | **Partially protected** | Pictures are sampled or sized; the update JSON is capped. On API < 29 a large embedded cover is loaded whole before sampling (QBP-13). A very long audio file is decoded completely by the bass reader (QBP-03). |
| H. Recovery after outage | The process is killed and restarted | **Protected** | Nothing durable is queued. The saved play queue is restored once, and covers and bass data are rebuilt on demand. There is no backlog to flood downstream services. |

---

## 11. Confirmed Findings and Evidence

Paths are relative to `app/src/main/java/com/musicplayer/lite/`.

### QBP-01: Cover-art task queue has no capacity limit
* **Severity:** Low. **Component:** `ArtLoader`. **Function:** constructor, `LifoQueue`, `load`.
* **Evidence:** `pool = new ThreadPoolExecutor(2, 2, 20, TimeUnit.SECONDS, new LifoQueue());` and `LifoQueue extends LinkedBlockingDeque<Runnable>` using its default capacity (no limit).
* **Explanation:** There is no maximum number of waiting cover tasks. Each waiting task holds the `ImageView` and the `Track` it was made for.
* **Impact:** Brief extra memory use and wasted wake-ups during a long, fast scroll while decoding is slow.
* **Conditions:** A fast fling through a very large list, together with slow decoding (slow SD card or USB drive).
* **Existing protection:** Newest-first order (`offerFirst`); `load` skips a task whose view was already reused for another row; the pool has only 2 threads, so decoding cannot take over all CPUs.
* **Mitigation:** If it ever shows up on a device, give the deque a capacity and drop the oldest entry when full.
* **Risks of the mitigation:** A dropped task leaves a placeholder until the row is bound again.

### QBP-02: `loadAsync` has no stale-task check
* **Severity:** Low. **Component:** `ArtLoader.loadAsync`.
* **Evidence:** `load` checks `key.equals(view.getTag(R.id.tag_art))` before decoding; `loadAsync` decodes unconditionally.
* **Impact:** Few callers (backdrop and bass-related callers use it), so waste is small.
* **Existing protection:** Result is cached, and a failure is remembered in `missing`.
* **Mitigation:** Let the caller pass a "still wanted?" check.

### QBP-03: Cancelled bass-reader tasks still open a decoder
* **Severity:** Low (option is off by default). **Component:** `BeatBuzz.setTrack`, `EnvelopeDecoder.decode`.
* **Evidence:** `BeatBuzz.setTrack` sets the cancel flag and queues a new task on a single-thread executor. `EnvelopeDecoder.decode` calls `extractor.setDataSource`, `MediaCodec.createDecoderByType`, `configure`, `start` **before** its first `cancel.get()` check (inside the `while (!outputDone)` loop).
* **Explanation:** Songs skipped quickly each leave a queued task that opens and then closes a decoder.
* **Impact:** A burst of file opens and codec creations when the user skips many songs in a row with "Vibrate with the music" on. A very long file is decoded to the end (no duration limit).
* **Existing protection:** Only 6 envelopes are cached (`KEEP`); the thread runs at background priority; the cancel flag stops a running decode at the next loop pass.
* **Mitigation:** Check the cancel flag before opening the decoder, and skip files longer than a chosen limit.

### QBP-04: No deadline on starting a song
* **Severity:** Low. **Component:** `PlaybackService.startCurrent` (calls `mp.prepareAsync()`).
* **Evidence:** Only `setOnPreparedListener` and `setOnErrorListener` are set. No timer or timeout exists for `prepareAsync` (searched for `timeout` in the file).
* **Impact:** If a source never answers (for example a removed USB drive), the player may stay in the "loading" state; the user can still press next.
* **Conditions:** Source that neither prepares nor errors. How often the platform does this is **Unknown**.
* **Existing protection:** `generation` drops results from a song that is no longer wanted.
* **Mitigation:** Start a timer when `prepareAsync` is called and treat expiry as an error (reusing `onError`).
* **Risks:** A timeout that is too short would skip slow network or USB sources.

### QBP-05: Whole play queue is written on every queue change
* **Severity:** Low. **Component:** `PlaybackService.queueChanged`, `Prefs.saveQueue`, `PlayQueue`.
* **Evidence:** `saveQueue` builds one comma-separated string of every song id (`StringBuilder(order.size() * 8)`) and calls `apply()`. `PlayQueue.add` and `playNext` have no size limit or duplicate check.
* **Impact:** Cost grows with the queue (a library of tens of thousands of songs gives a few hundred KB per write). The string is built on the main thread.
* **Existing protection:** Only the user adds songs; `SharedPreferences.apply()` writes asynchronously and merges writes; progress saves use a separate small write (`saveProgress`, every 15 s).
* **Mitigation:** Cap or de-duplicate the queue, or write it from a background thread.

### QBP-06: Search runs on the main thread for every keystroke
* **Severity:** Low. **Component:** `LibraryActivity` (text watcher, `rebuild`), `Library.search`, `SortOrder.apply`.
* **Evidence:** `onTextChanged` calls `rebuild()` directly, which calls `library.search(query)` (a loop over all songs and four fields) and `sorted(...)` on the UI thread. No debounce.
* **Impact:** Possible dropped frames on very large libraries. **Not measured.**
* **Existing protection:** Default sort order returns the list without sorting (`SortOrder.apply` returns early for title order).
* **Mitigation:** Debounce the text change and run the search on `Library.io`.

### QBP-07: Storage scan has no timeout or cancel
* **Severity:** Low. **Component:** `StorageScanner.scan`; callers `LibraryActivity.startScan`, `SettingsActivity`.
* **Evidence:** `onDone` runs only after `left.decrementAndGet() == 0` in the `MediaScannerConnection.scanFile` callback. The caller's `scanning` flag is cleared only in that callback. The walk is limited by depth 12 and by 50 000 **new** files, not by the number of files visited.
* **Impact:** If a callback never arrives, "scan" stays busy until the app process ends. Whether the platform can skip a callback is **Unknown**.
* **Mitigation:** A timeout that clears `scanning` and a count of visited entries.

### QBP-08: APK download has no byte cap or total deadline
* **Severity:** Informational. **Component:** `ReleaseApi.download`.
* **Evidence:** The loop reads until end of stream; the size is compared with the announced size only afterwards (`dest.length() != expectedSize`). Per-read timeout is 20 s; there is no overall limit.
* **Existing protection:** HTTPS only; free-space check before the download; user can cancel; wrong file is deleted after the checksum check.

### QBP-09: No operational metrics or logs
* **Severity:** Informational. **Component:** whole app.
* **Evidence:** zero `Log.*` calls in `app/src/main/java`; no reporting dependency.
* **Impact:** A stuck background task cannot be diagnosed from the device.

### QBP-10: Widget redraw on every playback change, on the main thread
* **Severity:** Informational. **Component:** `PlaybackService.notifyChanged`, `PlayerWidget.publish`.
* **Evidence:** `publish` creates a scaled bitmap (`Bitmap.createScaledBitmap`) and calls `refreshAll` each time.
* **Existing protection:** It runs on playback changes, not on the 500 ms tick.

### QBP-11: Background picture work is unguarded
* **Severity:** Informational. **Component:** `SettingsActivity.readWallpaperNow`, `onActivityResult`, `BackgroundStore.IO`.
* **Evidence:** A new raw thread for each tap, and an unbounded single-thread executor; no "already running" guard.
* **Impact:** Only repeated user taps can add work.

### QBP-12: Library change debounce has no maximum wait
* **Severity:** Informational. **Component:** `Library` constructor.
* **Evidence:** Each `onChange` removes the pending reload and posts a new one 1.5 s later.
* **Impact:** During a long continuous stream of MediaStore changes the list refresh waits until it calms down. This is the opposite of overload; it only delays the update.

### QBP-13: Whole embedded picture is read into memory on API < 29
* **Severity:** Low. **Component:** `ArtLoader.decode`.
* **Evidence:** `r.getEmbeddedPicture()` returns the full `byte[]`; it is sampled only after being read.
* **Impact:** A song with a very large embedded cover can cause a short memory spike, up to two at a time (2 threads).
* **Existing protection:** API 29 and newer use `loadThumbnail(uri, Size)`; sampling reduces the decoded bitmap.

---

## 12. Risk Severity Matrix

| ID | Severity | Type | Likelihood | Impact |
|---|---|---|---|---|
| QBP-01 | Low | Confirmed | Low | Low |
| QBP-02 | Low | Confirmed | Low | Low |
| QBP-03 | Low | Confirmed | Low (option off by default) | Low |
| QBP-04 | Low | Confirmed | Unknown | Medium (song does not start) |
| QBP-05 | Low | Confirmed | Low | Low |
| QBP-06 | Low | Confirmed | Low (large libraries only) | Low |
| QBP-07 | Low | Confirmed | Unknown | Low |
| QBP-13 | Low | Confirmed | Low | Low |
| QBP-08 | Informational | Confirmed | Very low | Low |
| QBP-09 | Informational | Confirmed | Certain | Low |
| QBP-10 | Informational | Confirmed | Certain | Very low |
| QBP-11 | Informational | Confirmed | Low | Very low |
| QBP-12 | Informational | Confirmed | Low | Very low |

Critical: 0. High: 0. Medium: 0. Low: 8. Informational: 5.

**Potential risks (not confirmed):**

| ID | Risk | Why it is only potential |
|---|---|---|
| P-01 | The bass reader's `MediaCodec` competes with playback for hardware decoders on devices with few of them | Depends on the device. |
| P-02 | A vibration command every 50 ms during heavy bass (`BeatBuzz.step`) may cost battery or be limited by the system | Depends on the device and Android version. |
| P-03 | Queued cover tasks keep detached `ImageView`s alive until they run | Short-lived and bounded in practice. |
| P-04 | The `missing` set in `ArtLoader` grows with the number of songs and sizes between reloads | Small strings; cleared on every reload. |
| P-05 | Behaviour with very large libraries (50 000+ songs) | No measurements exist. |
| P-06 | Two views asking for the same cover at once decode it twice | Harmless waste; cached afterwards. |

---

## 13. Recommended Mitigations

Only mitigations that fit this architecture are listed. Do not add server-style queues, brokers or rate limiters here.

| Priority | Change | Fixes | Trade-off |
|---|---|---|---|
| 1 | A deadline around `prepareAsync`, reusing `onError` | QBP-04 | A too-short limit skips slow USB or network sources |
| 2 | Debounce search and run it on `Library.io` | QBP-06 | A few hundred milliseconds of delay in results |
| 3 | A timeout and a visited-count limit for the storage scan | QBP-07 | A real long scan may be cut short |
| 4 | Check the cancel flag before opening the decoder, and a length limit | QBP-03 | Very long songs get no bass buzz |
| 5 | Cap or de-duplicate the play queue; write it off the main thread | QBP-05 | Behaviour change for people who queue the same song twice |
| 6 | A capacity on the cover task queue, dropping the oldest | QBP-01, QBP-02 | Needs care so a visible row is never left empty |
| 7 | Cap the bytes read in `ReleaseApi.download` at the announced size | QBP-08 | None significant |
| 8 | Add a few counters for a debug build only (pending cover tasks, reload time) | QBP-09 | Extra code to maintain |

Not recommended: circuit breakers, dead-letter queues, bulkheads or job prioritisation. The app has no remote
dependency that would need them beyond the single GitHub call, which is already rate limited.

---

## 14. Missing Information and Unknowns

1. Real behaviour on libraries of 10 000, 50 000 or more songs (no measurements).
2. Whether `MediaPlayer.prepareAsync` can stay silent forever for a removed USB drive on target head units.
3. Whether `MediaScannerConnection` always calls back for every path, including files that vanish during the scan.
4. How many hardware decoders typical car head units have, and whether the bass reader can take one from playback.
5. Battery and thermal effect of the 50 ms vibration commands.
6. How slow typical SD cards and USB sticks make cover decoding.
7. Whether any test covers the reload coalescing (`Library.refresh`, `dirty`) or the skip streak (`PlaybackService.errorStreak`). A search of `app/src/test` found no test naming them. `UpdaterTest` does exercise the once-a-day check.

---

## 15. Production Readiness Assessment

| Question | Answer |
|---|---|
| Can the app be overloaded from outside? | No. It has no network listener and no remote producer. |
| Can local work grow without bound? | Only the cover task queue (and user-driven lists), and its growth is limited by what is on screen. |
| Do retries amplify failures? | No. There is no automatic retry loop. |
| Is a slow dependency contained? | Yes for GitHub (timeouts, one worker, rate limit). Playback of a slow source has no deadline. |
| Can an operator see overload? | No. There are no logs or metrics. |
| Overall | **Acceptable for a single-user offline player.** The findings are hardening items, not blockers. |

---

## 16. Final Audit Checklist

| Check | Status |
|---|---|
| Project structure scanned | Done |
| All executors, threads, handlers and queues found | Done (section 2, 6) |
| Workloads traced from source to completion | Done (section 3, 4) |
| Retry and failure paths inspected | Done (section 7) |
| Resource limits inspected | Done (section 8) |
| Observability inspected | Done (section 9) |
| Scenarios A to H classified | Done (section 10), by code reading only |
| Findings have file, function and evidence | Done (section 11) |
| Mitigations fit the architecture | Done (section 13) |
| Tests, load runs or destructive actions performed for this audit | **None** |
| Source code, configuration or other documents changed | **No.** Only this file was added. |
