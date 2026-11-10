package me.iynotuo.judge.core;

/**
 * The problem limits a judge engine needs, decoupled from the database entity.
 *
 * @param id            problem id, used in logs
 * @param timeLimitMs   base time limit in milliseconds
 * @param memoryLimitMb memory limit in megabytes
 */
public record JudgeProblem(Long id, int timeLimitMs, int memoryLimitMb) {
}
