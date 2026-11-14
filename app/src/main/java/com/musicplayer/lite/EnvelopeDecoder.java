package com.musicplayer.lite;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads a song's own file and measures how heavy its bass is (see {@link BassEnvelope}). It decodes
 * the whole file once, on a background thread, at low priority. Nothing is recorded or kept: only
 * about twenty numbers for each second of the song.
 */
final class EnvelopeDecoder {
    /** A song longer than this is not read: the whole file would have to be decoded, which takes too long. */
    static final long MAX_DURATION_MS = 40L * 60 * 1000;

    private EnvelopeDecoder() {}

    /** The envelope of {@code track}, or null when the file cannot be read or {@code cancel} was set. */
    static float[] decode(Context context, Track track, AtomicBoolean cancel) {
        if (cancel.get()) return null;   // cancelled while it waited: do not even open the file
        final MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, track.uri(), null);
            int audio = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                final MediaFormat f = extractor.getTrackFormat(i);
                final String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audio = i;
                    format = f;
                    break;
                }
            }
            if (audio < 0) return null;
            extractor.selectTrack(audio);
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            int rate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            BassEnvelope envelope = new BassEnvelope(rate, channels);
            short[] pcm = new short[8192];
            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            while (!outputDone) {
                if (cancel.get()) return null;
                if (!inputDone) {
                    final int in = codec.dequeueInputBuffer(5000);
                    if (in >= 0) {
                        final ByteBuffer buf = codec.getInputBuffer(in);
                        final int size = extractor.readSampleData(buf, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                final int out = codec.dequeueOutputBuffer(info, 5000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    final MediaFormat now = codec.getOutputFormat();
                    if (now.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = now.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (now.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = now.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    envelope = new BassEnvelope(rate, channels);
                } else if (out >= 0) {
                    final ByteBuffer buf = codec.getOutputBuffer(out);
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset);
                        buf.limit(info.offset + info.size);
                        final java.nio.ShortBuffer shorts = buf.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer();
                        while (shorts.hasRemaining()) {
                            final int n = Math.min(pcm.length, shorts.remaining());
                            shorts.get(pcm, 0, n);
                            envelope.feed(pcm, n);
                        }
                    }
                    codec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            final float[] result = envelope.finish();
            return result.length == 0 ? null : result;
        } catch (Exception e) {
            return null;   // a file this phone cannot decode: no buzz for this song
        } finally {
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Exception ignored) {
                // Already gone.
            }
            extractor.release();
        }
    }
}
