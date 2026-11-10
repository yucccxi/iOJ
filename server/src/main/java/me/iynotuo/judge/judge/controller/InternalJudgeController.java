package me.iynotuo.judge.judge.controller;

import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.judge.service.JudgeNodeService;
import me.iynotuo.judge.judge.service.JudgeResultService;
import me.iynotuo.judge.judge.service.JudgeTaskService;
import me.iynotuo.judge.protocol.JudgeHeartbeatRequest;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeReleaseRequest;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTaskClaimRequest;
import me.iynotuo.judge.protocol.JudgeTaskClaimResponse;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.security.JudgePrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal API used by remote judge agents. Authentication is an API key handled by
 * {@link me.iynotuo.judge.security.JudgeKeyAuthenticationFilter}; this controller never sees a user
 * token.
 */
@RestController
@RequestMapping("/internal/judge")
public class InternalJudgeController {

    private final JudgeTaskService judgeTaskService;
    private final JudgeResultService judgeResultService;
    private final JudgeNodeService judgeNodeService;

    public InternalJudgeController(JudgeTaskService judgeTaskService,
                                   JudgeResultService judgeResultService,
                                   JudgeNodeService judgeNodeService) {
        this.judgeTaskService = judgeTaskService;
        this.judgeResultService = judgeResultService;
        this.judgeNodeService = judgeNodeService;
    }

    @PostMapping("/nodes/heartbeat")
    public ApiResponse<Void> nodeHeartbeat(@AuthenticationPrincipal JudgePrincipal principal,
                                           @RequestBody JudgeNodeInfo info) {
        judgeNodeService.heartbeat(info, principal.keyId());
        return ApiResponse.ok();
    }

    @PostMapping("/tasks/claim")
    public ApiResponse<JudgeTaskClaimResponse> claim(@AuthenticationPrincipal JudgePrincipal principal,
                                                     @RequestBody JudgeTaskClaimRequest request) {
        List<JudgeTask> tasks = judgeTaskService.claim(principal.keyId(), request);
        return ApiResponse.ok(new JudgeTaskClaimResponse(tasks));
    }

    @PostMapping("/tasks/{submissionId}/heartbeat")
    public ApiResponse<JudgeHeartbeatResponse> taskHeartbeat(@PathVariable Long submissionId,
                                                             @RequestBody JudgeHeartbeatRequest request) {
        return ApiResponse.ok(judgeTaskService.heartbeat(submissionId, request.claimToken()));
    }

    @GetMapping("/tasks/{submissionId}/testcases")
    public ApiResponse<List<JudgeTestCase>> testcases(@PathVariable Long submissionId,
                                                      @RequestParam("claimToken") String claimToken) {
        return ApiResponse.ok(judgeTaskService.testcases(submissionId, claimToken));
    }

    @PostMapping("/tasks/{submissionId}/result")
    public ApiResponse<Void> result(@AuthenticationPrincipal JudgePrincipal principal,
                                    @PathVariable Long submissionId,
                                    @RequestBody JudgeResult result) {
        judgeResultService.apply(submissionId, principal.keyId(), result);
        return ApiResponse.ok();
    }

    @PostMapping("/tasks/{submissionId}/release")
    public ApiResponse<Void> release(@PathVariable Long submissionId,
                                     @RequestBody JudgeReleaseRequest request) {
        judgeTaskService.release(submissionId, request.claimToken());
        return ApiResponse.ok();
    }
}
