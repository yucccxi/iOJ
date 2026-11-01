package me.iynotuo.judge.judge.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads child process output. After the limit is reached it keeps draining the stream without buffering, so the process never blocks on a full pipe and memory stays bounded.
 */
final class StreamCollector implements Runnable {

    private final InputStream input;
    private final int limit;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private volatile boolean truncated;

    StreamCollector(InputStream input, int limit) {
        this.input = input;
        this.limit = limit;
    }

    @Override
    public void run() {
        byte[] chunk = new byte[8192];
        try (InputStream stream = input) {
            int read;
            while ((read = stream.read(chunk)) != -1) {
                int remaining = limit - buffer.size();
                if (remaining > 0) {
                    buffer.write(chunk, 0, Math.min(remaining, read));
                }
                if (read > remaining) {
                    truncated = true;
                }
            }
        } catch (IOException ignored) {
            // Reading throws when the process is killed; that is expected and ignored
        }
    }

    String text() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    boolean truncated() {
        return truncated;
    }
}
