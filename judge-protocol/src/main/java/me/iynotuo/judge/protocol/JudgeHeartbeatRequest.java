package me.iynotuo.judge.protocol;

/**
 * Task heartbeat sent by a judge agent.
 *
 * <p>Renews the lease of one claimed task. A blank token is rejected with {@code cancel=true}; node
 * liveness is reported separately through {@link JudgeNodeInfo} on the node heartbeat endpoint.</p>
 *
 * @param nodeId          unique id of the judge agent instance
 * @param claimToken      task lease to renew; must match the current claim
 * @param runningTasks    number of tasks currently executing on the agent
 * @param protocolVersion protocol version understood by the agent
 */
public record JudgeHeartbeatRequest(String nodeId,
                                    String claimToken,
                                    Integer runningTasks,
                                    Integer protocolVersion) {

    public JudgeHeartbeatRequest {
        runningTasks = runningTasks == null ? 0 : runningTasks;
        protocolVersion = protocolVersion == null ? ProtocolVersion.CURRENT : protocolVersion;
    }
}
