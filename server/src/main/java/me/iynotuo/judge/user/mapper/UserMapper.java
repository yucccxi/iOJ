package me.iynotuo.judge.user.mapper;

import me.iynotuo.judge.common.enums.UserRole;
import me.iynotuo.judge.common.enums.UserStatus;
import me.iynotuo.judge.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data access for users; the SQL lives in resources/mapper/UserMapper.xml.
 */
@Mapper
public interface UserMapper {

    User findById(@Param("id") Long id);

    User findByUsername(@Param("username") String username);

    User findByEmail(@Param("email") String email);

    /**
     * Allows logging in with either the username or the email address.
     */
    User findByAccount(@Param("account") String account);

    int insert(User user);

    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("updatedAt") LocalDateTime updatedAt);

    int updatePassword(@Param("id") Long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("updatedAt") LocalDateTime updatedAt);

    int updateLastLogin(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    int updateRole(@Param("id") Long id,
                   @Param("role") UserRole role,
                   @Param("updatedAt") LocalDateTime updatedAt);

    int updateStatus(@Param("id") Long id,
                     @Param("status") UserStatus status,
                     @Param("updatedAt") LocalDateTime updatedAt);

    List<User> findPage(@Param("keyword") String keyword,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    long countPage(@Param("keyword") String keyword);

    long countAcceptedProblems(@Param("userId") Long userId);

    /**
     * Number of published problems authored by the user, used as the public contribution count.
     */
    long countAuthoredProblems(@Param("userId") Long userId);
}
