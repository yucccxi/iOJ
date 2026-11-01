package me.iynotuo.judge.problem.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.problem.dto.TagMetaVO;
import me.iynotuo.judge.problem.dto.TagSaveRequest;
import me.iynotuo.judge.problem.service.TagService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tag dictionary maintenance (administrators only).
 *
 * <p>Tags are a shared dictionary: the public list with usage counts is served by
 * {@code GET /meta/tags}, while creation, renaming and deletion happen here. Deleting a tag that is
 * still attached to a problem is rejected unless {@code force=true} is passed, which detaches it
 * everywhere first.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/tags")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTagController {

    private final TagService tagService;

    public AdminTagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * Creates a tag.
     */
    @PostMapping
    public ApiResponse<TagMetaVO> create(@Valid @RequestBody TagSaveRequest request) {
        return ApiResponse.ok(tagService.create(request));
    }

    /**
     * Renames a tag; the change is visible on every problem immediately.
     */
    @PutMapping("/{id}")
    public ApiResponse<TagMetaVO> rename(@PathVariable Long id,
                                         @Valid @RequestBody TagSaveRequest request) {
        return ApiResponse.ok(tagService.rename(id, request));
    }

    /**
     * Deletes a tag.
     *
     * @param force when true, detach the tag from all problems first; otherwise a tag in use is rejected
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestParam(required = false, defaultValue = "false") boolean force) {
        tagService.delete(id, force);
        return ApiResponse.ok();
    }
}
