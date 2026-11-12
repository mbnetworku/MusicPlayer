package com.musicplayer.lite;

import android.media.audiofx.Visualizer;

/** Listens to the player's audio session with Android's Visualizer effect (needs the RECORD_AUDIO permission). */
final class VisualizerSource implements SoundBars.Source {
    /** 1024 points give 512 bins: enough for a few dozen bars, and cheap to read. */
    private static final int WANTED_SIZE = 1024;

    @Override
    public SoundBars.Capture open(int audioSession, final SoundBars.Frames frames) {
        // Throws when the permission is missing or the device has no such effect.
        final Visualizer visualizer = new Visualizer(audioSession);
        try {
            visualizer.setEnabled(false);
            final int[] sizes = Visualizer.getCaptureSizeRange();
            visualizer.setCaptureSize(Math.max(sizes[0], Math.min(WANTED_SIZE, sizes[1])));
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {
                    // Only the spectrum is used.
                }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {
                    frames.onFft(fft, samplingRate);
                }
            }, Visualizer.getMaxCaptureRate(), false, true);
            visualizer.setEnabled(true);
        } catch (RuntimeException e) {
            visualizer.release();
            throw e;
        }
        return () -> {
            try {
                visualizer.setEnabled(false);
            } catch (RuntimeException ignored) {
                // Released already.
            }
            visualizer.release();
        };
    }
}
