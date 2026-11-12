package me.iynotuo.judge.agent;

import me.iynotuo.judge.core.JudgeContext;
import me.iynotuo.judge.core.JudgeOutcome;
import me.iynotuo.judge.protocol.JudgeLanguageConfig;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;
import me.iynotuo.judge.protocol.SubmissionStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the protocol-to-core mapping used by the remote agent.
 */
class AgentTaskMapperTest {

    private final AgentTaskMapper mapper = new AgentTaskMapper();

    @Test
    void mapsTaskIntoJudgeContext() {
        JudgeTask task = task();
        JudgeContext context = mapper.toContext(task);

        assertThat(context.submission().id()).isEqualTo(500L);
        assertThat(context.submission().language()).isEqualTo(Language.JAVA);
        assertThat(context.problem().timeLimitMs()).isEqualTo(1000);
        assertThat(context.problem().memoryLimitMb()).isEqualTo(256);
        assertThat(context.testcases()).hasSize(1);
        assertThat(context.language().getSourceFile()).isEqualTo("Main.java");
        assertThat(context.language().getCompileCommand()).isEqualTo("javac Main.java");
        assertThat(context.hasCustomChecker()).isFalse();
    }

    @Test
    void mapsOutcomeIntoResult() {
        JudgeTask task = task();
        JudgeOutcome outcome = new JudgeOutcome(SubmissionStatus.ACCEPTED, null, null,
                List.of(new JudgeOutcome.CaseResult(1, SubmissionStatus.ACCEPTED, 5, 1024, null)),
                5, 1024);

        JudgeResult result = mapper.toResult(task, outcome);

        assertThat(result.claimToken()).isEqualTo("claim-token");
        assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(result.cases()).hasSize(1);
        assertThat(result.cases().get(0).ordinal()).isEqualTo(1);
    }

    private JudgeTask task() {
        return new JudgeTask(
                500L,
                500L,
                "claim-token",
                300,
                Language.JAVA,
                "public class Main {}",
                1000,
                256,
                new JudgeLanguageConfig("Main.java", "javac Main.java", "java Main", null, 2.0, 64, 256,
                        false, List.of("javac -version")),
                null,
                List.of(new JudgeTestCase(1, "1 2\n", "3\n")),
                null,
                null);
    }
}
