package me.iynotuo.judge.problem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Unified catalog query: local problems and external OJ links in one pageable, filterable list.
 */
@Mapper
public interface CatalogMapper {

    List<CatalogRow> findPage(@Param("criteria") CatalogCriteria criteria);

    long count(@Param("criteria") CatalogCriteria criteria);
}
