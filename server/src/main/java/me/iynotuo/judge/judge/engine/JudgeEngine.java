package me.iynotuo.judge.judge.engine;

import me.iynotuo.judge.judge.JudgeContext;
import me.iynotuo.judge.judge.JudgeOutcome;

/**
 * Judge engine abstraction.
 *
 * <p>The implementation is picked at startup by {@code ioj.judge.engine}:
 * {@code isolate} uses {@link IsolateJudgeEngine} and requires a working isolate binary, while
 * {@code local} uses {@link LocalProcessJudgeEngine} and runs programs directly on the host
 * (fallback for machines without a sandbox).</p>
 *
 * <p>An engine owns the "compile + execute one submission" concern only: it receives everything it
 * needs through {@link JudgeContext}, runs the test cases in order and reports raw per-case results.
 * Persistence, status aggregation and retry/cleanup policy stay in
 * {@link me.iynotuo.judge.judge.JudgeService}.</p>
 */
public interface JudgeEngine {

    /**
     * Compiles and runs one submission, returning the per-test-case results.
     *
     * @param context the full input of one judging run: the submission (source code and language),
     *                the problem (time/memory/output limits), the ordered test cases, the resolved
     *                language configuration and the optional custom checker source; never {@code null}
     * @return the judging outcome, never {@code null}. {@code cases} holds the test cases that were
     *         actually executed, in test-case order; it may be shorter than the test case list because
     *         judging stops at the first failing case. {@code compileMessage} is non-null only for a
     *         compilation error, {@code judgeMessage} only for an engine/system error, and
     *         {@code memoryUsedKb} may be {@code null} when the engine cannot measure memory
     */
    JudgeOutcome judge(JudgeContext context);
}
