package me.iynotuo.judge.problem.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.Difficulty;
import me.iynotuo.judge.problem.dto.CheckerDetailVO;
import me.iynotuo.judge.problem.dto.CheckerSaveRequest;
import me.iynotuo.judge.problem.dto.ProblemDetailVO;
import me.iynotuo.judge.problem.dto.ProblemSaveRequest;
import me.iynotuo.judge.problem.dto.ProblemSummaryVO;
import me.iynotuo.judge.problem.service.ProblemService;
import me.iynotuo.judge.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Problem bank endpoints: browsing, detail lookup and author-side CRUD.
 *
 * <p>Read endpoints are public, but a problem that is not published is only visible to
 * its author and to administrators. Write endpoints require an authenticated user and
 * are then narrowed down to the author or an administrator inside {@link ProblemService}
 * rather than by URL rules, because ownership can only be decided per row.</p>
 */
@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    /**
     * Lists published problems, optionally filtered by keyword, difficulty and tag.
     *
     * @param keyword    optional title keyword
     * @param difficulty optional difficulty filter
     * @param tagId      optional tag filter
     * @param page       1-based page number
     * @param size       page size
     * @return a page of problem summaries
     */
    @GetMapping
    public ApiResponse<PageResult<ProblemSummaryVO>> page(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Difficulty difficulty,
                                                          @RequestParam(required = false) Long tagId,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(problemService.page(keyword, difficulty, tagId, PageQuery.of(page, size)));
    }

    /**
     * Lists the problems uploaded by the current user, including drafts, problems
     * awaiting review and problems that have been taken down.
     *
     * @param current the authenticated user
     * @param keyword optional title keyword
     * @param page    1-based page number
     * @param size    page size
     * @return a page of problem summaries owned by the caller
     */
    @GetMapping("/mine")
    public ApiResponse<PageResult<ProblemSummaryVO>> mine(@AuthenticationPrincipal LoginUser current,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(problemService.pageMine(current, keyword, PageQuery.of(page, size)));
    }

    /**
     * Returns the full detail of one problem.
     *
     * <p>Anonymous access is allowed for published problems only; an unpublished problem
     * is rejected with {@code PROBLEM_NOT_PUBLISHED} unless the caller is its author or
     * an administrator. For authenticated callers the response also tells whether the
     * problem has already been solved.</p>
     *
     * @param id      problem id
     * @param current the authenticated user, or {@code null} for anonymous callers
     * @return the problem detail
     */
    @GetMapping("/{id}")
    public ApiResponse<ProblemDetailVO> detail(@PathVariable Long id,
                                               @AuthenticationPrincipal LoginUser current) {
        return ApiResponse.ok(problemService.getDetail(id, current));
    }

    /**
     * Creates a problem owned by the current user.
     *
     * <p>The initial status is PUBLISHED when the caller is an administrator or when
     * {@code ioj.problem.auto-publish} is enabled; otherwise the problem enters the
     * review queue as PENDING_REVIEW.</p>
     *
     * @param current the authenticated user, recorded as the problem author
     * @param request problem fields together with its samples and tags
     * @return the created problem
     */
    @PostMapping
    public ApiResponse<ProblemDetailVO> create(@AuthenticationPrincipal LoginUser current,
                                               @Valid @RequestBody ProblemSaveRequest request) {
        return ApiResponse.ok(problemService.create(current, request));
    }

    /**
     * Fully replaces an existing problem, including its samples and tags.
     *
     * <p>Only the author or an administrator may update a problem; everybody else
     * receives {@code FORBIDDEN}.</p>
     *
     * @param current the authenticated user
     * @param id      problem id
     * @param request the new problem fields
     * @return the updated problem
     */
    @PutMapping("/{id}")
    public ApiResponse<ProblemDetailVO> update(@AuthenticationPrincipal LoginUser current,
                                               @PathVariable Long id,
                                               @Valid @RequestBody ProblemSaveRequest request) {
        return ApiResponse.ok(problemService.update(current, id, request));
    }

    /**
     * Uploads or clears the custom checker (special judge) of a problem.
     *
     * <p>Only the author or an administrator may change it. Sending an empty {@code code} removes the
     * checker and restores the built-in comparison. This lives outside {@code PUT /problems/{id}}
     * because that endpoint is a full replacement and would otherwise clear the checker whenever a
     * client forgot to send it.</p>
     *
     * @param current the authenticated user
     * @param id      problem id
     * @param request the checker source, possibly empty
     * @return an empty success response
     */
    @PutMapping("/{id}/checker")
    public ApiResponse<Void> updateChecker(@AuthenticationPrincipal LoginUser current,
                                           @PathVariable Long id,
                                           @Valid @RequestBody CheckerSaveRequest request) {
        problemService.updateChecker(current, id, request.code());
        return ApiResponse.ok();
    }

    /**
     * Returns the custom checker source of a problem for editing.
     *
     * <p>Author or administrator only: the checker encodes the judging rules, so it must never be
     * served to contestants.</p>
     *
     * @param current the authenticated user
     * @param id      problem id
     * @return the checker source, or an empty string when the problem has none
     */
    @GetMapping("/{id}/checker")
    public ApiResponse<CheckerDetailVO> checker(@AuthenticationPrincipal LoginUser current,
                                                @PathVariable Long id) {
        return ApiResponse.ok(new CheckerDetailVO(problemService.getChecker(current, id)));
    }

    /**
     * Deletes a problem and, through the foreign keys, all of its samples, tags,
     * testcases, submissions and submission cases.
     *
     * <p>Only the author or an administrator may delete a problem.</p>
     *
     * @param current the authenticated user
     * @param id      problem id
     * @return an empty success response
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser current, @PathVariable Long id) {
        problemService.delete(current, id);
        return ApiResponse.ok();
    }
}
