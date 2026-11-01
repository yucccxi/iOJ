package me.iynotuo.judge.problem.service;

import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
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
import java.util.Map;
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
     * @param difficulty coarse difficulty filter; applies to local problems only
     * @param tagId      tag filter (both kinds), null means no filter
     * @param rating     exact rating match, optional
     * @param minRating  inclusive lower bound, optional
     * @param maxRating  inclusive upper bound, optional
     * @param kind       {@code LOCAL}, {@code EXTERNAL} or null for both
     * @param query      pagination
     * @return one page mixing both kinds, newest first
     */
    public PageResult<CatalogItemVO> page(String keyword, Long sourceId, Difficulty difficulty, Long tagId,
                                          Integer rating, Integer minRating, Integer maxRating,
                                          String kind, PageQuery query) {
        CatalogCriteria criteria = new CatalogCriteria();
        criteria.setKeyword(StringUtils.hasText(keyword) ? keyword.trim() : null);
        criteria.setSourceId(sourceId);
        criteria.setDifficulty(difficulty);
        criteria.setTagId(tagId);
        criteria.setRating(rating);
        criteria.setMinRating(minRating);
        criteria.setMaxRating(maxRating);
        criteria.setKind(normalizeKind(kind));

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
                    return new CatalogItemVO(row.getKind(), row.getRefId(), row.getTitle(),
                            row.getDifficulty(), row.getRating(), row.getSourceId(), row.getSourceName(),
                            row.getUrl(), row.getSubmitCount(), row.getAcceptedCount(),
                            row.getAuthorId(), row.getAuthorNickname(),
                            (local ? localTags : externalTags).getOrDefault(row.getRefId(), List.of()),
                            row.getCreatedAt());
                })
                .toList();
        return PageResult.of(items, total, query);
    }

    private String normalizeKind(String kind) {
        if (!StringUtils.hasText(kind)) {
            return null;
        }
        String normalized = kind.trim().toUpperCase();
        if (!KIND_LOCAL.equals(normalized) && !KIND_EXTERNAL.equals(normalized)) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "kind 只能是 LOCAL 或 EXTERNAL");
        }
        return normalized;
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
