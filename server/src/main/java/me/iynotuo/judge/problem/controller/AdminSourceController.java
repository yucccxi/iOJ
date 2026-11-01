package me.iynotuo.judge.problem.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.problem.dto.SourceMetaVO;
import me.iynotuo.judge.problem.dto.SourceSaveRequest;
import me.iynotuo.judge.problem.service.SourceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Problem source dictionary maintenance (administrators only).
 *
 * <p>Sources are shared by local problems and external OJ links. The public list with usage counts
 * is served by {@code GET /meta/sources}; a rename is reflected everywhere and a source that is
 * still referenced cannot be deleted.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/sources")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSourceController {

    private final SourceService sourceService;

    public AdminSourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    /**
     * Creates a source.
     */
    @PostMapping
    public ApiResponse<SourceMetaVO> create(@Valid @RequestBody SourceSaveRequest request) {
        return ApiResponse.ok(sourceService.create(request));
    }

    /**
     * Renames a source; the change is visible on every problem immediately.
     */
    @PutMapping("/{id}")
    public ApiResponse<SourceMetaVO> rename(@PathVariable Long id,
                                            @Valid @RequestBody SourceSaveRequest request) {
        return ApiResponse.ok(sourceService.rename(id, request));
    }

    /**
     * Deletes a source. A source still used by local problems or external OJ links is rejected.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sourceService.delete(id);
        return ApiResponse.ok();
    }
}
