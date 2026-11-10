package me.iynotuo.judge.protocol;

/**
 * Response to a judge heartbeat.
 *
 * @param leaseSeconds how long the task lease is now valid, or 0 for a node-only heartbeat
 * @param cancel       {@code true} when the agent should abandon the task, for example because an
 *                     administrator rejudged it or the task was reassigned
 */
public record JudgeHeartbeatResponse(int leaseSeconds, boolean cancel) {
}
