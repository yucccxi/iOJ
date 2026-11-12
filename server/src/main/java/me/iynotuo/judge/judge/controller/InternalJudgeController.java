package me.iynotuo.judge.judge.controller;

import jakarta.servlet.http.HttpServletResponse;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.judge.service.JudgeBootstrapService;
import me.iynotuo.judge.judge.service.JudgeNodeService;
import me.iynotuo.judge.judge.service.JudgeResultService;
import me.iynotuo.judge.judge.service.JudgeTaskService;
import me.iynotuo.judge.judge.service.JudgeTestcaseBundleWriter;
import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeHeartbeatRequest;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeReleaseRequest;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTaskClaimRequest;
import me.iynotuo.judge.protocol.JudgeTaskClaimResponse;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.security.JudgePrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Internal API used by remote judge agents. Authentication is an API key handled by
 * {@link me.iynotuo.judge.security.JudgeKeyAuthenticationFilter}; this controller never sees a user
 * token.
 */
@RestController
@RequestMapping("/internal/judge")
public class InternalJudgeController {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JudgeTaskService judgeTaskService;
    private final JudgeResultService judgeResultService;
    private final JudgeNodeService judgeNodeService;
    private final JudgeBootstrapService judgeBootstrapService;
    private final JudgeTestcaseBundleWriter testcaseBundleWriter;

    public InternalJudgeController(JudgeTaskService judgeTaskService,
                                   JudgeResultService judgeResultService,
                                   JudgeNodeService judgeNodeService,
                                   JudgeBootstrapService judgeBootstrapService,
                                   JudgeTestcaseBundleWriter testcaseBundleWriter) {
        this.judgeTaskService = judgeTaskService;
        this.judgeResultService = judgeResultService;
        this.judgeNodeService = judgeNodeService;
        this.judgeBootstrapService = judgeBootstrapService;
        this.testcaseBundleWriter = testcaseBundleWriter;
    }

    @GetMapping("/config")
    public ApiResponse<JudgeBootstrapConfig> config() {
        return ApiResponse.ok(judgeBootstrapService.bootstrapConfig());
    }

    @PostMapping("/nodes/heartbeat")
    public ApiResponse<JudgeNodeHeartbeatResponse> nodeHeartbeat(@AuthenticationPrincipal JudgePrincipal principal,
                                                                 @RequestBody JudgeNodeInfo info) {
        judgeNodeService.heartbeat(info, principal.keyId());
        return ApiResponse.ok(new JudgeNodeHeartbeatResponse(judgeNodeService.nodeOfflineSeconds()));
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
    public void testcases(@PathVariable Long submissionId,
                          @RequestParam("claimToken") String claimToken,
                          @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                          HttpServletResponse response) throws IOException {
        List<JudgeTestCase> testcases = judgeTaskService.testcases(submissionId, claimToken);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT);
        if (accept != null && accept.toLowerCase(Locale.ROOT).contains("application/zip")) {
            response.setContentType("application/zip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"testcases-" + submissionId + ".zip\"");
            testcaseBundleWriter.write(response.getOutputStream(), testcases);
            return;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        JSON.writeValue(response.getOutputStream(), ApiResponse.ok(testcases));
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
