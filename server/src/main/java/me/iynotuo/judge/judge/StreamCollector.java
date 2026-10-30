package me.iynotuo.judge.judge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 读取子进程输出，超过上限后继续消费但不再保存，避免进程因管道写满而阻塞或撑爆内存。
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
            // 进程被 kill 时读取会抛异常，忽略即可
        }
    }

    String text() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    boolean truncated() {
        return truncated;
    }
}
