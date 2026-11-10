package me.iynotuo.judge.protocol;

/**
 * Voluntary release of a claimed task, used during graceful shutdown.
 *
 * @param claimToken fence of the claim being released, or {@code null} to release every task owned by
 *                   the node
 * @param reason     optional explanation for logs
 */
public record JudgeReleaseRequest(String claimToken, String reason) {
}
