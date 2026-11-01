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

    Tag findById(@Param("id") Long id);

    Tag findByName(@Param("name") String name);

    List<Tag> findByIds(@Param("ids") List<Long> ids);

    int insert(Tag tag);

    int updateName(@Param("id") Long id, @Param("name") String name);

    int deleteById(@Param("id") Long id);

    /**
     * Tags with how many local problems and external OJ links use them, for the admin page.
     */
    List<TagUsageRow> findAllWithUsage();

    /**
     * Total usage of one tag across local problems and external OJ links.
     */
    long countUsage(@Param("tagId") Long tagId);

    List<Tag> findByProblemId(@Param("problemId") Long problemId);

    List<ProblemTagRow> findByProblemIds(@Param("problemIds") List<Long> problemIds);

    int deleteProblemTags(@Param("problemId") Long problemId);

    int deleteProblemTagsByTagId(@Param("tagId") Long tagId);

    int insertProblemTags(@Param("problemId") Long problemId, @Param("tagIds") List<Long> tagIds);
}
