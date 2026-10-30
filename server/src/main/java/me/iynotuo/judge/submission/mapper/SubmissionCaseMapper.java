package me.iynotuo.judge.submission.mapper;

import me.iynotuo.judge.submission.entity.SubmissionCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubmissionCaseMapper {

    int insertBatch(@Param("cases") List<SubmissionCase> cases);

    List<SubmissionCase> findBySubmissionId(@Param("submissionId") Long submissionId);

    int deleteBySubmissionId(@Param("submissionId") Long submissionId);
}
