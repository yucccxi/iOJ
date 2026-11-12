package me.iynotuo.judge.protocol;

/**
 * Response to a node heartbeat.
 *
 * <p>The agent derives its node heartbeat interval from this value: one third of
 * {@code offlineSeconds}, leaving room for two missed heartbeats before the server marks the node
 * offline.</p>
 *
 * @param offlineSeconds how long the server waits without a heartbeat before marking the node offline
 */
public record JudgeNodeHeartbeatResponse(int offlineSeconds) {
}
