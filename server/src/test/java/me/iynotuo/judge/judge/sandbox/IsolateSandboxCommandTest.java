package me.iynotuo.judge.judge.sandbox;

import me.iynotuo.judge.config.JudgeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the isolate command line and the host-to-sandbox path translation.
 *
 * <p>The isolate engine has no automated coverage in this repository (that would require a working
 * sandbox plus root owned cgroup setup), so this test guards the parts that a refactor can silently
 * break: the argv order and formatting, and how paths below the working directory are rewritten to
 * {@code /box}.</p>
 */
class IsolateSandboxCommandTest {

    /**
     * Builds the isolate argv exactly as the engine did before the sandbox was extracted.
     */
    @Test
    void buildsTheExpectedIsolateCommand(@TempDir Path dir) {
        IsolateSandbox sandbox = new IsolateSandbox(properties());
        Path meta = dir.getParent().resolve("run-1.meta");

        List<String> command = sandbox.isolateCommand(3, dir, "/box/input.txt", "/box/stdout.txt",
                "/box/stderr.txt", meta, 1, new Limits(1000, 4, 524288, 16448), List.of("/box/main"));

        assertThat(command).containsExactly(
                "/usr/bin/isolate",
                "--cg",
                "--box-id=3",
                "--processes=1",
                "--time=1.00",
                "--wall-time=4.00",
                "--cg-mem=524288",
                "--fsize=16448",
                "--env=PATH=/usr/bin:/bin",
                "--env=TMPDIR=/box",
                "--env=LANG=C.UTF-8",
                "--dir=/box=" + dir.toAbsolutePath() + ":rw",
                "--dir=/etc/java-21-openjdk=/etc/java-21-openjdk",
                "--chdir=/box",
                "--stdin=/box/input.txt",
                "--stdout=/box/stdout.txt",
                "--stderr=/box/stderr.txt",
                "--meta=" + meta.toAbsolutePath(),
                "--run",
                "--",
                "/box/main");
    }

    /**
     * Verifies that relative executables, files in the working directory and PATH names are handled.
     */
    @Test
    void translatesHostArgvIntoSandboxArgv(@TempDir Path dir) throws Exception {
        IsolateSandbox sandbox = new IsolateSandbox(properties());
        Files.writeString(dir.resolve("checker"), "");
        Files.writeString(dir.resolve("main.cpp"), "");

        assertThat(sandbox.boxArgv(List.of("./main"), dir)).containsExactly("/box/main");
        assertThat(sandbox.boxArgv(List.of("checker"), dir)).containsExactly("/box/checker");
        assertThat(sandbox.boxArgv(List.of("g++", "-O2", "-o", "main", "main.cpp"), dir))
                .containsExactly("g++", "-O2", "-o", "main", "main.cpp");
        assertThat(sandbox.boxArgv(List.of("java", "-Xmx256m", "Main"), dir))
                .containsExactly("java", "-Xmx256m", "Main");
    }

    /**
     * Creates a configuration with a single read-only extra directory and PATH resolution disabled, so
     * the assertions do not depend on the machine that runs the test.
     *
     * @return judge properties for the sandbox
     */
    private JudgeProperties properties() {
        JudgeProperties properties = new JudgeProperties();
        JudgeProperties.Isolate isolate = properties.getIsolate();
        isolate.setBinary("/usr/bin/isolate");
        isolate.setCheckOnStartup(false);
        isolate.setResolveExecutables(false);
        isolate.setExtraDirs(List.of("/etc/java-21-openjdk"));
        return properties;
    }
}
