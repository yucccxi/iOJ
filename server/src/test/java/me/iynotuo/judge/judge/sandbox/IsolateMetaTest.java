package me.iynotuo.judge.judge.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IsolateMeta}: the {@code key:value} format, missing fields and OOM detection.
 */
class IsolateMetaTest {

    /**
     * Parses the fields produced by a normal, successful run.
     */
    @Test
    void parsesNormalMetaFile(@TempDir Path dir) throws Exception {
        Path meta = dir.resolve("run.meta");
        Files.writeString(meta, """
                time:0.12
                time-wall:0.34
                cg-mem:20480
                max-rss:19876
                exitcode:0
                """);

        IsolateMeta parsed = IsolateMeta.parse(meta);

        assertThat(parsed.timeMs()).isEqualTo(120);
        assertThat(parsed.wallTimeMs()).isEqualTo(340);
        assertThat(parsed.memoryKb()).isEqualTo(20480);
        assertThat(parsed.exitCode()).isZero();
        assertThat(parsed.signal()).isZero();
        assertThat(parsed.status()).isEmpty();
        assertThat(parsed.outOfMemory()).isFalse();
    }

    /**
     * Recognises a timeout, a signal and a cgroup OOM kill.
     */
    @Test
    void detectsTimeoutSignalAndOom(@TempDir Path dir) throws Exception {
        Path meta = dir.resolve("run.meta");
        Files.writeString(meta, """
                time:1.00
                status:TO
                message:Time limit exceeded
                """);
        assertThat(IsolateMeta.parse(meta).status()).isEqualTo("TO");

        Files.writeString(meta, """
                status:SG
                exitsig:11
                """);
        IsolateMeta signalled = IsolateMeta.parse(meta);
        assertThat(signalled.status()).isEqualTo("SG");
        assertThat(signalled.signal()).isEqualTo(11);

        Files.writeString(meta, """
                status:SG
                cg-oom-killed:1
                """);
        assertThat(IsolateMeta.parse(meta).outOfMemory()).isTrue();
    }

    /**
     * Splits on the first colon only, so messages that contain a colon survive.
     */
    @Test
    void splitsMessageOnFirstColon(@TempDir Path dir) throws Exception {
        Path meta = dir.resolve("run.meta");
        Files.writeString(meta, "message:exited with code: 1\n");

        assertThat(IsolateMeta.parse(meta).get("message")).isEqualTo("exited with code: 1");
    }

    /**
     * Returns safe defaults when the meta file does not exist.
     */
    @Test
    void returnsDefaultsForMissingFile(@TempDir Path dir) {
        IsolateMeta parsed = IsolateMeta.parse(dir.resolve("missing.meta"));

        assertThat(parsed.status()).isEmpty();
        assertThat(parsed.timeMs()).isZero();
        assertThat(parsed.memoryKb()).isZero();
        assertThat(parsed.exitCode()).isZero();
        assertThat(parsed.outOfMemory()).isFalse();
    }
}
