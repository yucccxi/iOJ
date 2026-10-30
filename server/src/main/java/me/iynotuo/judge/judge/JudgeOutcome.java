package me.iynotuo.judge.judge;

import me.iynotuo.judge.common.enums.SubmissionStatus;

import java.util.List;

/**
 * 一次判题的输出结果。
 *
 * @param status         整体状态（第一个未通过的测试点状态，或 ACCEPTED）
 * @param compileMessage 编译错误信息，非编译错误时为 null
 * @param judgeMessage   系统错误信息（如测试点缺失），不包含测试数据
 * @param cases          逐个测试点结果（fail-fast，失败后不再继续）
 * @param timeUsedMs     所有已执行测试点中的最大耗时
 * @param memoryUsedKb   内存峰值；本地引擎无法精确统计，一般返回 null
 */
public record JudgeOutcome(SubmissionStatus status,
                           String compileMessage,
                           String judgeMessage,
                           List<CaseResult> cases,
                           Integer timeUsedMs,
                           Integer memoryUsedKb) {

    public record CaseResult(Integer ordinal, SubmissionStatus status, Integer timeUsedMs,
                             Integer memoryUsedKb, String message) {
    }

    public static JudgeOutcome systemError(String message) {
        return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, null, message, List.of(), null, null);
    }

    public static JudgeOutcome compilationError(String message) {
        return new JudgeOutcome(SubmissionStatus.COMPILATION_ERROR, message, null, List.of(), null, null);
    }
}
