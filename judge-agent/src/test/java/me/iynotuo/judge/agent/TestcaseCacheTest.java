package me.iynotuo.judge.agent;

import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeLanguageConfig;
import me.iynotuo.judge.protocol.JudgeNodeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestcaseCacheTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @TempDir
    Path tempDir;

    @Test
    void downloadsOnceAndThenReusesTheCache() throws Exception {
        List<JudgeTestCase> cases = List.of(new JudgeTestCase(1, "1 2\n", "3\n"));
        byte[] bundle = zip(cases);
        String hash = sha256(bundle);
        FakeClient client = new FakeClient(bundle);
        TestcaseCache cache = cache(client);

        assertThat(cache.loadOrFetch(task(7L, hash))).isEqualTo(cases);
        assertThat(cache.loadOrFetch(task(7L, hash))).isEqualTo(cases);

        assertThat(client.downloads).isEqualTo(1);
        assertThat(Files.readString(tempDir.resolve("7").resolve("bundle.sha256")).trim())
                .isEqualTo(hash);
    }

    @Test
    void downloadsAgainWhenTheHashChanges() throws Exception {
        byte[] first = zip(List.of(new JudgeTestCase(1, "a\n", "b\n")));
        byte[] second = zip(List.of(new JudgeTestCase(1, "c\n", "d\n")));
        FakeClient client = new FakeClient(first);
        TestcaseCache cache = cache(client);

        cache.loadOrFetch(task(7L, sha256(first)));
        client.bundle = second;
        cache.loadOrFetch(task(7L, sha256(second)));

        assertThat(client.downloads).isEqualTo(2);
        assertThat(Files.readString(tempDir.resolve("7").resolve("bundle.sha256")).trim())
                .isEqualTo(sha256(second));
    }

    @Test
    void evictsTheOldestProblemWhenCapacityIsExceeded() throws Exception {
        byte[] first = zip(List.of(new JudgeTestCase(1, "a\n", "b\n")));
        byte[] second = zip(List.of(new JudgeTestCase(1, "c\n", "d\n")));
        AgentProperties properties = properties(1L);
        FakeClient client = new FakeClient(first);
        TestcaseCache cache = new TestcaseCache(properties, client, json);

        cache.loadOrFetch(task(1L, sha256(first)));
        Files.setLastModifiedTime(tempDir.resolve("1").resolve("bundle.zip"),
                java.nio.file.attribute.FileTime.from(Instant.parse("2020-01-01T00:00:00Z")));
        client.bundle = second;
        cache.loadOrFetch(task(2L, sha256(second)));

        assertThat(Files.exists(tempDir.resolve("1"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("2").resolve("bundle.zip"))).isTrue();
    }

    @Test
    void rejectsABundleWhoseHashDoesNotMatch() throws Exception {
        byte[] bundle = zip(List.of(new JudgeTestCase(1, "a\n", "b\n")));
        FakeClient client = new FakeClient(bundle);
        TestcaseCache cache = cache(client);

        assertThatThrownBy(() -> cache.loadOrFetch(task(7L, "0".repeat(64))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("哈希不一致");
        assertThat(Files.exists(tempDir.resolve("7").resolve("bundle.zip"))).isFalse();
    }

    @Test
    void reDownloadsWhenTheCachedBundleIsCorrupt() throws Exception {
        byte[] bundle = zip(List.of(new JudgeTestCase(1, "a\n", "b\n")));
        FakeClient client = new FakeClient(bundle);
        TestcaseCache cache = cache(client);

        cache.loadOrFetch(task(7L, sha256(bundle)));
        Files.writeString(tempDir.resolve("7").resolve("bundle.zip"), "not a zip", StandardCharsets.UTF_8);

        assertThat(cache.loadOrFetch(task(7L, sha256(bundle)))).hasSize(1);
        assertThat(client.downloads).isEqualTo(2);
    }

    @Test
    void bypassesTheCacheWhenDisabled() throws Exception {
        byte[] bundle = zip(List.of(new JudgeTestCase(1, "a\n", "b\n")));
        AgentProperties properties = properties(0);
        properties.getTestcaseCache().setEnabled(false);
        FakeClient client = new FakeClient(bundle);
        TestcaseCache cache = new TestcaseCache(properties, client, json);

        assertThat(cache.loadOrFetch(task(7L, sha256(bundle)))).hasSize(1);
        assertThat(client.downloads).isZero();
        assertThat(client.fetches).isEqualTo(1);
    }

    private TestcaseCache cache(FakeClient client) {
        return new TestcaseCache(properties(2L * 1024 * 1024), client, json);
    }

    private AgentProperties properties(long maxBytes) {
        AgentProperties properties = new AgentProperties();
        properties.getTestcaseCache().setEnabled(true);
        properties.getTestcaseCache().setDirectory(tempDir.toString());
        properties.getTestcaseCache().setMaxBytes(maxBytes);
        return properties;
    }

    private JudgeTask task(long problemId, String hash) {
        return new JudgeTask(
                1L,
                1L,
                problemId,
                "claim-token",
                300,
                Language.JAVA,
                "public class Main {}",
                1000,
                256,
                new JudgeLanguageConfig("Main.java", "javac Main.java", "java Main", null,
                        2.0, 64, 256, false, List.of("javac -version")),
                null,
                hash);
    }

    private byte[] zip(List<JudgeTestCase> cases) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry entry = new ZipEntry(TestcaseZipReader.ENTRY_NAME);
            entry.setTime(0);
            zip.putNextEntry(entry);
            OutputStream nonClosing = new FilterOutputStream(zip) {
                @Override
                public void close() throws IOException {
                    flush();
                }
            };
            json.writeValue(nonClosing, cases);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static final class FakeClient implements JudgeAgentClient {

        private byte[] bundle;
        private int downloads;
        private int fetches;
        private final JsonMapper json = JsonMapper.builder().build();

        private FakeClient(byte[] bundle) {
            this.bundle = bundle;
        }

        @Override
        public JudgeBootstrapConfig fetchConfig() {
            return null;
        }

        @Override
        public List<JudgeTask> claim(int maxTasks, List<Language> languages) {
            return List.of();
        }

        @Override
        public List<JudgeTestCase> fetchTestcases(Long taskId, String claimToken) {
            fetches++;
            try {
                return TestcaseZipReader.read(new ByteArrayInputStream(bundle), json);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void downloadTestcaseBundle(Long taskId, String claimToken, Path target) {
            downloads++;
            try {
                Files.write(target, bundle);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public JudgeNodeHeartbeatResponse heartbeatNode(JudgeNodeInfo info) {
            return new JudgeNodeHeartbeatResponse(90);
        }

        @Override
        public JudgeHeartbeatResponse heartbeatTask(Long taskId, String claimToken) {
            return new JudgeHeartbeatResponse(300, false);
        }

        @Override
        public void reportResult(Long taskId, JudgeResult result) {
        }

        @Override
        public void release(Long taskId, String claimToken, String reason) {
        }
    }
}
