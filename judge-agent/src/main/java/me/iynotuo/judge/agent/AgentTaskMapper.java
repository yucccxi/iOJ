package me.iynotuo.judge.agent;

import me.iynotuo.judge.core.JudgeContext;
import me.iynotuo.judge.core.JudgeOutcome;
import me.iynotuo.judge.core.JudgeProblem;
import me.iynotuo.judge.core.JudgeSubmission;
import me.iynotuo.judge.core.JudgeTestcase;
import me.iynotuo.judge.core.config.JudgeProperties;
import me.iynotuo.judge.protocol.JudgeCaseResult;
import me.iynotuo.judge.protocol.JudgeLanguageConfig;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTestCase;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts protocol objects into the judge-core context and back.
 */
@Component
public class AgentTaskMapper {

    /**
     * Builds the execution context for one claimed task.
     */
    public JudgeContext toContext(JudgeTask task) {
        return toContext(task, task.testcases());
    }

    /**
     * Builds the execution context with test data supplied separately (bundle mode).
     */
    public JudgeContext toContext(JudgeTask task, List<JudgeTestCase> testcases) {
        if (testcases == null || testcases.isEmpty()) {
            throw new IllegalStateException("任务没有测试数据");
        }
        List<JudgeTestcase> judgeTestcases = testcases.stream()
                .map(item -> new JudgeTestcase(item.ordinal(), item.input(), item.output()))
                .toList();
        JudgeProperties.LanguageConfig language = toLanguageConfig(task.languageConfig());
        return new JudgeContext(
                new JudgeSubmission(task.submissionId(), task.code(), task.language()),
                new JudgeProblem(task.submissionId(), task.timeLimitMs(), task.memoryLimitMb()),
                judgeTestcases,
                language,
                task.checkerCode());
    }

    /**
     * Converts the engine outcome into the protocol result.
     */
    public JudgeResult toResult(JudgeTask task, JudgeOutcome outcome) {
        List<JudgeCaseResult> cases = outcome.cases().stream()
                .map(item -> new JudgeCaseResult(item.ordinal(), item.status(),
                        item.timeUsedMs(), item.memoryUsedKb(), item.message()))
                .toList();
        return new JudgeResult(task.claimToken(), outcome.status(), outcome.timeUsedMs(),
                outcome.memoryUsedKb(), outcome.compileMessage(), outcome.judgeMessage(), cases);
    }

    /**
     * Builds a SYSTEM_ERROR result for an agent-side failure before the engine could return one.
     */
    public JudgeResult systemError(JudgeTask task, String message) {
        return new JudgeResult(task.claimToken(),
                me.iynotuo.judge.protocol.SubmissionStatus.SYSTEM_ERROR,
                null, null, null, message, List.of());
    }

    private JudgeProperties.LanguageConfig toLanguageConfig(JudgeLanguageConfig source) {
        JudgeProperties.LanguageConfig target = new JudgeProperties.LanguageConfig();
        target.setSourceFile(source.sourceFile());
        target.setCompileCommand(source.compileCommand());
        target.setRunCommand(source.runCommand());
        target.setRunWrapper(source.runWrapper());
        target.setTimeMultiplier(source.timeMultiplier());
        target.setIsolateProcesses(source.isolateProcesses());
        target.setIsolateMemoryExtraMb(source.isolateMemoryExtraMb());
        target.setCopySourceToRun(source.copySourceToRun());
        target.setProbeCommands(source.probeCommands());
        return target;
    }
}
