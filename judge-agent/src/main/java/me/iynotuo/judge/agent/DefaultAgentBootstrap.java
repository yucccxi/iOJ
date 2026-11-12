package me.iynotuo.judge.agent;

import me.iynotuo.judge.core.config.JudgeProperties;
import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeLanguageConfig;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches the server configuration and verifies that this judge machine has every required toolchain.
 *
 * <p>The agent is intentionally homogeneous: a server-supported language that cannot be probed locally
 * is a fatal startup error, not a reason to claim a subset of tasks.</p>
 */
@Component
public class DefaultAgentBootstrap implements AgentBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentBootstrap.class);

    private static final int EXIT_CONFIG = 78;
    private static final int EXIT_TEMPFAIL = 75;

    private final JudgeAgentClient client;
    private final ToolchainProbe toolchainProbe;
    private final AgentProperties agentProperties;
    private final JudgeProperties judgeProperties;

    public DefaultAgentBootstrap(JudgeAgentClient client,
                                 ToolchainProbe toolchainProbe,
                                 AgentProperties agentProperties,
                                 JudgeProperties judgeProperties) {
        this.client = client;
        this.toolchainProbe = toolchainProbe;
        this.agentProperties = agentProperties;
        this.judgeProperties = judgeProperties;
    }

    @Override
    public BootstrapResult bootstrap() {
        JudgeBootstrapConfig config = fetchWithRetry();
        if (config.protocolVersion() != ProtocolVersion.CURRENT) {
            throw new AgentStartupException(EXIT_CONFIG,
                    "主服务器协议版本 " + config.protocolVersion()
                            + " 与本机 " + ProtocolVersion.CURRENT + " 不匹配，请升级 Agent");
        }
        if (config.requiredLanguages().isEmpty()) {
            throw new AgentStartupException(EXIT_CONFIG, "主服务器没有配置任何判题语言");
        }

        List<String> failures = new ArrayList<>();
        for (Language language : config.requiredLanguages()) {
            JudgeLanguageConfig languageConfig = config.languages().get(language);
            if (languageConfig == null) {
                failures.add(language.name() + ": 主服务器缺少语言配置");
                continue;
            }
            if (languageConfig.sourceFile() == null || languageConfig.sourceFile().isBlank()
                    || languageConfig.runCommand() == null || languageConfig.runCommand().isBlank()) {
                failures.add(language.name() + ": sourceFile/runCommand 配置不完整");
                continue;
            }
            if (languageConfig.probeCommands().isEmpty()) {
                failures.add(language.name() + ": 未配置 probeCommands");
                continue;
            }
            for (ToolchainProbe.ProbeResult result : toolchainProbe.probeAll(languageConfig.probeCommands())) {
                if (!result.ok()) {
                    failures.add(language.name() + ": `" + result.command() + "` 失败（" + result.message() + "）");
                } else {
                    log.info("工具链探测通过 language={} command={} output={}",
                            language, result.command(), result.message());
                }
            }
        }

        if (config.checkerEnabled()) {
            if (config.checkerProbeCommands().isEmpty()) {
                failures.add("CHECKER: checker 已启用，但未配置 probeCommands");
            } else {
                for (ToolchainProbe.ProbeResult result : toolchainProbe.probeAll(config.checkerProbeCommands())) {
                    if (!result.ok()) {
                        failures.add("CHECKER: `" + result.command() + "` 失败（" + result.message() + "）");
                    } else {
                        log.info("Checker 工具链探测通过 command={} output={}",
                                result.command(), result.message());
                    }
                }
            }
        }

        String isolateBinary = judgeProperties.getIsolate().getBinary();
        if (isolateBinary == null || isolateBinary.isBlank()) {
            failures.add("ISOLATE: 未配置 isolate.binary");
        } else {
            ToolchainProbe.ProbeResult version = toolchainProbe.probeExecutable(isolateBinary, "--version");
            ToolchainProbe.ProbeResult check = toolchainProbe.probeExecutable(isolateBinary, "--check-config");
            if (!version.ok()) {
                failures.add("ISOLATE: `" + version.command() + "` 失败（" + version.message() + "）");
            }
            if (!check.ok()) {
                failures.add("ISOLATE: `" + check.command() + "` 失败（" + check.message() + "）");
            }
        }

        if (!failures.isEmpty()) {
            throw new AgentStartupException(EXIT_CONFIG,
                    "判题机能力校验失败，拒绝启动：\n  - " + String.join("\n  - ", failures)
                            + "\n请安装缺失的开发工具包后重启 Agent。");
        }

        log.info("配置校验通过 configVersion={} requiredLanguages={}",
                config.configVersion(), config.requiredLanguages());
        return new BootstrapResult(config);
    }

    private JudgeBootstrapConfig fetchWithRetry() {
        int retries = Math.max(0, agentProperties.getConfigFetchRetries());
        long delay = Math.max(100, agentProperties.getConfigFetchRetryDelayMs());
        RuntimeException last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                JudgeBootstrapConfig config = client.fetchConfig();
                if (config == null) {
                    throw new IllegalStateException("主服务器返回空配置");
                }
                return config;
            } catch (RuntimeException ex) {
                last = ex;
                if (isAuthFailure(ex)) {
                    throw new AgentStartupException(EXIT_CONFIG,
                            "主服务器拒绝配置请求，请检查 IOJ_JUDGE_KEY：" + ex.getMessage());
                }
                log.warn("拉取判题配置失败 attempt={}/{}: {}", attempt + 1, retries + 1, ex.getMessage());
                if (attempt < retries) {
                    sleep(delay);
                }
            }
        }
        throw new AgentStartupException(EXIT_TEMPFAIL,
                "无法从主服务器获取判题配置：" + (last == null ? "未知错误" : last.getMessage()));
    }

    private boolean isAuthFailure(RuntimeException ex) {
        String message = ex.getMessage();
        return message != null && (message.contains("HTTP 401") || message.contains("HTTP 403"));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
