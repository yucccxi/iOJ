package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.CatalogSort;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.SortOrder;
import me.iynotuo.judge.common.enums.TagMatchMode;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.problem.dto.CatalogItemVO;
import me.iynotuo.judge.problem.dto.TagVO;
import me.iynotuo.judge.problem.mapper.CatalogCriteria;
import me.iynotuo.judge.problem.mapper.CatalogMapper;
import me.iynotuo.judge.problem.mapper.CatalogRow;
import me.iynotuo.judge.problem.mapper.ExternalProblemTagMapper;
import me.iynotuo.judge.problem.mapper.ExternalProblemTagRow;
import me.iynotuo.judge.problem.mapper.ProblemScopeMapper;
import me.iynotuo.judge.problem.mapper.ProblemTagRow;
import me.iynotuo.judge.problem.mapper.TagMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Unified search over local problems and external OJ links.
 *
 * <p>The two kinds live in different tables, so the SQL is a UNION ALL with a shared column set
 * ({@code kind}, {@code ref_id}, {@code title}, {@code rating}, {@code scope_id}, {@code url}).
 * Tags are loaded per kind with at most two queries, so a page of N items never costs N queries.</p>
 */
@Service
public class CatalogService {

    private static final String KIND_LOCAL = "LOCAL";
    private static final String KIND_EXTERNAL = "EXTERNAL";

    private final CatalogMapper catalogMapper;
    private final TagMapper tagMapper;
    private final ExternalProblemTagMapper externalProblemTagMapper;
    private final ProblemScopeMapper problemScopeMapper;

    public CatalogService(CatalogMapper catalogMapper,
                          TagMapper tagMapper,
                          ExternalProblemTagMapper externalProblemTagMapper,
                          ProblemScopeMapper problemScopeMapper) {
        this.catalogMapper = catalogMapper;
        this.tagMapper = tagMapper;
        this.externalProblemTagMapper = externalProblemTagMapper;
        this.problemScopeMapper = problemScopeMapper;
    }

    /**
     * Searches both problem kinds with the given filters.
     *
     * @param keyword    title keyword (both kinds), blank means no filter
     * @param difficulties coarse difficulty filter; multiple values are OR-ed and apply to both kinds
     * @param scopeIds    scope ids to match (repeatable, OR-ed); null/empty means every scope
     * @param tagIds      tag ids to match (both kinds); null/empty means no tag filter
     * @param tagMatch    {@code ANY} (default) or {@code ALL}; only relevant when tagIds is set
     * @param rating      exact rating match, optional
     * @param minRating   inclusive lower bound, optional
     * @param maxRating   inclusive upper bound, optional
     * @param sort        sort key; null means {@code DEFAULT}. {@code RATING} requires exactly one
     *                    {@code scope} because ratings are not comparable across scopes
     * @param order       sort direction; null means {@code DESC}
     * @param query       pagination
     * @return one page mixing both kinds, sorted by the requested key
     */
    public PageResult<CatalogItemVO> page(String keyword, List<Difficulty> difficulties,
                                          List<Long> scopeIds, List<Long> tagIds, TagMatchMode tagMatch,
                                          Integer rating, Integer minRating, Integer maxRating,
                                          CatalogSort sort, SortOrder order, PageQuery query) {
        CatalogSort effectiveSort = sort == null ? CatalogSort.DEFAULT : sort;
        List<Long> normalizedScopeIds = normalizeScopeIds(scopeIds);
        // A scope id that does not exist is a request-parameter error, not an empty result.
        if (!normalizedScopeIds.isEmpty()
                && problemScopeMapper.countByIds(normalizedScopeIds) != normalizedScopeIds.size()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "scope 不存在");
        }
        if (effectiveSort == CatalogSort.RATING && normalizedScopeIds.size() != 1) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST,
                    "按 rating 排序时必须且只能指定一个 scopeId");
        }

        CatalogCriteria criteria = new CatalogCriteria();
        criteria.setKeyword(StringUtils.hasText(keyword) ? keyword.trim() : null);
        criteria.setDifficulties(difficulties == null || difficulties.isEmpty() ? null : difficulties);
        criteria.setScopeIds(normalizedScopeIds.isEmpty() ? null : normalizedScopeIds);
        List<Long> normalizedTagIds = tagIds == null ? null : tagIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        criteria.setTagIds(normalizedTagIds == null || normalizedTagIds.isEmpty() ? null : normalizedTagIds);
        criteria.setTagCount(normalizedTagIds == null ? 0 : normalizedTagIds.size());
        criteria.setMatchAllTags(tagMatch == TagMatchMode.ALL);
        criteria.setRating(rating);
        criteria.setMinRating(minRating);
        criteria.setMaxRating(maxRating);
        criteria.setSort(effectiveSort.name());
        criteria.setSortAscending(order == SortOrder.ASC);

        long total = catalogMapper.count(criteria);
        if (total == 0) {
            return PageResult.empty(query);
        }
        criteria.setOffset(query.offset());
        criteria.setLimit(query.size());
        List<CatalogRow> rows = catalogMapper.findPage(criteria);
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), total, query);
        }

        Map<Long, List<TagVO>> localTags = loadLocalTags(rows);
        Map<Long, List<TagVO>> externalTags = loadExternalTags(rows);
        List<CatalogItemVO> items = rows.stream()
                .map(row -> {
                    boolean local = KIND_LOCAL.equals(row.getKind());
                    return new CatalogItemVO(row.getRefId(), row.getTitle(), row.getCode(),
                            row.getDifficulty(), row.getTime(), row.getRating(), row.getScopeId(),
                            row.getScopeName(), row.getUrl(), row.getSubmitCount(), row.getAcceptedCount(),
                            row.getAuthorId(), row.getAuthorNickname(),
                            (local ? localTags : externalTags).getOrDefault(row.getRefId(), List.of()),
                            row.getCreatedAt());
                })
                .toList();
        return PageResult.of(items, total, query);
    }

    /**
     * Deduplicates the supplied scope ids and drops nulls. Existence is validated by the caller so
     * an unknown id returns a request-parameter error instead of silently producing an empty page.
     */
    private List<Long> normalizeScopeIds(List<Long> scopeIds) {
        return scopeIds == null ? List.of() : scopeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Map<Long, List<TagVO>> loadLocalTags(List<CatalogRow> rows) {
        List<Long> ids = rows.stream()
                .filter(row -> KIND_LOCAL.equals(row.getKind()))
                .map(CatalogRow::getRefId)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tagMapper.findByProblemIds(ids).stream()
                .collect(Collectors.groupingBy(ProblemTagRow::getProblemId,
                        Collectors.mapping(row -> new TagVO(row.getId(), row.getName()), Collectors.toList())));
    }

    private Map<Long, List<TagVO>> loadExternalTags(List<CatalogRow> rows) {
        List<Long> ids = rows.stream()
                .filter(row -> KIND_EXTERNAL.equals(row.getKind()))
                .map(CatalogRow::getRefId)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return externalProblemTagMapper.findByExternalProblemIds(ids).stream()
                .collect(Collectors.groupingBy(ExternalProblemTagRow::getExternalProblemId,
                        Collectors.mapping(row -> new TagVO(row.getId(), row.getName()), Collectors.toList())));
    }
}
