package me.iynotuo.judge.agent;

import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Caches downloaded test data bundles under {@code directory/problemId}.
 *
 * <p>The server sends the SHA-256 of each ZIP bundle. A bundle is downloaded only when the local
 * {@code bundle.sha256} is missing or differs from that value. Downloads are written to temporary
 * files, verified and then moved into place atomically, so a crash cannot leave a partial bundle
 * that looks current.</p>
 */
@Component
public class TestcaseCache {

    private static final Logger log = LoggerFactory.getLogger(TestcaseCache.class);

    private static final String BUNDLE_FILE = "bundle.zip";
    private static final String HASH_FILE = "bundle.sha256";

    private final AgentProperties properties;
    private final JudgeAgentClient client;
    private final JsonMapper json;
    private final Path root;
    private final boolean cacheUsable;
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Object evictionLock = new Object();

    public TestcaseCache(AgentProperties properties, JudgeAgentClient client, JsonMapper json) {
        this.properties = properties;
        this.client = client;
        this.json = json;
        String configured = properties.getTestcaseCache().getDirectory();
        this.root = Path.of(configured == null || configured.isBlank()
                ? "./cache/testcases" : configured).toAbsolutePath().normalize();
        boolean usable = false;
        if (properties.getTestcaseCache().isEnabled()) {
            try {
                createDirectory(root);
                usable = true;
            } catch (IllegalStateException ex) {
                log.warn("测试数据缓存不可用，将直接下载: {}", ex.getMessage());
            }
        }
        this.cacheUsable = usable;
    }

    /**
     * Returns the test cases from the cache or downloads them when the bundle is missing or stale.
     *
     * @param task claimed task whose bundle should be loaded
     * @return the test cases stored in the bundle
     * @throws Exception when the bundle cannot be downloaded, verified or parsed
     */
    public List<JudgeTestCase> loadOrFetch(JudgeTask task) throws Exception {
        String expected = normalizeHash(task.bundleSha256());
        if (!cacheUsable
                || !properties.getTestcaseCache().isEnabled()
                || task.problemId() == null
                || task.testcaseBundleUrl() == null
                || !isValidHash(expected)) {
            return client.fetchTestcases(task.taskId(), task.claimToken());
        }

        Path problemDir = root.resolve(Long.toString(task.problemId()));
        Path bundle = problemDir.resolve(BUNDLE_FILE);
        Path hashFile = problemDir.resolve(HASH_FILE);
        ReentrantLock lock = locks.computeIfAbsent(task.problemId(), id -> new ReentrantLock());
        lock.lock();
        try {
            if (Files.isRegularFile(bundle) && hashMatches(hashFile, expected)) {
                try {
                    List<JudgeTestCase> cached = TestcaseZipReader.read(bundle, json);
                    touch(bundle);
                    log.debug("测试数据缓存命中 problemId={} sha256={}", task.problemId(), prefix(expected));
                    return cached;
                } catch (IOException ex) {
                    log.warn("测试数据缓存无法解析，重新下载 problemId={}: {}",
                            task.problemId(), ex.getMessage());
                    deleteRecursively(problemDir);
                }
            }
            downloadAndStore(task, problemDir, bundle, hashFile, expected);
            return TestcaseZipReader.read(bundle, json);
        } finally {
            lock.unlock();
        }
    }

    private void downloadAndStore(JudgeTask task, Path problemDir, Path bundle, Path hashFile,
                                  String expected) throws Exception {
        createDirectory(problemDir);
        Path tempBundle = Files.createTempFile(problemDir, "bundle-", ".tmp");
        Path tempHash = null;
        try {
            setPermissions(tempBundle, "rw-------");
            client.downloadTestcaseBundle(task.taskId(), task.claimToken(), tempBundle);
            String actual = sha256(tempBundle);
            if (!expected.equals(actual)) {
                throw new IllegalStateException("测试数据 bundle 哈希不一致: expected="
                        + prefix(expected) + " actual=" + prefix(actual));
            }
            moveAtomically(tempBundle, bundle);
            tempHash = Files.createTempFile(problemDir, "sha-", ".tmp");
            setPermissions(tempHash, "rw-------");
            Files.writeString(tempHash, expected + System.lineSeparator(), StandardCharsets.UTF_8);
            moveAtomically(tempHash, hashFile);
            touch(bundle);
            log.info("测试数据已缓存 problemId={} sha256={}", task.problemId(), prefix(expected));
            evictIfNeeded(problemDir);
        } finally {
            Files.deleteIfExists(tempBundle);
            if (tempHash != null) {
                Files.deleteIfExists(tempHash);
            }
        }
    }

    private void evictIfNeeded(Path keepDirectory) {
        long maxBytes = properties.getTestcaseCache().getMaxBytes();
        if (maxBytes <= 0) {
            return;
        }
        synchronized (evictionLock) {
            List<CacheEntry> entries = new ArrayList<>();
            long total = 0;
            try (Stream<Path> children = Files.list(root)) {
                for (Path dir : children.toList()) {
                    if (!Files.isDirectory(dir)) {
                        continue;
                    }
                    Path bundle = dir.resolve(BUNDLE_FILE);
                    if (!Files.isRegularFile(bundle)) {
                        continue;
                    }
                    long size = Files.size(bundle);
                    long modified = Files.getLastModifiedTime(bundle).toMillis();
                    entries.add(new CacheEntry(dir, size, modified));
                    total += size;
                }
            } catch (IOException ex) {
                log.warn("读取测试数据缓存目录失败: {}", ex.getMessage());
                return;
            }
            if (total <= maxBytes) {
                return;
            }
            entries.sort(Comparator.comparingLong(CacheEntry::modified));
            for (CacheEntry entry : entries) {
                if (total <= maxBytes) {
                    break;
                }
                if (entry.directory().equals(keepDirectory)) {
                    continue;
                }
                Long problemId = parseProblemId(entry.directory());
                if (problemId == null) {
                    continue;
                }
                ReentrantLock lock = locks.computeIfAbsent(problemId, id -> new ReentrantLock());
                if (!lock.tryLock()) {
                    continue;
                }
                try {
                    if (deleteRecursively(entry.directory())) {
                        total -= entry.size();
                        log.info("淘汰测试数据缓存 problemId={} bytes={}", problemId, entry.size());
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private static Long parseProblemId(Path directory) {
        try {
            return Long.valueOf(directory.getFileName().toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (digestIn.read(buffer) >= 0) {
                // Drain the stream into the digest.
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean hashMatches(Path hashFile, String expected) {
        if (!Files.isRegularFile(hashFile)) {
            return false;
        }
        try {
            return Files.readString(hashFile, StandardCharsets.UTF_8).trim().equalsIgnoreCase(expected);
        } catch (IOException ex) {
            return false;
        }
    }

    private static String normalizeHash(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidHash(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    private static String prefix(String hash) {
        return hash == null ? "null" : hash.substring(0, Math.min(12, hash.length()));
    }

    private static void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
        } catch (IOException ignored) {
            // LRU ordering is best effort.
        }
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            setPermissions(directory, "rwx------");
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建测试数据缓存目录: " + directory, ex);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setPermissions(Path path, String permissions) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX file systems keep their defaults.
        }
    }

    private static boolean deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort cleanup; the next eviction can retry.
                }
            });
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private record CacheEntry(Path directory, long size, long modified) {
    }
}
