package me.iynotuo.judge.protocol;

import java.util.List;

/**
 * Capabilities and current load of a judge agent.
 *
 * @param nodeId         unique id of the agent instance
 * @param hostname       host name, for operators
 * @param version        agent version
 * @param languages      languages the agent can judge
 * @param maxConcurrency maximum number of tasks the agent runs at once
 * @param runningTasks   number of tasks currently executing
 * @param isolateVersion isolate version reported by the host, or {@code null}
 */
public record JudgeNodeInfo(String nodeId,
                            String hostname,
                            String version,
                            List<Language> languages,
                            int maxConcurrency,
                            int runningTasks,
                            String isolateVersion) {

    public JudgeNodeInfo {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
