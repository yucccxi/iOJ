package me.iynotuo.judge.core.engine;

import me.iynotuo.judge.core.JudgeContext;
import me.iynotuo.judge.core.JudgeOutcome;

/**
 * Judge engine abstraction.
 *
 * <p>The implementation is picked at startup by {@code ioj.judge.engine}:
 * {@code isolate} uses {@link IsolateJudgeEngine} and requires a working isolate binary, while
 * {@code local} uses {@link LocalProcessJudgeEngine} and runs programs directly on the host. The
 * local engine is intended for the server's local mode (tests/development); an agent still requires
 * isolate during bootstrap even when it selects {@code local}.</p>
 *
 * <p>An engine owns the "compile + execute one submission" concern: it receives everything it needs
 * through {@link JudgeContext}, runs test cases in order, stops at the first failure and aggregates
 * the overall status. Persistence and lease retry/cleanup live in the server and in
 * {@code AgentRunner}.</p>
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
