package me.iynotuo.judge.user.service;

import me.iynotuo.judge.common.api.PageQuery;
import me.iynotuo.judge.common.api.PageResult;
import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.common.enums.UserStatus;
import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.security.JwtTokenProvider;
import me.iynotuo.judge.security.LoginUser;
import me.iynotuo.judge.user.dto.ChangePasswordRequest;
import me.iynotuo.judge.user.dto.LoginRequest;
import me.iynotuo.judge.user.dto.LoginResponse;
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

/**
 * 用户与登录相关的业务逻辑。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public UserVO register(RegisterRequest request) {
        if (userMapper.findByUsername(request.username()) != null) {
            throw BusinessException.of(ErrorCode.USERNAME_TAKEN);
        }
        if (userMapper.findByEmail(request.email()) != null) {
            throw BusinessException.of(ErrorCode.EMAIL_TAKEN);
        }
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : request.username());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 并发注册时由唯一索引兜底
            throw BusinessException.of(ErrorCode.CONFLICT, "用户名或邮箱已被占用");
        }
        return UserVO.from(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
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
        return UserProfileVO.of(user, userMapper.countAcceptedProblems(id));
    }

    @Transactional
    public UserVO updateProfile(LoginUser current, UpdateProfileRequest request) {
        userMapper.updateProfile(current.id(), request.nickname().trim(), LocalDateTime.now());
        return getById(current.id());
    }

    @Transactional
    public void changePassword(LoginUser current, ChangePasswordRequest request) {
        User user = requireUser(current.id());
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.OLD_PASSWORD_MISMATCH);
        }
        userMapper.updatePassword(current.id(), passwordEncoder.encode(request.newPassword()), LocalDateTime.now());
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

    private User requireUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw BusinessException.of(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
