package me.iynotuo.judge.user.controller;

import jakarta.validation.Valid;
import me.iynotuo.judge.common.api.ApiResponse;
import me.iynotuo.judge.common.enums.EmailCodePurpose;
import me.iynotuo.judge.security.LoginUser;
import me.iynotuo.judge.user.dto.LoginRequest;
import me.iynotuo.judge.user.dto.LoginResponse;
import me.iynotuo.judge.user.dto.PasswordResetRequest;
import me.iynotuo.judge.user.dto.RegisterRequest;
import me.iynotuo.judge.user.dto.SendEmailCodeRequest;
import me.iynotuo.judge.user.dto.SendEmailCodeVO;
import me.iynotuo.judge.user.dto.UserVO;
import me.iynotuo.judge.user.service.EmailCodeService;
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
 * <p>Email code requests, register and login are the only fully public POST endpoints of the API.
 * Login issues a stateless JWT that the client sends back as {@code Authorization: Bearer <token>};
 * the server keeps no session, and the authorities of every request are reloaded from
 * the database.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final EmailCodeService emailCodeService;

    public AuthController(UserService userService, EmailCodeService emailCodeService) {
        this.userService = userService;
        this.emailCodeService = emailCodeService;
    }

    /**
     * Sends a registration verification code to the given address.
     *
     * <p>The code is only delivered by mail and never echoed in the response. The response carries
     * the lifetime and the earliest moment a new code may be requested, so the client can render a
     * countdown without guessing the server configuration.</p>
     *
     * @param request the address to verify
     * @return code lifetime and resend cooldown, both in seconds
     */
    @PostMapping("/email-codes")
    public ApiResponse<SendEmailCodeVO> sendEmailCode(@Valid @RequestBody SendEmailCodeRequest request) {
        return ApiResponse.ok(emailCodeService.send(request.email(), EmailCodePurpose.REGISTER));
    }

    /**
     * Sends a password reset code to the address of an existing account.
     *
     * <p>Unknown addresses are rejected explicitly with 2001, and banned accounts with 2004, so the
     * page can tell the visitor what to do instead of letting them wait for a mail that will never
     * arrive. The same facts are already observable through {@code POST /auth/register}.</p>
     *
     * @param request the address to send the code to
     * @return code lifetime and resend cooldown, both in seconds
     */
    @PostMapping("/password-reset-codes")
    public ApiResponse<SendEmailCodeVO> sendPasswordResetCode(@Valid @RequestBody SendEmailCodeRequest request) {
        return ApiResponse.ok(emailCodeService.send(request.email(), EmailCodePurpose.RESET_PASSWORD));
    }

    /**
     * Resets a forgotten password with the code mailed by {@code POST /auth/password-reset-codes}.
     *
     * <p>No token is needed: the code is the credential. It is single use and only valid for the
     * address it was sent to, so a registration code cannot be reused here.</p>
     *
     * @param request address, mailed code and the new password
     */
    @PostMapping("/password-resets")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(request);
        return ApiResponse.ok();
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
