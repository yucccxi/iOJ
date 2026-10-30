package me.iynotuo.judge.problem.mapper;

import me.iynotuo.judge.problem.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Tags and problem-tag links.
 */
@Mapper
public interface TagMapper {

    List<Tag> findAll();

    Tag findById(@Param("id") Long id);

    Tag findByName(@Param("name") String name);

    int insert(Tag tag);

    List<Tag> findByProblemId(@Param("problemId") Long problemId);

    List<ProblemTagRow> findByProblemIds(@Param("problemIds") List<Long> problemIds);

    int deleteProblemTags(@Param("problemId") Long problemId);

    int insertProblemTags(@Param("problemId") Long problemId, @Param("tagIds") List<Long> tagIds);
}
