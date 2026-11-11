package me.iynotuo.judge.protocol;

import java.util.List;
import java.util.Map;

/**
 * Configuration fetched by a judge agent at startup.
 *
 * <p>The agent must verify every language in {@code requiredLanguages} before it registers with the
 * server. Judge agents are intentionally homogeneous: if the server supports a language, every agent
 * is expected to have the corresponding toolchain.</p>
 *
 * @param protocolVersion   internal protocol version
 * @param configVersion     hash of the language/checker configuration, used for diagnostics
 * @param requiredLanguages languages the agent must support
 * @param languages         language command snapshots keyed by language
 * @param checkerEnabled    whether custom checkers are enabled
 * @param checkerProbeCommands commands that verify the checker compiler
 */
public record JudgeBootstrapConfig(int protocolVersion,
                                   String configVersion,
                                   List<Language> requiredLanguages,
                                   Map<Language, JudgeLanguageConfig> languages,
                                   boolean checkerEnabled,
                                   List<String> checkerProbeCommands) {

    public JudgeBootstrapConfig {
        requiredLanguages = requiredLanguages == null ? List.of() : List.copyOf(requiredLanguages);
        languages = languages == null ? Map.of() : Map.copyOf(languages);
        checkerProbeCommands = checkerProbeCommands == null ? List.of() : List.copyOf(checkerProbeCommands);
    }
}
