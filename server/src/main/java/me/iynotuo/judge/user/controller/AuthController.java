package me.iynotuo.judge.user.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.security.LoginUser;
import me.iynotuo.judge.user.dto.LoginRequest;
import me.iynotuo.judge.user.dto.LoginResponse;
import me.iynotuo.judge.user.dto.RegisterRequest;
import me.iynotuo.judge.user.dto.UserVO;
import me.iynotuo.judge.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration and login endpoints.
 *
 * <p>Register and login are the only fully public POST endpoints of the API. They issue
 * a stateless JWT that the client sends back as {@code Authorization: Bearer <token>};
 * the server keeps no session, and the authorities of every request are reloaded from
 * the database.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Registers a new account.
     *
     * <p>The role is always USER and the status always ACTIVE regardless of the request
     * body, so registration can never be used to obtain administrator rights.</p>
     *
     * @param request username, email, password and optional nickname
     * @return the created user
     */
    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(userService.register(request));
    }

    /**
     * Authenticates a user and issues an access token.
     *
     * <p>Both wrong password and unknown account result in the same error code, so the
     * endpoint does not reveal whether an account exists. Banned accounts are rejected
     * here as well as on every subsequent request.</p>
     *
     * @param request account (username or email) and password
     * @return the access token, its lifetime in seconds and the user profile
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(userService.login(request));
    }

    /**
     * Returns the account information of the currently authenticated user.
     *
     * @param current the authenticated user
     * @return the current user
     */
    @GetMapping("/me")
    public ApiResponse<UserVO> me(@AuthenticationPrincipal LoginUser current) {
        return ApiResponse.ok(userService.getById(current.id()));
    }
}
