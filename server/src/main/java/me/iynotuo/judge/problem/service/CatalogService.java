package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.enums.TagMatchMode;
import me.iynotuo.judge.problem.dto.CatalogItemVO;
import me.iynotuo.judge.problem.dto.TagVO;
import me.iynotuo.judge.problem.mapper.CatalogCriteria;
import me.iynotuo.judge.problem.mapper.CatalogMapper;
import me.iynotuo.judge.problem.mapper.CatalogRow;
import me.iynotuo.judge.problem.mapper.ExternalProblemTagMapper;
import me.iynotuo.judge.problem.mapper.ExternalProblemTagRow;
import me.iynotuo.judge.problem.mapper.ProblemTagRow;
import me.iynotuo.judge.problem.mapper.TagMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Unified search over local problems and external OJ links.
 *
 * <p>The two kinds live in different tables, so the SQL is a UNION ALL with a shared column set
 * ({@code kind}, {@code ref_id}, {@code title}, {@code rating}, {@code source_id}, {@code url}).
 * Tags are loaded per kind with at most two queries, so a page of N items never costs N queries.</p>
 */
@Service
public class CatalogService {

    private static final String KIND_LOCAL = "LOCAL";
    private static final String KIND_EXTERNAL = "EXTERNAL";

    private final CatalogMapper catalogMapper;
    private final TagMapper tagMapper;
    private final ExternalProblemTagMapper externalProblemTagMapper;

    public CatalogService(CatalogMapper catalogMapper,
                          TagMapper tagMapper,
                          ExternalProblemTagMapper externalProblemTagMapper) {
        this.catalogMapper = catalogMapper;
        this.tagMapper = tagMapper;
        this.externalProblemTagMapper = externalProblemTagMapper;
    }

    /**
     * Searches both problem kinds with the given filters.
     *
     * @param keyword    title keyword (both kinds), blank means no filter
     * @param sourceId   source filter (both kinds), null means no filter
     * @param difficulties coarse difficulty filter; multiple values are OR-ed and apply to both kinds
     * @param scopes      source scopes: {@code LOCAL}, {@code EXTERNAL}, or source names
     *                    (case-insensitive, repeatable, OR-ed); null/empty means both kinds
     * @param tagIds      tag ids to match (both kinds); null/empty means no tag filter
     * @param tagMatch    {@code ANY} (default) or {@code ALL}; only relevant when tagIds is set
     * @param rating      exact rating match, optional
     * @param minRating   inclusive lower bound, optional
     * @param maxRating   inclusive upper bound, optional
     * @param query       pagination
     * @return one page mixing both kinds, newest first
     */
    public PageResult<CatalogItemVO> page(String keyword, Long sourceId, List<Difficulty> difficulties,
                                          List<String> scopes, List<Long> tagIds, TagMatchMode tagMatch,
                                          Integer rating, Integer minRating, Integer maxRating,
                                          PageQuery query) {
        CatalogCriteria criteria = new CatalogCriteria();
        criteria.setKeyword(StringUtils.hasText(keyword) ? keyword.trim() : null);
        criteria.setSourceId(sourceId);
        criteria.setDifficulties(difficulties == null || difficulties.isEmpty() ? null : difficulties);
        applyScope(criteria, scopes);
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
                    return new CatalogItemVO(row.getKind(), row.getRefId(), row.getTitle(), row.getCode(),
                            row.getDifficulty(), row.getTime(), row.getRating(), row.getSourceId(),
                            row.getSourceName(), row.getUrl(), row.getSubmitCount(), row.getAcceptedCount(),
                            row.getAuthorId(), row.getAuthorNickname(),
                            (local ? localTags : externalTags).getOrDefault(row.getRefId(), List.of()),
                            row.getCreatedAt());
                })
                .toList();
        return PageResult.of(items, total, query);
    }

    /**
     * Translates the public {@code scope} values into the two UNION-branch flags.
     *
     * <p>{@code LOCAL} keeps the local branch, {@code EXTERNAL} keeps every external row, and any
     * other value is treated as an external source name (case-insensitive). Multiple values are
     * OR-ed, so {@code LOCAL&scope=Codeforces} returns local problems plus Codeforces problems.</p>
     */
    private void applyScope(CatalogCriteria criteria, List<String> scopes) {
        List<String> normalized = scopes == null ? List.of() : scopes.stream()
                .filter(StringUtils::hasText)
                .map(scope -> scope.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            criteria.setIncludeLocal(true);
            criteria.setIncludeExternal(true);
            criteria.setExternalSources(null);
            return;
        }

        boolean includeAllExternal = normalized.contains("external");
        List<String> namedSources = normalized.stream()
                .filter(scope -> !scope.equals("local") && !scope.equals("external"))
                .toList();
        criteria.setIncludeLocal(normalized.contains("local"));
        criteria.setIncludeExternal(includeAllExternal || !namedSources.isEmpty());
        criteria.setExternalSources(includeAllExternal ? null : namedSources);
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
