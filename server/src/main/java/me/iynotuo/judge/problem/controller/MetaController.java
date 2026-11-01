package me.iynotuo.judge.problem.controller;

import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.problem.dto.LanguageVO;
import me.iynotuo.judge.problem.dto.SourceMetaVO;
import me.iynotuo.judge.problem.dto.TagMetaVO;
import me.iynotuo.judge.problem.service.MetaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reference-data endpoints used to bootstrap the front end.
 *
 * <p>Both endpoints are public, so the submission form and the problem editor can be
 * rendered before a user logs in. The language list is derived from the
 * {@code ioj.judge.languages} configuration map, which means enabling a new language
 * never requires a change in this class.</p>
 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    /**
     * Lists the languages a submission is allowed to use.
     *
     * @return enabled languages with their codes and display names
     */
    @GetMapping("/languages")
    public ApiResponse<List<LanguageVO>> languages() {
        return ApiResponse.ok(metaService.languages());
    }

    /**
     * Lists every tag available for labelling problems, with its usage count.
     *
     * @return all tags ordered by name
     */
    @GetMapping("/tags")
    public ApiResponse<List<TagMetaVO>> tags() {
        return ApiResponse.ok(metaService.tags());
    }

    /**
     * Lists every problem source (contest, textbook, OJ platform ...) that a problem may reference.
     *
     * @return all sources, ordered by name
     */
    @GetMapping("/sources")
    public ApiResponse<List<SourceMetaVO>> sources() {
        return ApiResponse.ok(metaService.sources());
    }
}
