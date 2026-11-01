package me.iynotuo.judge.problem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Links between external OJ problems and tags (the external counterpart of {@code problem_tags}).
 */
@Mapper
public interface ExternalProblemTagMapper {

    List<ExternalProblemTagRow> findByExternalProblemIds(@Param("externalProblemIds") List<Long> externalProblemIds);

    int deleteByTagId(@Param("tagId") Long tagId);
}
