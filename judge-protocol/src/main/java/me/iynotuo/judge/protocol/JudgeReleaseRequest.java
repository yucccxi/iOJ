package me.iynotuo.judge.protocol;

/**
 * Release of one claimed task. Used when the agent cannot execute a claimed task and during a
 * graceful shutdown.
 *
 * @param claimToken fence of the claim being released; a blank value is a no-op, not a node-wide
 *                   release
 * @param reason     optional explanation for logs
 */
public record JudgeReleaseRequest(String claimToken, String reason) {
}
