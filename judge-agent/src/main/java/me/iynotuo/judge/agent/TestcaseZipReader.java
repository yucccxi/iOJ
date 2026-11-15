package me.iynotuo.judge.agent;

import me.iynotuo.judge.protocol.JudgeTestCase;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the single {@code testcases.json} entry from a test data ZIP bundle.
 */
final class TestcaseZipReader {

    static final String ENTRY_NAME = "testcases.json";

    private TestcaseZipReader() {
    }

    static List<JudgeTestCase> read(Path file, JsonMapper json) throws IOException {
        try (InputStream body = Files.newInputStream(file)) {
            return read(body, json);
        }
    }

    static List<JudgeTestCase> read(InputStream body, JsonMapper json) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(body)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (ENTRY_NAME.equals(entry.getName())) {
                    List<JudgeTestCase> testcases = json.readValue(zip,
                            new TypeReference<List<JudgeTestCase>>() {
                            });
                    return testcases == null ? List.of() : testcases;
                }
            }
        }
        throw new IOException("测试数据 bundle 中缺少 " + ENTRY_NAME);
    }
}
