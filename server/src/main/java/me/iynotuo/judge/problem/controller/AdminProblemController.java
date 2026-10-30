package me.iynotuo.judge.problem.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.ProblemStatus;
import me.iynotuo.judge.problem.dto.ProblemSummaryVO;
import me.iynotuo.judge.problem.dto.UpdateProblemStatusRequest;
import me.iynotuo.judge.problem.service.ProblemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative problem review endpoints.
 *
 * <p>Every method requires the ADMIN role. The check is applied twice: by the
 * {@code /api/v1/admin/**} URL rule in {@code SecurityConfig} and by the class-level
 * {@code @PreAuthorize} annotation below, so a mistake in one of them cannot expose the
 * review queue.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/problems")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProblemController {

    private final ProblemService problemService;

    public AdminProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    /**
     * Lists problems of every status for the review queue.
     *
     * @param keyword optional title keyword
     * @param status  optional status filter; all statuses are returned when omitted
     * @param page    1-based page number
     * @param size    page size
     * @return a page of problem summaries
     */
    @GetMapping
    public ApiResponse<PageResult<ProblemSummaryVO>> page(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) ProblemStatus status,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(problemService.pageForAdmin(keyword, status, PageQuery.of(page, size)));
    }

    /**
     * Approves or takes down a problem.
     *
     * <p>Only PUBLISHED and HIDDEN are accepted. Publishing requires at least one
     * testcase, so an empty problem can never go live.</p>
     *
     * @param id      problem id
     * @param request the target status
     * @return an empty success response
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateProblemStatusRequest request) {
        problemService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }
}
