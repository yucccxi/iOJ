package me.iynotuo.judge.problem.mapper;

import me.iynotuo.judge.problem.entity.ProblemSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProblemSampleMapper {

    List<ProblemSample> findByProblemId(@Param("problemId") Long problemId);

    int deleteByProblemId(@Param("problemId") Long problemId);

    int insertBatch(@Param("samples") List<ProblemSample> samples);
}
