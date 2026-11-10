package me.iynotuo.judge.protocol;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the wire shape of the internal judge protocol.
 *
 * <p>Both the main server and remote judge agents depend on these records being serializable with
 * Jackson 3. A round trip catches accidental field renames or missing record components before an
 * agent is upgraded independently.</p>
 */
class JudgeProtocolTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void taskRoundTripsThroughJson() throws Exception {
        JudgeTask task = new JudgeTask(
                500L,
                500L,
                "claim-token",
                300,
                Language.JAVA,
                "public class Main {}",
                1000,
                256,
                new JudgeLanguageConfig("Main.java", "javac Main.java", "java Main", null, 2.0, 64, 256, false),
                null,
                List.of(new JudgeTestCase(1, "1 2\n", "3\n")),
                null,
                null);

        String encoded = json.writeValueAsString(task);
        JudgeTask decoded = json.readValue(encoded, JudgeTask.class);

        assertThat(decoded).isEqualTo(task);
    }

    @Test
    void resultRoundTripsThroughJson() throws Exception {
        JudgeResult result = new JudgeResult(
                "claim-token",
                SubmissionStatus.WRONG_ANSWER,
                12,
                1024,
                null,
                null,
                List.of(new JudgeCaseResult(1, SubmissionStatus.WRONG_ANSWER, 12, 1024, "expected 3")));

        String encoded = json.writeValueAsString(result);
        JudgeResult decoded = json.readValue(encoded, JudgeResult.class);

        assertThat(decoded).isEqualTo(result);
    }

    @Test
    void nullListsBecomeEmptyLists() {
        assertThat(new JudgeTaskClaimResponse(null).tasks()).isEmpty();
        assertThat(new JudgeResult(null, SubmissionStatus.SYSTEM_ERROR, null, null, null, "boom", null).cases())
                .isEmpty();
    }

    @Test
    void protocolVersionIsPositive() {
        assertThat(ProtocolVersion.CURRENT).isPositive();
    }
}
