package me.iynotuo.judge.problem.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Cached SHA-256 of the ZIP bundle built from one problem's test cases.
 *
 * <p>A {@code null} hash means the cache must be recomputed. Test case mutations clear the hash and
 * increment {@code testcaseVersion} so a concurrent computation cannot store a stale value.</p>
 */
@Data
public class ProblemTestcaseBundle {

    private Long problemId;

    private String bundleSha256;

    private Long testcaseVersion;

    private LocalDateTime updatedAt;
}
