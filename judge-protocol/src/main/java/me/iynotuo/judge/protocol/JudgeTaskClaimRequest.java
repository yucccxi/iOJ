package me.iynotuo.judge.protocol;

import java.util.List;

/**
 * Request sent by a judge agent to claim work.
 *
 * @param nodeId         unique id of the judge agent instance
 * @param maxTasks       maximum number of tasks the agent wants; one is a safe default
 * @param languages      languages the agent can judge
 * @param protocolVersion protocol version understood by the agent
 */
public record JudgeTaskClaimRequest(String nodeId,
                                    Integer maxTasks,
                                    List<Language> languages,
                                    Integer protocolVersion) {

    public JudgeTaskClaimRequest {
        languages = languages == null ? List.of() : List.copyOf(languages);
        maxTasks = maxTasks == null ? 1 : maxTasks;
        protocolVersion = protocolVersion == null ? ProtocolVersion.CURRENT : protocolVersion;
    }
}
