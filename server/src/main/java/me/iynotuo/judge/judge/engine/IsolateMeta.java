package me.iynotuo.judge.judge.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Parsed contents of an Isolate meta file ({@code key:value} per line).
 *
 * <p>The format is not stable across versions and fields may be missing, so all accessors
 * return a safe default instead of throwing. Values must be split at the first colon only.</p>
 */
public final class IsolateMeta {

    private final Map<String, String> values;

    private IsolateMeta(Map<String, String> values) {
        this.values = values;
    }

    /**
     * Reads and parses a meta file produced by {@code isolate --meta}.
     *
     * @param file meta file to read; may be {@code null} or missing, in which case nothing is parsed
     * @return the parsed values, or an empty instance when the file is missing or unreadable; never
     *         {@code null}. Callers are expected to classify the "unknown" result as a system error
     */
    public static IsolateMeta parse(Path file) {
        Map<String, String> values = new HashMap<>();
        if (file == null || !Files.isRegularFile(file)) {
            return new IsolateMeta(values);
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int separator = line.indexOf(':');
                if (separator > 0) {
                    values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
                }
            }
        } catch (IOException ignored) {
            // A missing or unreadable meta file is treated as "unknown"; the caller classifies it.
        }
        return new IsolateMeta(values);
    }

    /**
     * Returns the raw value of one meta field.
     *
     * @param key field name as written by isolate, e.g. {@code status} or {@code cg-mem}
     * @return the trimmed raw value, or {@code null} when the field is absent
     */
    public String get(String key) {
        return values.get(key);
    }

    /**
     * Tells whether a meta field was present.
     *
     * @param key field name as written by isolate, e.g. {@code cg-oom-killed}
     * @return {@code true} when the field exists, even if its value is empty
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /**
     * Returns the isolate status code of the run.
     *
     * @return the status code ({@code TO}, {@code RE}, {@code SG}, {@code XX}) or an empty string when the
     *         program exited normally or the field is missing
     */
    public String status() {
        return values.getOrDefault("status", "");
    }

    /**
     * Tells whether the kernel killed the program for exceeding the cgroup memory limit.
     *
     * @return {@code true} when isolate reported {@code cg-oom-killed}
     */
    public boolean outOfMemory() {
        return has("cg-oom-killed");
    }

    /**
     * Returns the program exit code.
     *
     * @return the exit code, or 0 when the field is absent (for example when the program was signalled)
     */
    public int exitCode() {
        return parseInt(values.get("exitcode"));
    }

    /**
     * Returns the signal that terminated the program.
     *
     * @return the signal number, or 0 when the program exited normally or the field is absent
     */
    public int signal() {
        return parseInt(values.get("exitsig"));
    }

    /**
     * Returns the CPU time consumed by the program.
     *
     * @return CPU time in milliseconds (isolate writes seconds), or 0 when unavailable
     */
    public int timeMs() {
        return toMillis(values.get("time"));
    }

    /**
     * Returns the wall-clock time of the run.
     *
     * @return wall-clock time in milliseconds (isolate writes seconds), or 0 when unavailable
     */
    public int wallTimeMs() {
        return toMillis(values.get("time-wall"));
    }

    /**
     * Returns the peak memory usage reported by the cgroup.
     *
     * @return cgroup memory usage in KB, or 0 when unavailable (the engine then stores {@code null})
     */
    public int memoryKb() {
        return parseInt(values.get("cg-mem"));
    }

    private static int toMillis(String seconds) {
        if (seconds == null || seconds.isBlank()) {
            return 0;
        }
        try {
            return (int) Math.ceil(Double.parseDouble(seconds) * 1000.0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
