package me.iynotuo.judge.problem.controller;

import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.problem.dto.CatalogItemVO;
import me.iynotuo.judge.problem.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified search over the local problem bank and the external OJ catalog.
 *
 * <p>Kept outside {@code /problems} on purpose: {@code GET /problems/{id}} declares a Long path
 * variable, so {@code /problems/catalog} would be parsed as an id and fail with a type error before
 * reaching this controller.</p>
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * Lists local problems and external OJ links in one page.
     *
     * @param keyword    optional title keyword (applies to both kinds)
     * @param sourceId   optional source filter (applies to both kinds)
     * @param difficulty optional coarse difficulty (applies to local problems only)
     * @param tagId      optional tag filter (applies to both kinds)
     * @param rating     optional exact rating match
     * @param minRating  optional inclusive lower bound of rating
     * @param maxRating  optional inclusive upper bound of rating
     * @param kind       optional {@code LOCAL} / {@code EXTERNAL}; omit for both
     * @param page       1-based page number
     * @param size       page size
     * @return a page mixing both kinds, newest first
     */
    @GetMapping
    public ApiResponse<PageResult<CatalogItemVO>> page(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long sourceId,
                                                       @RequestParam(required = false) Difficulty difficulty,
                                                       @RequestParam(required = false) Long tagId,
                                                       @RequestParam(required = false) Integer rating,
                                                       @RequestParam(required = false) Integer minRating,
                                                       @RequestParam(required = false) Integer maxRating,
                                                       @RequestParam(required = false) String kind,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(catalogService.page(keyword, sourceId, difficulty, tagId, rating,
                minRating, maxRating, kind, PageQuery.of(page, size)));
    }
}
