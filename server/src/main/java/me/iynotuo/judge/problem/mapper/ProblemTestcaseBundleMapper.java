package me.iynotuo.judge.problem.mapper;

import me.iynotuo.judge.problem.entity.ProblemTestcaseBundle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ProblemTestcaseBundleMapper {

    ProblemTestcaseBundle findByProblemId(@Param("problemId") Long problemId);

    /**
     * Creates the cache row without changing an existing row, so a hash can be written afterwards
     * with a version check.
     */
    int ensureRow(@Param("problemId") Long problemId,
                  @Param("testcaseVersion") Long testcaseVersion,
                  @Param("now") LocalDateTime now);

    /**
     * Stores the hash only when the test cases did not change since the caller read the version.
     */
    int updateHashIfVersion(@Param("problemId") Long problemId,
                            @Param("bundleSha256") String bundleSha256,
                            @Param("testcaseVersion") Long testcaseVersion,
                            @Param("now") LocalDateTime now);

    /**
     * Clears the cached hash and increments the version after test cases change.
     */
    int invalidate(@Param("problemId") Long problemId, @Param("now") LocalDateTime now);
}
