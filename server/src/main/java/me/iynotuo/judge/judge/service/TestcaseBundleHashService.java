package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.problem.entity.ProblemTestcaseBundle;
import me.iynotuo.judge.problem.mapper.ProblemTestcaseBundleMapper;
import me.iynotuo.judge.protocol.JudgeTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Caches the SHA-256 of a problem's test data bundle in the database.
 *
 * <p>The first claim for a problem computes the hash and stores it. Test case mutations clear the
 * cached value and bump a version; a computation that started before the mutation will not write
 * its hash back.</p>
 */
@Service
public class TestcaseBundleHashService {

    private static final Logger log = LoggerFactory.getLogger(TestcaseBundleHashService.class);

    private final ProblemTestcaseBundleMapper bundleMapper;
    private final JudgeTestcaseBundleWriter bundleWriter;
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public TestcaseBundleHashService(ProblemTestcaseBundleMapper bundleMapper,
                                     JudgeTestcaseBundleWriter bundleWriter) {
        this.bundleMapper = bundleMapper;
        this.bundleWriter = bundleWriter;
    }

    /**
     * Returns the cached row, or {@code null} when this problem has never been hashed.
     */
    public ProblemTestcaseBundle find(Long problemId) {
        return bundleMapper.findByProblemId(problemId);
    }

    /**
     * Computes the bundle hash and stores it when the test cases still match {@code expectedVersion}.
     *
     * <p>The caller must pass the version it read before loading the test cases. If the version
     * changed in the meantime, the computed hash is returned for the current task but is not cached,
     * so the next claim recomputes it from the new test cases.</p>
     */
    @Transactional
    public String getOrCompute(Long problemId, List<JudgeTestCase> testcases, long expectedVersion)
            throws IOException {
        ReentrantLock lock = locks.computeIfAbsent(problemId, id -> new ReentrantLock());
        lock.lock();
        try {
            ProblemTestcaseBundle current = bundleMapper.findByProblemId(problemId);
            if (current != null && current.getBundleSha256() != null) {
                return current.getBundleSha256();
            }

            long currentVersion = current == null ? 0L : current.getTestcaseVersion();

            String hash = bundleWriter.sha256(testcases);
            if (currentVersion != expectedVersion) {
                log.warn("测试数据在计算 bundle 哈希期间发生变化，跳过缓存 problemId={} expectedVersion={} currentVersion={}",
                        problemId, expectedVersion, currentVersion);
                return hash;
            }

            LocalDateTime now = LocalDateTime.now();
            bundleMapper.ensureRow(problemId, expectedVersion, now);
            int updated = bundleMapper.updateHashIfVersion(problemId, hash, expectedVersion, now);
            if (updated == 0) {
                log.warn("测试数据 bundle 哈希未写入缓存，版本已变化 problemId={} version={}",
                        problemId, expectedVersion);
            }
            return hash;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears the cached hash after the problem's test cases change.
     */
    @Transactional
    public void invalidate(Long problemId) {
        bundleMapper.invalidate(problemId, LocalDateTime.now());
    }
}
