package com.musicplayer.lite;

import android.media.MediaDataSource;
import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Lets the app read a song file itself and hand the bytes to the player. Some car radios let the
 * app open a file but not the player's own system process (or the other way round), so a song that
 * will not start any other way can still play like this.
 */
final class FileDataSource extends MediaDataSource {
    private final FileChannel channel;
    private final Closeable owner;

    private FileDataSource(FileChannel channel, Closeable owner) {
        this.channel = channel;
        this.owner = owner;
    }

    /** Reads from a file descriptor, such as the one the music library gives for a song. */
    static FileDataSource of(ParcelFileDescriptor pfd) {
        final FileInputStream in = new FileInputStream(pfd.getFileDescriptor());
        return new FileDataSource(in.getChannel(), () -> {
            try {
                in.close();
            } finally {
                pfd.close();
            }
        });
    }

    /** Reads from a file by its path. */
    static FileDataSource of(String path) throws IOException {
        final FileInputStream in = new FileInputStream(path);
        return new FileDataSource(in.getChannel(), in);
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if (size == 0) return 0;
        if (position >= channel.size()) return -1;
        final int n = channel.read(ByteBuffer.wrap(buffer, offset, size), position);
        return n <= 0 ? -1 : n;
    }

    @Override
    public long getSize() throws IOException {
        return channel.size();
    }

    @Override
    public void close() throws IOException {
        owner.close();
    }
}
