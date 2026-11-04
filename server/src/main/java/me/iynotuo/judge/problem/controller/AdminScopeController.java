package me.iynotuo.judge.problem.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.problem.dto.ScopeMetaVO;
import me.iynotuo.judge.problem.dto.ScopeSaveRequest;
import me.iynotuo.judge.problem.service.ScopeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Problem scope dictionary maintenance (administrators only).
 *
 * <p>Scopes are shared by local problems and external OJ links. The public list with usage counts
 * is served by {@code GET /meta/scopes}; a rename is reflected everywhere and a scope that is
 * still referenced cannot be deleted.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/scopes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminScopeController {

    private final ScopeService scopeService;

    public AdminScopeController(ScopeService scopeService) {
        this.scopeService = scopeService;
    }

    /**
     * Creates a scope.
     */
    @PostMapping
    public ApiResponse<ScopeMetaVO> create(@Valid @RequestBody ScopeSaveRequest request) {
        return ApiResponse.ok(scopeService.create(request));
    }

    /**
     * Renames a scope; the change is visible on every problem immediately.
     */
    @PutMapping("/{id}")
    public ApiResponse<ScopeMetaVO> rename(@PathVariable Long id,
                                           @Valid @RequestBody ScopeSaveRequest request) {
        return ApiResponse.ok(scopeService.rename(id, request));
    }

    /**
     * Deletes a scope. A scope still used by local problems or external OJ links is rejected.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scopeService.delete(id);
        return ApiResponse.ok();
    }
}
