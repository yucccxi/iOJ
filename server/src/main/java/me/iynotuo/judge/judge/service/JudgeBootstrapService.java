package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.core.config.JudgeProperties;
import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeLanguageConfig;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.ProtocolVersion;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Builds the startup configuration sent to a judge agent.
 *
 * <p>All agents are expected to be homogeneous: {@code requiredLanguages} is the full set of languages
 * configured on the server.</p>
 */
@Service
public class JudgeBootstrapService {

    private final JudgeProperties judgeProperties;

    public JudgeBootstrapService(JudgeProperties judgeProperties) {
        this.judgeProperties = judgeProperties;
    }

    public JudgeBootstrapConfig bootstrapConfig() {
        Map<Language, JudgeLanguageConfig> languages = new EnumMap<>(Language.class);
        List<Language> required = new ArrayList<>();
        for (Map.Entry<Language, JudgeProperties.LanguageConfig> entry : judgeProperties.getLanguages().entrySet()) {
            Language language = entry.getKey();
            JudgeProperties.LanguageConfig source = entry.getValue();
            required.add(language);
            languages.put(language, new JudgeLanguageConfig(
                    source.getSourceFile(),
                    source.getCompileCommand(),
                    source.getRunCommand(),
                    source.getRunWrapper(),
                    source.getTimeMultiplier(),
                    source.getIsolateProcesses(),
                    source.getIsolateMemoryExtraMb(),
                    source.isCopySourceToRun(),
                    source.getProbeCommands()));
        }
        boolean checkerEnabled = judgeProperties.getChecker().isEnabled();
        List<String> checkerProbes = checkerEnabled
                ? judgeProperties.getChecker().getProbeCommands() : List.of();
        return new JudgeBootstrapConfig(
                ProtocolVersion.CURRENT,
                configHash(required, languages, checkerEnabled, checkerProbes),
                required,
                languages,
                checkerEnabled,
                checkerProbes);
    }

    private String configHash(List<Language> required,
                              Map<Language, JudgeLanguageConfig> languages,
                              boolean checkerEnabled,
                              List<String> checkerProbes) {
        StringBuilder builder = new StringBuilder("protocol=").append(ProtocolVersion.CURRENT);
        for (Language language : required) {
            JudgeLanguageConfig config = languages.get(language);
            if (config == null) {
                continue;
            }
            builder.append('|').append(language.name())
                    .append('|').append(config.sourceFile())
                    .append('|').append(config.compileCommand())
                    .append('|').append(config.runCommand())
                    .append('|').append(config.runWrapper())
                    .append('|').append(config.timeMultiplier())
                    .append('|').append(config.isolateProcesses())
                    .append('|').append(config.isolateMemoryExtraMb())
                    .append('|').append(config.copySourceToRun())
                    .append('|').append(String.join(",", config.probeCommands()));
        }
        builder.append("|checker=").append(checkerEnabled)
                .append('|').append(String.join(",", checkerProbes));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
