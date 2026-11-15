package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.protocol.JudgeTestCase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeTestcaseBundleWriterTest {

    private final JudgeTestcaseBundleWriter writer = new JudgeTestcaseBundleWriter();

    @Test
    void sha256MatchesTheWrittenBundle() throws Exception {
        List<JudgeTestCase> testcases = List.of(
                new JudgeTestCase(1, "1 2\n", "3\n"),
                new JudgeTestCase(2, "4 5\n", "9\n"));

        ByteArrayOutputStream bundle = new ByteArrayOutputStream();
        writer.write(bundle, testcases);
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bundle.toByteArray()));

        assertThat(writer.sha256(testcases)).isEqualTo(expected);
        assertThat(writer.sha256(testcases)).isEqualTo(writer.sha256(testcases));
    }
}
