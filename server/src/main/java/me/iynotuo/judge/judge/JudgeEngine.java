package me.iynotuo.judge.judge;

/**
 * 判题引擎抽象。当前提供本地进程实现 {@link LocalProcessJudgeEngine}，
 * 生产环境建议实现一个基于 Docker / nsjail 的版本（见 docs/server/03-judge.md）。
 */
public interface JudgeEngine {

    JudgeOutcome judge(JudgeContext context);
}
