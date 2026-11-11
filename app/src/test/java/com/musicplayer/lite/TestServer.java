package com.musicplayer.lite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** A tiny local web server that plays the part of GitHub in the updater tests. */
final class TestServer implements AutoCloseable {
    private static final class Reply {
        final int code;
        final byte[] body;
        final int chunkDelayMs;

        Reply(int code, byte[] body, int chunkDelayMs) {
            this.code = code;
            this.body = body;
            this.chunkDelayMs = chunkDelayMs;
        }
    }

    private final ServerSocket socket;
    private final Map<String, Reply> routes = new ConcurrentHashMap<>();
    private volatile boolean closed;
    final AtomicInteger requests = new AtomicInteger();

    TestServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        Thread acceptor = new Thread(this::acceptLoop, "test-server");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    String base() {
        return "http://127.0.0.1:" + socket.getLocalPort();
    }

    void text(String path, int code, String body) {
        routes.put(path, new Reply(code, body.getBytes(StandardCharsets.UTF_8), 0));
    }

    void bytes(String path, byte[] body) {
        routes.put(path, new Reply(200, body, 0));
    }

    /** Sends the body in 32 KB pieces with a pause between them, so a download can be cancelled. */
    void slowBytes(String path, byte[] body, int delayMs) {
        routes.put(path, new Reply(200, body, delayMs));
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                final Socket client = socket.accept();
                Thread t = new Thread(() -> serve(client), "test-server-client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket client) {
        try (Socket c = client) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.ISO_8859_1));
            String request = in.readLine(); // "GET /path HTTP/1.1"
            if (request == null) return;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // headers are not needed
            }
            requests.incrementAndGet();
            String path = request.split(" ")[1];
            int query = path.indexOf('?');
            if (query >= 0) path = path.substring(0, query);
            Reply r = routes.get(path);
            if (r == null) r = new Reply(404, "{}".getBytes(StandardCharsets.UTF_8), 0);

            OutputStream out = c.getOutputStream();
            String head = "HTTP/1.1 " + r.code + " " + reason(r.code) + "\r\nContent-Length: " + r.body.length
                    + "\r\nConnection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            if (r.chunkDelayMs == 0) {
                out.write(r.body);
            } else {
                for (int i = 0; i < r.body.length; i += 32 * 1024) {
                    out.write(r.body, i, Math.min(32 * 1024, r.body.length - i));
                    out.flush();
                    Thread.sleep(r.chunkDelayMs);
                }
            }
            out.flush();
        } catch (IOException | InterruptedException ignored) {
            // The client hung up (a cancelled download), or the test is over.
        }
    }

    private static String reason(int code) {
        switch (code) {
            case 200:
                return "OK";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            default:
                return "Error";
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
