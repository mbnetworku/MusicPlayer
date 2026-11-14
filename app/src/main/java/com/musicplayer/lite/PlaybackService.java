package com.musicplayer.lite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays the queue. Runs in the foreground while music is playing so the system
 * keeps it alive with the screen off, and owns the audio session that the
 * equalizer is attached to. Everything here runs on the main thread.
 */
public final class PlaybackService extends Service {

    interface Listener {
        /** Track, play state, queue or modes changed. */
        void onPlaybackChanged();
    }

    final class LocalBinder extends Binder {
        PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    static final String ACTION_PLAY_PAUSE = "com.musicplayer.lite.PLAY_PAUSE";
    static final String ACTION_NEXT = "com.musicplayer.lite.NEXT";
    static final String ACTION_PREVIOUS = "com.musicplayer.lite.PREVIOUS";
    static final String ACTION_STOP = "com.musicplayer.lite.STOP";
    static final String ACTION_MEDIA_BUTTON = "com.musicplayer.lite.MEDIA_BUTTON";

    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 1;
    /** "Previous" restarts the song when it is further in than this. */
    private static final int RESTART_MS = 3000;
    private static final int SAVE_EVERY_MS = 15000;
    /** A song that has not started after this long is treated as one that cannot be played. */
    static final int PREPARE_TIMEOUT_MS = 30000;
    /** Queue changes that come close together are written once. */
    static final int QUEUE_SAVE_DELAY_MS = 400;
    private static final int ART_PX = 512;

    private final IBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private BeatBuzz buzz;
    private final List<Listener> listeners = new ArrayList<>();
    private final PlayQueue queue = new PlayQueue();

    private Prefs prefs;
    private Library library;
    private AudioManager audio;
    private NotificationManager notifications;
    private MediaSession session;
    private EffectsController effects;
    private AudioAttributes attrs;
    private AudioFocusRequest focusRequest;
    private int audioSessionId;

    private MediaPlayer player;
    /** Bumped for every new player so late callbacks from an old one are ignored. */
    private int generation;
    private boolean prepared;
    private boolean playing;
    /** What the user asked for; differs from {@link #playing} while buffering or interrupted. */
    private boolean wantPlay;
    private boolean pausedByFocus;
    private boolean hasFocus;
    private boolean noisyRegistered;
    private boolean foreground;
    /** A playback notification is on screen (so it must be kept up to date). */
    private boolean notificationShown;
    private boolean restored;
    private int pendingSeek;
    private int errorStreak;
    private Bitmap art;

    // ---- lifecycle ----------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = Prefs.get(this);
        preferredWay = prefs.playWay();   // the way that worked last time on this device
        library = Library.get(this);
        audio = getSystemService(AudioManager.class);
        notifications = getSystemService(NotificationManager.class);
        attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        int id = audio.generateAudioSessionId();
        audioSessionId = id > 0 ? id : 0;

        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW));

        session = new MediaSession(this, "MusicPlayer");
        session.setCallback(sessionCallback);
        session.setSessionActivity(openAppIntent());
        ComponentName receiver = new ComponentName(this, MediaButtonReceiver.class);
        if (Build.VERSION.SDK_INT >= 31) {
            session.setMediaButtonBroadcastReceiver(receiver);
        } else {
            session.setMediaButtonReceiver(PendingIntent.getBroadcast(this, 0,
                    new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(receiver),
                    PendingIntent.FLAG_IMMUTABLE));
        }

        effects = new EffectsController(this, audioSessionId);
        broadcastEffectSession(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);

        // Bring back the last queue (paused, at the saved position) once the library is known.
        library.whenLoaded(this::restoreQueue);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_MEDIA_BUTTON.equals(action)) {
            // Started with startForegroundService(): must call startForeground() promptly.
            enterForeground(buildNotification());
            final KeyEvent key = MediaButtonReceiver.keyEventOf(intent);
            runWhenReady(() -> {
                if (key != null) handleKey(key.getKeyCode());
                if (!playing && !wantPlay) postNotification();
                stopIfIdle();
            });
        } else if (ACTION_PLAY_PAUSE.equals(action)) {
            runWhenReady(this::togglePlayPause);
        } else if (ACTION_NEXT.equals(action)) {
            runWhenReady(() -> next(true));
        } else if (ACTION_PREVIOUS.equals(action)) {
            runWhenReady(this::previous);
        } else if (ACTION_STOP.equals(action)) {
            stopAndRelease();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        saveQueueNow();
        if (!playing && !wantPlay) stopAndRelease();
    }

    @Override
    public void onDestroy() {
        saveQueueNow();   // before the timers are cleared: a change that waits must not be lost
        main.removeCallbacksAndMessages(null);
        saveProgress();
        releasePlayer();
        abandonFocus();
        unregisterNoisy();
        if (buzz != null) buzz.stop();
        broadcastEffectSession(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        effects.release();
        session.release();
        listeners.clear();
        super.onDestroy();
    }

    // ---- public API (main thread) -------------------------------------------------

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    PlayQueue queue() {
        return queue;
    }

    EffectsController effects() {
        return effects;
    }

    Track currentTrack() {
        return queue.current();
    }

    /** True while sound is coming out, or about to (buffering after a play request). */
    boolean isPlaying() {
        return playing || (wantPlay && !prepared && !pausedByFocus);
    }

    int position() {
        if (player != null && prepared) {
            try {
                return player.getCurrentPosition();
            } catch (IllegalStateException e) {
                return pendingSeek;
            }
        }
        return pendingSeek;
    }

    int duration() {
        if (player != null && prepared) {
            try {
                int d = player.getDuration();
                if (d > 0) return d;
            } catch (IllegalStateException ignored) {
                // fall through to the indexed duration
            }
        }
        Track t = queue.current();
        return t == null ? 0 : t.durationMs;
    }

    void playQueue(List<Track> tracks, int index, boolean shuffle) {
        if (tracks.isEmpty()) return;
        queue.set(tracks, index, shuffle);
        queueChanged();
        startCurrent(true, 0);
    }

    void play() {
        if (queue.current() == null) return;
        if (player == null) {
            startCurrent(true, pendingSeek);
            return;
        }
        wantPlay = true;
        if (prepared) startPlayback();
        else postNotification();
    }

    void pause() {
        wantPlay = false;
        pausedByFocus = false;
        if (player != null && prepared && playing) {
            try {
                player.pause();
            } catch (IllegalStateException ignored) {
                // already stopped
            }
        }
        playing = false;
        main.removeCallbacks(saveTick);
        unregisterNoisy();
        abandonFocus();
        saveProgress();
        updateSession();
        postNotification();
        notifyChanged();
    }

    void togglePlayPause() {
        if (isPlaying()) pause();
        else play();
    }

    void next(boolean byUser) {
        Track n = queue.advance(byUser);
        if (n == null) {
            endOfQueue();
            return;
        }
        queueProgressChanged();
        startCurrent(true, 0);
    }

    void previous() {
        if (queue.current() == null) return;
        if (position() > RESTART_MS) {
            seekTo(0);
            return;
        }
        queue.retreat();
        queueProgressChanged();
        startCurrent(true, 0);
    }

    void seekTo(int ms) {
        ms = Math.max(0, ms);
        if (player != null && prepared) {
            try {
                player.seekTo(ms, MediaPlayer.SEEK_CLOSEST);
            } catch (IllegalStateException ignored) {
                // player is being replaced
            }
        } else {
            pendingSeek = ms;
        }
        updateSession();
    }

    void skipToQueueItem(int index) {
        queue.jumpTo(index);
        queueProgressChanged();
        startCurrent(true, 0);
    }

    void playNext(List<Track> tracks) {
        boolean wasEmpty = queue.size() == 0;
        for (int i = tracks.size() - 1; i >= 0; i--) queue.playNext(tracks.get(i));
        queueChanged();
        if (wasEmpty) showQueuedTrack();
    }

    void enqueue(List<Track> tracks) {
        boolean wasEmpty = queue.size() == 0;
        for (Track t : tracks) queue.add(t);
        queueChanged();
        if (wasEmpty) showQueuedTrack();
    }

    /** The first track was added to an empty queue: it becomes current, paused. */
    private void showQueuedTrack() {
        art = null;
        Track t = queue.current();
        if (t != null) loadArt(t, generation);
        updateMetadata();
        updateSession();
    }

    void removeFromQueue(int index) {
        boolean wasCurrent = queue.remove(index);
        queueChanged();
        if (!wasCurrent) return;
        if (queue.size() == 0) {
            stopAndRelease();
        } else {
            startCurrent(playing || wantPlay, 0);
        }
    }

    /** Drops deleted songs from the queue. Playback moves on if the current song was one of them. */
    void removeTracks(java.util.Set<Long> ids) {
        List<Track> list = queue.tracks();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (ids.contains(list.get(i).id)) removeFromQueue(i);
        }
    }

    void setShuffle(boolean on) {
        queue.setShuffle(on);
        queueChanged();
    }

    void cycleRepeat() {
        queue.cycleRepeat();
        queueChanged();
    }

    // ---- starting a track -----------------------------------------------------------

    /**
     * The ways to open a song. The content address from the music library is the default. If it does
     * not work the file path is tried, then the app reads the file itself and gives the bytes to the player.
     */
    static final int WAY_URI = 1;
    static final int WAY_PATH = 2;
    static final int WAY_STREAM = 4;
    /** The way the next start must use (0: the one that worked last time, or the content address). */
    private int nextWay;
    /** The way the song that is loading was opened with. */
    private int wayNow = WAY_URI;
    /** The ways already tried for {@link #triedFor}, so that each is tried once. */
    private int triedMask;
    private long triedFor = -1;
    /** The way that worked after another had failed: it goes first from then on (this radio needs it). */
    private int preferredWay = WAY_URI;
    /** The app reads the file itself: this is the source. It is closed when the player is released. */
    private android.media.MediaDataSource streamSource;
    /** A message to show the user that "All files access" may fix this. Taken once by the library screen. */
    private boolean allFilesHint;
    /** Why the last song could not be played: a MediaPlayer error code, or one of the values below. */
    private static final int WHY_OPEN = -9001;
    private static final int WHY_TIMEOUT = -9002;

    private void startCurrent(boolean autoPlay, int startMs) {
        Track t = queue.current();
        releasePlayer();
        pendingSeek = Math.max(0, startMs);
        prepared = false;
        playing = false;
        wantPlay = autoPlay;
        pausedByFocus = false;
        art = null;
        updateMetadata();
        if (t == null) {
            updateSession();
            notifyChanged();
            return;
        }
        final int gen = ++generation;
        loadArt(t, gen);
        final int way = chooseWay(t);
        nextWay = 0;
        wayNow = way;
        if (triedFor != t.id) {
            triedFor = t.id;
            triedMask = 0;
        }
        triedMask |= way;

        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            if (audioSessionId > 0) mp.setAudioSessionId(audioSessionId);
            mp.setAudioAttributes(attrs);
            mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mp.setOnPreparedListener(m -> onPrepared(gen));
            mp.setOnCompletionListener(m -> onCompleted(gen));
            mp.setOnErrorListener((m, what, extra) -> {
                onError(gen, what, extra);
                return true;
            });
            if (way == WAY_PATH) {
                mp.setDataSource(t.path());
            } else if (way == WAY_STREAM) {
                streamSource = openStream(t);
                mp.setDataSource(streamSource);
            } else {
                mp.setDataSource(getApplicationContext(), t.uri());
            }
            mp.prepareAsync();
            timeoutGeneration = gen;
            main.removeCallbacks(prepareTimeout);
            main.postDelayed(prepareTimeout, PREPARE_TIMEOUT_MS);
        } catch (Exception e) {
            onError(gen, WHY_OPEN, 0);
            return;
        }
        updateSession();
        postNotification();
        notifyChanged();
    }

    private void onPrepared(int gen) {
        if (gen != generation || player == null) return;
        main.removeCallbacks(prepareTimeout);
        prepared = true;
        errorStreak = 0;
        if (Integer.bitCount(triedMask) > 1 && preferredWay != wayNow) {
            preferredWay = wayNow;   // another way failed for this song and this one worked: use it first from now on
            prefs.setPlayWay(wayNow);
        }
        triedFor = -1;
        triedMask = 0;
        if (pendingSeek > 0) {
            try {
                player.seekTo(pendingSeek, MediaPlayer.SEEK_CLOSEST);
            } catch (IllegalStateException ignored) {
                // start from the beginning instead
            }
            pendingSeek = 0;
        }
        if (wantPlay) {
            startPlayback();
        } else {
            updateSession();
            notifyChanged();
        }
    }

    private void startPlayback() {
        if (player == null || !prepared) {
            wantPlay = true;
            return;
        }
        final int focus = requestFocus();
        if (focus == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            // Something else (a phone call) has the sound for now. Android tells us when it is free,
            // and playback then starts by itself (see the GAIN case of the focus listener).
            wantPlay = true;
            pausedByFocus = true;
            playing = false;
            updateSession();
            postNotification();
            notifyChanged();
            return;
        }
        // Granted, or refused outright. Some car stereos refuse every request, even when nothing else
        // is playing. The user pressed play, so the song plays; Android still tells us if we lose the sound.
        try {
            player.start();
        } catch (IllegalStateException e) {
            onError(generation);
            return;
        }
        playing = true;
        wantPlay = true;
        pausedByFocus = false;
        registerNoisy();
        if (!session.isActive()) session.setActive(true);
        updateSession();
        postNotification();
        notifyChanged();
        main.removeCallbacks(saveTick);
        main.postDelayed(saveTick, SAVE_EVERY_MS);
    }

    private void onCompleted(int gen) {
        if (gen != generation) return;
        playing = false;
        if (queue.repeat() == PlayQueue.REPEAT_ONE && player != null) {
            try {
                player.seekTo(0);
                player.start();
                playing = true;
                updateSession();
                notifyChanged();
                return;
            } catch (IllegalStateException ignored) {
                // fall back to loading the track again
            }
        }
        Track n = queue.advance(false);
        if (n == null) {
            endOfQueue();
        } else {
            queueProgressChanged();
            startCurrent(true, 0);
        }
    }

    private void onError(int gen) {
        onError(gen, 0, 0);
    }

    /** The words for an error code, so that the message says why a song did not play. */
    private String reasonFor(int what, int extra) {
        if (what == WHY_TIMEOUT || extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) return getString(R.string.reason_timeout);
        if (what == WHY_OPEN) return getString(R.string.reason_open);
        if (extra == MediaPlayer.MEDIA_ERROR_IO) return getString(R.string.reason_io);
        if (extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) return getString(R.string.reason_unsupported);
        if (extra == MediaPlayer.MEDIA_ERROR_MALFORMED) return getString(R.string.reason_malformed);
        return getString(R.string.reason_unknown, what, extra);
    }

    private void onError(int gen, int what, int extra) {
        if (gen != generation) return;
        main.removeCallbacks(prepareTimeout);
        final boolean wasLoading = !prepared;
        final Track failed = queue.current();
        releasePlayer();
        playing = false;
        prepared = false;
        // A song that could not be opened one way is tried the other ways, each once. Some car radios open a
        // USB drive only one way: by address, by file path, or only when the app reads the file itself.
        if (wasLoading && failed != null && what != WHY_TIMEOUT) {
            final int next = nextUntriedWay(failed);
            if (next != 0) {
                nextWay = next;
                startCurrent(wantPlay, pendingSeek);
                return;
            }
        }
        final String reason = reasonFor(what, extra);
        final int ways = failed != null && triedFor == failed.id ? Integer.bitCount(triedMask) : 0;
        Diag.playFailed((failed == null ? "" : failed.title + ": ") + reason
                + (ways > 1 ? getString(R.string.diag_ways_tried, ways) : ""));
        triedFor = -1;   // a later try of this song starts again with the first way
        triedMask = 0;
        if ((what == WHY_OPEN || extra == MediaPlayer.MEDIA_ERROR_IO) && Permissions.allFilesNeeded()
                && !Permissions.hasAllFiles() && !prefs.askedAllFiles()) {
            allFilesHint = true;   // the library screen offers "All files access" once
        }
        errorStreak++;
        // After the last try of the only song there is nothing to skip to; with more songs the usual limit applies.
        if (errorStreak > Math.min(queue.size(), 5) || queue.size() <= 1) {
            errorStreak = 0;
            wantPlay = false;
            Toast.makeText(this, getString(R.string.toast_cant_play_reason, reason), Toast.LENGTH_LONG).show();
            updateSession();
            postNotification();
            notifyChanged();
            return;
        }
        if (errorStreak == 1) Toast.makeText(this, R.string.toast_skipped, Toast.LENGTH_SHORT).show();
        Track n = queue.advance(true);
        if (n == null) {
            endOfQueue();
        } else {
            queueProgressChanged();
            startCurrent(true, 0);
        }
    }

    /** Last track finished: park on the first track, paused, ready to play again. */
    private void endOfQueue() {
        releasePlayer();
        playing = false;
        prepared = false;
        wantPlay = false;
        pausedByFocus = false;
        pendingSeek = 0;
        queue.jumpTo(0);
        art = null;
        Track t = queue.current();
        if (t != null) loadArt(t, ++generation);
        main.removeCallbacks(saveTick);
        unregisterNoisy();
        abandonFocus();
        queueProgressChanged();
        updateMetadata();
        updateSession();
        postNotification();
        notifyChanged();
    }

    /** The song of this generation has been waiting to start; if it still is, it counts as an error. */
    private int timeoutGeneration = -1;
    private final Runnable prepareTimeout = () -> {
        if (timeoutGeneration == generation && player != null && !prepared) {
            Diag.count(Diag.PREPARE_TIMEOUT);
            onError(generation, WHY_TIMEOUT, 0);
        }
    };

    /** The way to open this song first: the one that worked before, if this song can use it. */
    private int chooseWay(Track t) {
        if (nextWay != 0) return nextWay;
        if (preferredWay == WAY_PATH && !t.dir.isEmpty()) return WAY_PATH;
        if (preferredWay == WAY_STREAM) return WAY_STREAM;
        return WAY_URI;
    }

    /** A way that was not tried yet for this song, or 0 when all were. */
    private int nextUntriedWay(Track t) {
        final int mask = triedFor == t.id ? triedMask : 0;
        if ((mask & WAY_URI) == 0) return WAY_URI;
        if ((mask & WAY_PATH) == 0 && !t.dir.isEmpty()) return WAY_PATH;
        if ((mask & WAY_STREAM) == 0) return WAY_STREAM;
        return 0;
    }

    /** Opens the song file in the app: through the music library first, then by its path. */
    private android.media.MediaDataSource openStream(Track t) throws java.io.IOException {
        try {
            final android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(t.uri(), "r");
            if (pfd != null) return FileDataSource.of(pfd);
        } catch (java.io.IOException | RuntimeException e) {
            // not through the library: try the path below
        }
        if (!t.dir.isEmpty()) return FileDataSource.of(t.path());
        throw new java.io.IOException("the file cannot be read");
    }

    /** True once if "All files access" may fix the songs that did not play. The library screen shows the offer. */
    boolean takeAllFilesHint() {
        final boolean hint = allFilesHint;
        allFilesHint = false;
        return hint;
    }

    private void releasePlayer() {
        main.removeCallbacks(prepareTimeout);
        generation++;
        MediaPlayer old = player;
        player = null;
        prepared = false;
        if (old != null) {
            old.setOnPreparedListener(null);
            old.setOnCompletionListener(null);
            old.setOnErrorListener(null);
            try {
                old.reset();
            } catch (IllegalStateException ignored) {
                // nothing to reset
            }
            old.release();
        }
        if (streamSource != null) {
            try {
                streamSource.close();
            } catch (java.io.IOException ignored) {
                // already closed
            }
            streamSource = null;
        }
    }

    private void stopAndRelease() {
        saveQueueNow();
        wantPlay = false;
        playing = false;
        pausedByFocus = false;
        main.removeCallbacks(saveTick);
        saveProgress();
        releasePlayer();
        abandonFocus();
        unregisterNoisy();
        if (session.isActive()) session.setActive(false);
        updateSession();
        stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
        notificationShown = false;
        notifications.cancel(NOTIFICATION_ID);
        notifyChanged();
        stopSelf();
    }

    private void stopIfIdle() {
        if (!playing && !wantPlay && queue.current() == null) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false;
            stopSelf();
        }
    }

    // ---- audio focus and headphone unplugging ------------------------------------

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                hasFocus = false;
                if (playing || wantPlay) pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (playing && player != null) {
                    pausedByFocus = true;
                    try {
                        player.pause();
                    } catch (IllegalStateException ignored) {
                        // already paused
                    }
                    playing = false;
                    main.removeCallbacks(this.saveTick);
                    unregisterNoisy();
                    updateSession();
                    postNotification();
                    notifyChanged();
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                hasFocus = true;
                if (pausedByFocus) {
                    pausedByFocus = false;
                    if (wantPlay) startPlayback();
                }
                break;
            default:
                // LOSS_TRANSIENT_CAN_DUCK: Android lowers our volume by itself.
                break;
        }
    };

    /** What Android said to our request for the sound: granted, delayed until it is free, or refused. */
    private int requestFocus() {
        if (hasFocus) return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(focusListener, main)
                    .build();
        }
        final int result = audio.requestAudioFocus(focusRequest);
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return result;
    }

    private void abandonFocus() {
        if (focusRequest != null && hasFocus) audio.abandonAudioFocusRequest(focusRequest);
        hasFocus = false;
    }

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (playing) pause();
        }
    };

    private void registerNoisy() {
        if (noisyRegistered) return;
        IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(noisyReceiver, f);
        }
        noisyRegistered = true;
    }

    private void unregisterNoisy() {
        if (!noisyRegistered) return;
        unregisterReceiver(noisyReceiver);
        noisyRegistered = false;
    }

    // ---- media session -------------------------------------------------------------

    private final MediaSession.Callback sessionCallback = new MediaSession.Callback() {
        @Override
        public void onPlay() {
            play();
        }

        @Override
        public void onPause() {
            pause();
        }

        @Override
        public void onSkipToNext() {
            next(true);
        }

        @Override
        public void onSkipToPrevious() {
            previous();
        }

        @Override
        public void onSeekTo(long pos) {
            seekTo((int) pos);
        }

        @Override
        public void onStop() {
            stopAndRelease();
        }
    };

    private void handleKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                play();
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                pause();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                togglePlayPause();
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                next(true);
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                previous();
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                stopAndRelease();
                break;
            default:
                break;
        }
    }

    private void updateSession() {
        if (buzz == null) buzz = new BeatBuzz(this, this::position);
        buzz.setTrack(queue.current());
        buzz.setPlaying(playing);
        Track t = queue.current();
        int state;
        if (t == null) state = PlaybackState.STATE_NONE;
        else if (playing) state = PlaybackState.STATE_PLAYING;
        else if (wantPlay && !prepared) state = PlaybackState.STATE_BUFFERING;
        else state = PlaybackState.STATE_PAUSED;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_STOP;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, position(), playing ? 1f : 0f, SystemClock.elapsedRealtime())
                .build());
    }

    private void updateMetadata() {
        Track t = queue.current();
        MediaMetadata.Builder b = new MediaMetadata.Builder();
        if (t != null) {
            b.putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration());
            if (art != null) b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        }
        session.setMetadata(b.build());
    }

    private void loadArt(Track t, final int gen) {
        ArtLoader.get(this).loadAsync(t, ART_PX, bitmap -> {
            if (gen != generation || bitmap == null) return;
            art = bitmap;
            updateMetadata();
            if (queue.current() != null) postNotification();
            PlayerWidget.publish(PlaybackService.this, queue.current(), playing, art);
        }, () -> gen == generation);
    }

    private void broadcastEffectSession(String action) {
        if (audioSessionId <= 0) return;
        sendBroadcast(new Intent(action)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName())
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC));
    }

    int audioSession() {
        return audioSessionId;
    }

    // ---- notification --------------------------------------------------------------

    private PendingIntent openAppIntent() {
        Intent i = new Intent(this, LibraryActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification.Action action(int icon, int title, String action, int code) {
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), getString(title),
                actionIntent(action, code)).build();
    }

    private Notification buildNotification() {
        Track t = queue.current();
        boolean showPause = isPlaying();
        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle(t == null ? getString(R.string.app_name) : t.title)
                .setContentText(t == null ? getString(R.string.notif_starting) : t.artist)
                .setContentIntent(openAppIntent())
                .setDeleteIntent(actionIntent(ACTION_STOP, 4))
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .addAction(action(R.drawable.ic_skip_previous, R.string.cd_previous, ACTION_PREVIOUS, 1))
                .addAction(showPause
                        ? action(R.drawable.ic_pause, R.string.cd_pause, ACTION_PLAY_PAUSE, 2)
                        : action(R.drawable.ic_play, R.string.cd_play, ACTION_PLAY_PAUSE, 2))
                .addAction(action(R.drawable.ic_skip_next, R.string.cd_next, ACTION_NEXT, 3))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        if (art != null) b.setLargeIcon(art);
        return b.build();
    }

    private void enterForeground(Notification n) {
        notificationShown = true;
        try {
            startService(new Intent(this, PlaybackService.class));
        } catch (RuntimeException ignored) {
            // Already started, or the system will not let a background app start itself.
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
            foreground = true;
        } catch (RuntimeException e) {
            notifications.notify(NOTIFICATION_ID, n);
        }
    }

    /** Foreground with a live notification while playing; a dismissible one while paused. */
    private void postNotification() {
        boolean wantForeground = playing || wantPlay;
        if (wantForeground) {
            Notification n = buildNotification();
            notificationShown = true;
            if (foreground) notifications.notify(NOTIFICATION_ID, n);
            else enterForeground(n);
        } else if (notificationShown) {
            // Paused: keep the notification (so Play works from it) but let the user swipe it away.
            if (foreground) {
                stopForeground(STOP_FOREGROUND_DETACH);
                foreground = false;
            }
            if (queue.current() != null) notifications.notify(NOTIFICATION_ID, buildNotification());
            else notifications.cancel(NOTIFICATION_ID);
        }
    }

    // ---- saved state ---------------------------------------------------------------

    private final Runnable saveTick = new Runnable() {
        @Override
        public void run() {
            saveProgress();
            if (playing) main.postDelayed(this, SAVE_EVERY_MS);
        }
    };

    private boolean queueSavePending;
    private final Runnable queueSave = this::saveQueueNow;

    private void queueChanged() {
        // Many changes in a row (dragging songs, adding a few albums) are written once, a moment later.
        queueSavePending = true;
        main.removeCallbacks(queueSave);
        main.postDelayed(queueSave, QUEUE_SAVE_DELAY_MS);
        notifyChanged();
    }

    /** Writes the queue now if a change is waiting to be written. */
    private void saveQueueNow() {
        main.removeCallbacks(queueSave);
        if (!queueSavePending) return;
        queueSavePending = false;
        Track t = queue.current();
        prefs.saveQueue(queue.tracks(), t == null ? -1 : t.id, queue.isShuffle(), queue.repeat());
    }

    /** A different track became current: remember it at 0:00 without rewriting the id list. */
    private void queueProgressChanged() {
        Track t = queue.current();
        if (t != null) prefs.saveProgress(t.id, 0);
    }

    private void saveProgress() {
        Track t = queue.current();
        if (t != null) prefs.saveProgress(t.id, position());
    }

    private void runWhenReady(Runnable r) {
        library.whenLoaded(() -> {
            restoreQueue();
            r.run();
        });
    }

    private void restoreQueue() {
        if (restored) return;
        restored = true;
        if (queue.size() > 0) return;
        long[] ids = prefs.queueIds();
        if (ids.length == 0) return;
        long currentId = prefs.queueCurrent();
        List<Track> list = new ArrayList<>(ids.length);
        int current = -1;
        for (long id : ids) {
            Track t = library.byId(id);
            if (t == null) continue;
            if (id == currentId && current < 0) current = list.size();
            list.add(t);
        }
        if (list.isEmpty()) return;
        queue.restore(list, Math.max(0, current), prefs.queueShuffle(), prefs.queueRepeat());
        pendingSeek = current >= 0 ? prefs.queuePosition() : 0;
        Track t = queue.current();
        if (t != null) loadArt(t, generation);
        updateMetadata();
        updateSession();
        notifyChanged();
    }

    private void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) l.onPlaybackChanged();
        PlayerWidget.publish(this, queue.current(), playing, art);
    }
}
