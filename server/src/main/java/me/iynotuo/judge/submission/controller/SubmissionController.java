package me.iynotuo.judge.submission.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.SubmissionStatus;
import me.iynotuo.judge.security.LoginUser;
import me.iynotuo.judge.submission.dto.SubmissionDetailVO;
import me.iynotuo.judge.submission.dto.SubmissionSummaryVO;
import me.iynotuo.judge.submission.dto.SubmitRequest;
import me.iynotuo.judge.submission.service.SubmissionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Code submission endpoints.
 *
 * <p>Submitting requires authentication. The list and detail endpoints stay public so
 * that scoreboards work for anonymous visitors, but the source code, compile message,
 * judge message and per-testcase results are only returned to the submitter, the
 * problem author and administrators.</p>
 */
@RestController
@RequestMapping("/api/v1/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /**
     * Submits code for judging.
     *
     * <p>The submission is stored in PENDING state and judged asynchronously by the
     * judge worker, so the client should poll the detail endpoint until the status is no
     * longer PENDING or JUDGING.</p>
     *
     * @param current the authenticated user, recorded as the submitter
     * @param request problem id, language and source code
     * @return the newly created submission in PENDING state
     */
    @PostMapping
    public ApiResponse<SubmissionDetailVO> submit(@AuthenticationPrincipal LoginUser current,
                                                  @Valid @RequestBody SubmitRequest request) {
        return ApiResponse.ok(submissionService.submit(current, request));
    }

    /**
     * Returns a single submission.
     *
     * <p>Readable without authentication, but sensitive fields are masked for callers
     * other than the submitter, the problem author and administrators.</p>
     *
     * @param id      submission id
     * @param current the authenticated user, or {@code null} for anonymous callers
     * @return the submission detail
     */
    @GetMapping("/{id}")
    public ApiResponse<SubmissionDetailVO> detail(@PathVariable Long id,
                                                  @AuthenticationPrincipal LoginUser current) {
        return ApiResponse.ok(submissionService.getDetail(id, current));
    }

    /**
     * Lists submissions, optionally filtered by problem, user and status.
     *
     * <p>Source code is never included in list items.</p>
     *
     * @param problemId optional problem filter
     * @param userId    optional submitter filter
     * @param status    optional status filter
     * @param page      1-based page number
     * @param size      page size
     * @return a page of submission summaries
     */
    @GetMapping
    public ApiResponse<PageResult<SubmissionSummaryVO>> page(@RequestParam(required = false) Long problemId,
                                                             @RequestParam(required = false) Long userId,
                                                             @RequestParam(required = false) SubmissionStatus status,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(submissionService.page(problemId, userId, status, PageQuery.of(page, size)));
    }
}
