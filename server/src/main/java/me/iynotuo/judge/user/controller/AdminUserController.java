package me.iynotuo.judge.user.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.security.LoginUser;
import me.iynotuo.judge.user.dto.UpdateUserRoleRequest;
import me.iynotuo.judge.user.dto.UpdateUserStatusRequest;
import me.iynotuo.judge.user.dto.UserVO;
import me.iynotuo.judge.user.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative user management endpoints.
 *
 * <p>Every method requires the ADMIN role, enforced both by the {@code /api/v1/admin/**}
 * URL rule in {@code SecurityConfig} and by the class-level {@code @PreAuthorize} below.
 * An administrator can neither change their own role nor ban themselves, which prevents
 * an accidental lockout of the last administrator.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Lists users with an optional keyword filter.
     *
     * @param keyword optional username, nickname or email keyword
     * @param page    1-based page number
     * @param size    page size
     * @return a page of users
     */
    @GetMapping
    public ApiResponse<PageResult<UserVO>> page(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(userService.page(keyword, PageQuery.of(page, size)));
    }

    /**
     * Grants or revokes the ADMIN role for a user.
     *
     * <p>The change applies to the target user's next request because authorities are
     * always reloaded from the database, but it does not invalidate tokens that have
     * already been issued.</p>
     *
     * @param current the administrator performing the change
     * @param id      the user to modify
     * @param request the target role
     * @return an empty success response
     */
    @PatchMapping("/{id}/role")
    public ApiResponse<Void> updateRole(@AuthenticationPrincipal LoginUser current,
                                        @PathVariable Long id,
                                        @Valid @RequestBody UpdateUserRoleRequest request) {
        userService.updateRole(current, id, request.role());
        return ApiResponse.ok();
    }

    /**
     * Bans or unbans a user.
     *
     * <p>A banned user cannot log in any more, and any token that was already issued is
     * rejected on the next request because the authentication filter only accepts ACTIVE
     * accounts.</p>
     *
     * @param current the administrator performing the change
     * @param id      the user to modify
     * @param request the target status
     * @return an empty success response
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@AuthenticationPrincipal LoginUser current,
                                          @PathVariable Long id,
                                          @Valid @RequestBody UpdateUserStatusRequest request) {
        userService.updateStatus(current, id, request.status());
        return ApiResponse.ok();
    }
}
