package me.iynotuo.judge.judge.controller;

import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.judge.dto.JudgeNodeVO;
import me.iynotuo.judge.judge.service.JudgeNodeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of judge nodes for operators.
 */
@RestController
@RequestMapping("/api/v1/admin/judge-nodes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminJudgeNodeController {

    private final JudgeNodeService judgeNodeService;

    public AdminJudgeNodeController(JudgeNodeService judgeNodeService) {
        this.judgeNodeService = judgeNodeService;
    }

    @GetMapping
    public ApiResponse<List<JudgeNodeVO>> list() {
        return ApiResponse.ok(judgeNodeService.list().stream().map(JudgeNodeVO::from).toList());
    }
}
