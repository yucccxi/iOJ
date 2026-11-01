package me.iynotuo.judge.judge.answer;

import me.iynotuo.judge.config.JudgeProperties;
import me.iynotuo.judge.judge.JudgeContext;
import me.iynotuo.judge.judge.sandbox.Sandbox;
import org.springframework.stereotype.Component;

/**
 * Chooses how answers are checked for one submission.
 *
 * <p>This is the only place that knows both the problem (does it ship a checker?) and the deployment
 * (may a checker run here?); judge engines simply ask for a checker, which keeps every engine free of
 * judging policy.</p>
 */
@Component
public class AnswerCheckerFactory {

    private final JudgeProperties properties;

    /**
     * Creates the factory.
     *
     * @param properties judge configuration; {@code ioj.judge.checker} decides whether custom checkers
     *                   are allowed at all. Never {@code null}
     */
    public AnswerCheckerFactory(JudgeProperties properties) {
        this.properties = properties;
    }

    /**
     * Builds the checker for one submission.
     *
     * @param context the judging run; its {@code checkerCode} tells whether the problem ships a checker
     * @param sandbox the sandbox that will execute the checker, if any
     * @return a text comparator, a program checker, or a disabled checker that reports
     *         {@code CHECKER_ERROR}; never {@code null}
     */
    public AnswerChecker create(JudgeContext context, Sandbox sandbox) {
        if (!context.hasCustomChecker()) {
            return new TextAnswerChecker();
        }
        if (!properties.getChecker().isEnabled()) {
            return new DisabledAnswerChecker("custom checkers are disabled on this judge");
        }
        if (!sandbox.supportsAuthorCode()) {
            return new DisabledAnswerChecker("custom checkers require the isolate engine");
        }
        return new ProgramAnswerChecker(sandbox, properties, context.checkerCode());
    }
}
