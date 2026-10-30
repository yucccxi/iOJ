package me.iynotuo.judge.user.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.security.LoginUser;
import me.iynotuo.judge.user.dto.ChangePasswordRequest;
import me.iynotuo.judge.user.dto.UpdateProfileRequest;
import me.iynotuo.judge.user.dto.UserProfileVO;
import me.iynotuo.judge.user.dto.UserVO;
import me.iynotuo.judge.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User profile endpoints.
 *
 * <p>Self-service endpoints address the caller as {@code /me} and therefore never take
 * a user id from the path, which removes any chance of modifying somebody else's
 * profile. Only the public profile lookup accepts an id, and it is read-only.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Returns the public profile of any user, including the number of solved problems.
     *
     * @param id user id
     * @return the public profile
     */
    @GetMapping("/{id}")
    public ApiResponse<UserProfileVO> profile(@PathVariable Long id) {
        return ApiResponse.ok(userService.getProfile(id));
    }

    /**
     * Returns the public profile of the current user.
     *
     * @param current the authenticated user
     * @return the current user's profile
     */
    @GetMapping("/me")
    public ApiResponse<UserProfileVO> me(@AuthenticationPrincipal LoginUser current) {
        return ApiResponse.ok(userService.getProfile(current.id()));
    }

    /**
     * Updates the nickname of the current user.
     *
     * @param current the authenticated user
     * @param request the new nickname, at most 32 characters
     * @return the updated user
     */
    @PatchMapping("/me")
    public ApiResponse<UserVO> updateProfile(@AuthenticationPrincipal LoginUser current,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(current, request));
    }

    /**
     * Changes the password of the current user.
     *
     * <p>The old password must be supplied and match, so a stolen token alone is not
     * enough to take over the account. Tokens issued before the change stay valid until
     * they expire, because the API keeps no token blacklist.</p>
     *
     * @param current the authenticated user
     * @param request the old and the new password
     * @return an empty success response
     */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal LoginUser current,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(current, request);
        return ApiResponse.ok();
    }
}
