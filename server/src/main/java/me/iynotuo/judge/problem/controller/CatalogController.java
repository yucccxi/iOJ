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

import java.util.List;

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
     * @param difficulties optional coarse difficulties (repeat the parameter to OR them; applies to
     *                     both local and external problems), e.g.
     *                     {@code difficulty=EASY&difficulty=HARD}
     * @param tagId        optional tag filter (applies to both kinds)
     * @param rating       optional exact rating match
     * @param minRating    optional inclusive lower bound of rating
     * @param maxRating    optional inclusive upper bound of rating
     * @param scope        optional source scope: {@code LOCAL}, {@code EXTERNAL}, or a source name
     *                     such as {@code Codeforces} / {@code AtCoder}; repeatable and
     *                     case-insensitive, e.g. {@code scope=LOCAL&scope=Codeforces}
     * @param page         1-based page number
     * @param size         page size
     * @return a page mixing both kinds, newest first
     */
    @GetMapping
    public ApiResponse<PageResult<CatalogItemVO>> page(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long sourceId,
                                                       @RequestParam(required = false) List<Difficulty> difficulty,
                                                       @RequestParam(required = false) List<String> scope,
                                                       @RequestParam(required = false) Long tagId,
                                                       @RequestParam(required = false) Integer rating,
                                                       @RequestParam(required = false) Integer minRating,
                                                       @RequestParam(required = false) Integer maxRating,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(catalogService.page(keyword, sourceId, difficulty, scope, tagId, rating,
                minRating, maxRating, PageQuery.of(page, size)));
    }
}
