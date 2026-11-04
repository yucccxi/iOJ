package me.iynotuo.judge.problem.mapper;

import me.iynotuo.judge.problem.entity.ProblemScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Problem scopes, maintained by administrators and referenced by {@code problems.scope_id}.
 */
@Mapper
public interface ProblemScopeMapper {

    ProblemScope findById(@Param("id") Long id);

    ProblemScope findByName(@Param("name") String name);

    /**
     * Scopes with how many local problems and external OJ links reference them, for the admin page.
     */
    List<ScopeUsageRow> findAllWithUsage();

    int insert(ProblemScope scope);

    int updateName(@Param("id") Long id, @Param("name") String name);

    int deleteById(@Param("id") Long id);

    /**
     * Total usage of one scope across local problems and external OJ links.
     */
    long countUsage(@Param("id") Long id);
}
