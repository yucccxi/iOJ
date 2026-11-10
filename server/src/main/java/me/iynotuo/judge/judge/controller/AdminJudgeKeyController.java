package me.iynotuo.judge.judge.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.judge.dto.JudgeKeyCreateRequest;
import me.iynotuo.judge.judge.dto.JudgeKeyCreatedVO;
import me.iynotuo.judge.judge.dto.JudgeKeyStatusRequest;
import me.iynotuo.judge.judge.dto.JudgeKeyVO;
import me.iynotuo.judge.judge.service.JudgeKeyService;
import me.iynotuo.judge.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administrator management of remote judge agent keys.
 */
@RestController
@RequestMapping("/api/v1/admin/judge-keys")
@PreAuthorize("hasRole('ADMIN')")
public class AdminJudgeKeyController {

    private final JudgeKeyService judgeKeyService;

    public AdminJudgeKeyController(JudgeKeyService judgeKeyService) {
        this.judgeKeyService = judgeKeyService;
    }

    @PostMapping
    public ApiResponse<JudgeKeyCreatedVO> create(@AuthenticationPrincipal LoginUser current,
                                                 @Valid @RequestBody JudgeKeyCreateRequest request) {
        return ApiResponse.ok(JudgeKeyCreatedVO.from(
                judgeKeyService.create(request.name(), request.expiresAt(), current.id())));
    }

    @GetMapping
    public ApiResponse<List<JudgeKeyVO>> list() {
        return ApiResponse.ok(judgeKeyService.list().stream().map(JudgeKeyVO::from).toList());
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody JudgeKeyStatusRequest request) {
        judgeKeyService.setEnabled(id, request.enabled());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        judgeKeyService.delete(id);
        return ApiResponse.ok();
    }
}
