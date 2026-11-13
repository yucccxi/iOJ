package me.iynotuo.judge.protocol;

/**
 * Response to a task heartbeat.
 *
 * @param leaseSeconds how long the task lease is now valid, or 0 when the claim is invalid
 * @param cancel       {@code true} when the agent should abandon the task because the claim was
 *                     reassigned or is no longer current
 */
public record JudgeHeartbeatResponse(int leaseSeconds, boolean cancel) {
}
