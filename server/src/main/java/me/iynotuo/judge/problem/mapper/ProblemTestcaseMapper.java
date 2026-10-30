package me.iynotuo.judge.problem.mapper;

import me.iynotuo.judge.problem.dto.TestcaseSummaryVO;
import me.iynotuo.judge.problem.entity.ProblemTestcase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemTestcaseMapper {

    List<ProblemTestcase> findByProblemId(@Param("problemId") Long problemId);

    List<TestcaseSummaryVO> findSummaries(@Param("problemId") Long problemId);

    TestcaseSummaryVO findSummary(@Param("problemId") Long problemId, @Param("ordinal") Integer ordinal);

    ProblemTestcase findById(@Param("id") Long id);

    int countByProblemId(@Param("problemId") Long problemId);

    Integer findMaxOrdinal(@Param("problemId") Long problemId);

    int deleteByProblemId(@Param("problemId") Long problemId);

    int deleteById(@Param("problemId") Long problemId, @Param("id") Long id);

    int insertBatch(@Param("testcases") List<ProblemTestcase> testcases);
}
