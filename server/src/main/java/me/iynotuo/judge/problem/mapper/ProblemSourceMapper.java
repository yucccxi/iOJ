package me.iynotuo.judge.problem.mapper;

import me.iynotuo.judge.problem.entity.ProblemSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Problem sources, maintained by administrators and referenced by {@code problems.source_id}.
 */
@Mapper
public interface ProblemSourceMapper {

    ProblemSource findById(@Param("id") Long id);

    ProblemSource findByName(@Param("name") String name);

    /**
     * Sources with how many local problems and external OJ links reference them, for the admin page.
     */
    List<SourceUsageRow> findAllWithUsage();

    int insert(ProblemSource source);

    int updateName(@Param("id") Long id, @Param("name") String name);

    int deleteById(@Param("id") Long id);

    /**
     * Total usage of one source across local problems and external OJ links.
     */
    long countUsage(@Param("id") Long id);
}
