package me.iynotuo.judge.user.service;

import me.iynotuo.judge.captcha.CaptchaService;
import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.CaptchaPurpose;
import me.iynotuo.judge.common.enums.EmailCodePurpose;
import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.common.enums.UserStatus;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.security.JwtTokenProvider;
import me.iynotuo.judge.security.LoginUser;
import me.iynotuo.judge.user.dto.ChangePasswordRequest;
import me.iynotuo.judge.user.dto.LoginRequest;
import me.iynotuo.judge.user.dto.LoginResponse;
import me.iynotuo.judge.user.dto.PasswordResetRequest;
import me.iynotuo.judge.user.dto.RegisterRequest;
import me.iynotuo.judge.user.dto.UpdateProfileRequest;
import me.iynotuo.judge.user.dto.UserProfileVO;
import me.iynotuo.judge.user.dto.UserVO;
import me.iynotuo.judge.user.entity.User;
import me.iynotuo.judge.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * User account and login logic.
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailCodeService emailCodeService;
    private final CaptchaService captchaService;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider,
                       EmailCodeService emailCodeService, CaptchaService captchaService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.emailCodeService = emailCodeService;
        this.captchaService = captchaService;
    }

    @Transactional
    public UserVO register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userMapper.findByUsername(request.username()) != null) {
            throw BusinessException.of(ErrorCode.USERNAME_TAKEN);
        }
        if (userMapper.findByEmail(email) != null) {
            throw BusinessException.of(ErrorCode.EMAIL_TAKEN);
        }
        // Verify before writing anything: a rejected code must not create a row, and a taken
        // username must not burn a code. The failed-attempt counter is written by
        // EmailCodeAttemptRecorder in its own REQUIRES_NEW transaction, so a wrong code is still
        // counted when this transaction rolls back with the exception below.
        Long codeId = emailCodeService.isEnabled()
                ? emailCodeService.verify(email, EmailCodePurpose.REGISTER, request.emailCode())
                : null;
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : request.username());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // Unique indexes guard against concurrent registrations
            throw BusinessException.of(ErrorCode.CONFLICT, "用户名或邮箱已被占用");
        }
        // Consumed in this transaction, so a failed insert keeps the code usable and a successful
        // one can never be replayed.
        emailCodeService.consume(codeId);
        return UserVO.from(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Validate before looking up the account: a caller without a solved challenge must not be
        // able to probe usernames/emails through this endpoint.
        captchaService.verifyAndConsume(request.captchaId(), request.captchaCode(), CaptchaPurpose.LOGIN);
        User user = userMapper.findByAccount(request.account());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.BAD_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw BusinessException.of(ErrorCode.USER_DISABLED);
        }
        userMapper.updateLastLogin(user.getId(), LocalDateTime.now());
        String token = tokenProvider.createToken(user);
        return LoginResponse.of(token, tokenProvider.expirationSeconds(), UserVO.from(user));
    }

    public UserVO getById(Long id) {
        return UserVO.from(requireUser(id));
    }

    public UserProfileVO getProfile(Long id) {
        User user = requireUser(id);
        return UserProfileVO.of(user, userMapper.countAcceptedProblems(id),
                userMapper.countAuthoredProblems(id));
    }

    @Transactional
    public UserVO updateProfile(LoginUser current, UpdateProfileRequest request) {
        userMapper.updateProfile(current.id(), request.nickname().trim(), LocalDateTime.now());
        return getById(current.id());
    }

    /**
     * Changes the password and invalidates every JWT issued before this call. The caller's current
     * token stops working too, so the client must log in again.
     */
    @Transactional
    public void changePassword(LoginUser current, ChangePasswordRequest request) {
        User user = requireUser(current.id());
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.OLD_PASSWORD_MISMATCH);
        }
        userMapper.updatePassword(current.id(), passwordEncoder.encode(request.newPassword()), LocalDateTime.now());
    }

    /**
     * Invalidates every JWT for the current account and returns a new token for this device.
     */
    @Transactional
    public LoginResponse logoutAll(LoginUser current) {
        userMapper.incrementTokenVersion(current.id(), LocalDateTime.now());
        User user = requireUser(current.id());
        String token = tokenProvider.createToken(user);
        return LoginResponse.of(token, tokenProvider.expirationSeconds(), UserVO.from(user));
    }

    /**
     * Resets a forgotten password with a code mailed to the account address.
     *
     * <p>No login is required, so the code is the only credential. It is checked with
     * {@code RESET_PASSWORD}, which keeps a registration code from working here, and consumed in the
     * same transaction that writes the new password: a failed update keeps the code usable, a
     * successful one can never be replayed. Updating the password also increments the token version,
     * so every existing JWT for this account becomes invalid.</p>
     *
     * @param request address, mailed code and the new password
     */
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        if (!emailCodeService.isEnabled()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "邮箱验证功能未启用，无法通过邮箱找回密码");
        }
        String email = normalizeEmail(request.email());
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw BusinessException.of(ErrorCode.USER_NOT_FOUND, "该邮箱尚未注册");
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw BusinessException.of(ErrorCode.USER_DISABLED);
        }
        Long codeId = emailCodeService.verify(email, EmailCodePurpose.RESET_PASSWORD, request.emailCode());
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.newPassword()), LocalDateTime.now());
        emailCodeService.consume(codeId);
    }

    public PageResult<UserVO> page(String keyword, PageQuery query) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long total = userMapper.countPage(normalized);
        if (total == 0) {
            return PageResult.empty(query);
        }
        List<UserVO> items = userMapper.findPage(normalized, query.offset(), query.size()).stream()
                .map(UserVO::from)
                .toList();
        return PageResult.of(items, total, query);
    }

    @Transactional
    public void updateRole(LoginUser operator, Long userId, UserRole role) {
        if (operator.isSelf(userId)) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "不能修改自己的角色");
        }
        requireUser(userId);
        userMapper.updateRole(userId, role, LocalDateTime.now());
    }

    @Transactional
    public void updateStatus(LoginUser operator, Long userId, UserStatus status) {
        if (operator.isSelf(userId)) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "不能封禁自己");
        }
        requireUser(userId);
        userMapper.updateStatus(userId, status, LocalDateTime.now());
    }

    /**
     * Trims and lower-cases an address so one mailbox cannot create two accounts that differ only
     * in case. MySQL compares case-insensitively by default while H2 does not, and normalizing here
     * keeps both behaving the same.
     */
    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private User requireUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw BusinessException.of(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
