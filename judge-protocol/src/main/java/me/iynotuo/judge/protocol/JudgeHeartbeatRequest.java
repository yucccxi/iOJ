package me.iynotuo.judge.protocol;

/**
 * Heartbeat sent by a judge agent.
 *
 * <p>With a {@code claimToken} it renews the lease of that task. Without one it only reports that the
 * node is alive and how many tasks it is currently running.</p>
 *
 * @param nodeId          unique id of the judge agent instance
 * @param claimToken      task lease to renew, or {@code null} for a node-only heartbeat
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
