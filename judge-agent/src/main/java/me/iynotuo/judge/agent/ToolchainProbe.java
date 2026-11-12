package me.iynotuo.judge.agent;

import me.iynotuo.judge.core.sandbox.Commands;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs host-side probe commands such as {@code javac -version}. A probe passes only when the command
 * exits with status 0 before the timeout.
 */
@Component
public class ToolchainProbe {

    private static final int OUTPUT_LIMIT = 512;

    private final AgentProperties properties;

    public ToolchainProbe(AgentProperties properties) {
        this.properties = properties;
    }

    public List<ProbeResult> probeAll(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of(new ProbeResult(false, "", "未配置探测命令"));
        }
        List<ProbeResult> results = new ArrayList<>(commands.size());
        for (String command : commands) {
            results.add(probe(command));
        }
        return results;
    }

    public ProbeResult probeExecutable(String executable, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(List.of(arguments));
        return run(command);
    }

    public ProbeResult probe(String command) {
        return run(Commands.split(command));
    }

    private ProbeResult run(List<String> command) {
        if (command.isEmpty()) {
            return new ProbeResult(false, "", "空探测命令");
        }
        String display = String.join(" ", command);
        long timeoutMs = Math.max(100, properties.getProbeTimeoutMs());
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                String output = readLimited(process.getInputStream());
                return new ProbeResult(false, display, "探测超时: " + output);
            }
            String output = readLimited(process.getInputStream());
            if (process.exitValue() != 0) {
                return new ProbeResult(false, display,
                        "退出码 " + process.exitValue() + ": " + output);
            }
            return new ProbeResult(true, display, output);
        } catch (IOException ex) {
            return new ProbeResult(false, display, "无法执行: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new ProbeResult(false, display, "探测被中断");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String readLimited(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(OUTPUT_LIMIT);
        return new String(bytes, StandardCharsets.UTF_8).trim().replaceAll("\\s+", " ");
    }

    /**
     * Result of one probe command.
     */
    public record ProbeResult(boolean ok, String command, String message) {
    }
}
