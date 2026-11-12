package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.protocol.JudgeTestCase;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes the test data bundle as a ZIP archive.
 *
 * <p>The archive intentionally contains one {@code testcases.json} entry instead of one entry per
 * test case: a single DEFLATE stream keeps the redundancy between test cases and therefore compresses
 * much better than many small entries.</p>
 */
@Component
public class JudgeTestcaseBundleWriter {

    /**
     * Name of the only entry in the archive.
     */
    public static final String ENTRY_NAME = "testcases.json";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * Streams the test cases into a ZIP archive.
     *
     * <p>The caller owns the output stream; this method finishes the ZIP central directory but does
     * not close the HTTP response. Jackson is given a non-closing wrapper so it cannot close the ZIP
     * stream before {@link ZipOutputStream#closeEntry()} runs.</p>
     *
     * @param out       response output stream
     * @param testcases test cases to serialize
     * @throws IOException when the archive cannot be written
     */
    public void write(OutputStream out, List<JudgeTestCase> testcases) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        ZipEntry entry = new ZipEntry(ENTRY_NAME);
        // A fixed timestamp keeps identical test data byte-for-byte reproducible for future caching or hashing.
        entry.setTime(0);
        zip.putNextEntry(entry);

        OutputStream nonClosing = new FilterOutputStream(zip) {
            @Override
            public void close() throws IOException {
                flush();
            }
        };
        JSON.writeValue(nonClosing, testcases);

        zip.closeEntry();
        zip.finish();
        zip.flush();
    }
}
