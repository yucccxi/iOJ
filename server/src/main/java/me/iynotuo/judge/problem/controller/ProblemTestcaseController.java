package me.iynotuo.judge.problem.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.problem.dto.TestcaseDetailVO;
import me.iynotuo.judge.problem.dto.TestcaseRequest;
import me.iynotuo.judge.problem.dto.TestcaseSummaryVO;
import me.iynotuo.judge.problem.service.TestcaseService;
import me.iynotuo.judge.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Testcase management for a single problem.
 *
 * <p>Hidden test data is the most sensitive asset of an online judge, so every method,
 * including the read-only ones, requires the caller to be the problem author or an
 * administrator. {@code SecurityConfig} additionally marks this URL prefix as
 * authenticated, so anonymous callers are rejected before they reach this class.</p>
 */
@RestController
@RequestMapping("/api/v1/problems/{problemId}/testcases")
public class ProblemTestcaseController {

    private final TestcaseService testcaseService;

    public ProblemTestcaseController(TestcaseService testcaseService) {
        this.testcaseService = testcaseService;
    }

    /**
     * Lists the testcases of a problem without their input and output content.
     *
     * @param current   the authenticated user, who must be the author or an administrator
     * @param problemId problem id
     * @return testcase summaries ordered by ordinal
     */
    @GetMapping
    public ApiResponse<List<TestcaseSummaryVO>> list(@AuthenticationPrincipal LoginUser current,
                                                     @PathVariable Long problemId) {
        return ApiResponse.ok(testcaseService.list(current, problemId));
    }

    /**
     * Returns one testcase including its input and expected output.
     *
     * @param current    the authenticated user, who must be the author or an administrator
     * @param problemId  problem id
     * @param testcaseId testcase id, which has to belong to {@code problemId}
     * @return the testcase content
     */
    @GetMapping("/{testcaseId}")
    public ApiResponse<TestcaseDetailVO> get(@AuthenticationPrincipal LoginUser current,
                                             @PathVariable Long problemId,
                                             @PathVariable Long testcaseId) {
        return ApiResponse.ok(testcaseService.get(current, problemId, testcaseId));
    }

    /**
     * Appends a single testcase to a problem.
     *
     * <p>The ordinal is assigned automatically as the current maximum plus one, and the
     * per-problem testcase limit from {@code ioj.problem.max-testcases} is enforced.</p>
     *
     * @param current   the authenticated user, who must be the author or an administrator
     * @param problemId problem id
     * @param request   testcase input and expected output
     * @return the summary of the created testcase
     */
    @PostMapping
    public ApiResponse<TestcaseSummaryVO> add(@AuthenticationPrincipal LoginUser current,
                                              @PathVariable Long problemId,
                                              @Valid @RequestBody TestcaseRequest request) {
        return ApiResponse.ok(testcaseService.add(current, problemId, request));
    }

    /**
     * Replaces the whole testcase set of a problem in one request.
     *
     * <p>Intended for the "upload, then save once" front-end flow: the existing rows are
     * deleted and the submitted list is re-numbered starting from 1.</p>
     *
     * @param current   the authenticated user, who must be the author or an administrator
     * @param problemId problem id
     * @param requests  the complete new testcase set; an empty list clears the problem
     * @return the summaries of the stored testcases
     */
    @PutMapping
    public ApiResponse<List<TestcaseSummaryVO>> replace(@AuthenticationPrincipal LoginUser current,
                                                        @PathVariable Long problemId,
                                                        @Valid @RequestBody List<@Valid TestcaseRequest> requests) {
        return ApiResponse.ok(testcaseService.replace(current, problemId, requests));
    }

    /**
     * Deletes one testcase of a problem.
     *
     * @param current    the authenticated user, who must be the author or an administrator
     * @param problemId  problem id
     * @param testcaseId testcase id
     * @return an empty success response
     */
    @DeleteMapping("/{testcaseId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser current,
                                    @PathVariable Long problemId,
                                    @PathVariable Long testcaseId) {
        testcaseService.delete(current, problemId, testcaseId);
        return ApiResponse.ok();
    }
}
