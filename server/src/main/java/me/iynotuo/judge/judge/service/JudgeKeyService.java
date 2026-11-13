package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.judge.entity.JudgeKey;
import me.iynotuo.judge.judge.mapper.JudgeKeyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Creates and validates API keys for remote judge agents.
 *
 * <p>The plaintext key is returned exactly once at creation time. Only its SHA-256 hash is stored, so
 * a database leak does not expose usable agent credentials.</p>
 */
@Service
public class JudgeKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "jsk_";

    private final JudgeKeyMapper judgeKeyMapper;

    public JudgeKeyService(JudgeKeyMapper judgeKeyMapper) {
        this.judgeKeyMapper = judgeKeyMapper;
    }

    /**
     * Creates a key.
     *
     * @param name      operator-facing label, usually the node name
     * @param expiresAt optional expiry
     * @param createdBy administrator user id
     * @return the stored key and its one-time plaintext value
     */
    @Transactional
    public CreatedKey create(String name, LocalDateTime expiresAt, Long createdBy) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String plaintext = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String prefix = plaintext.substring(0, Math.min(12, plaintext.length()));

        JudgeKey key = new JudgeKey();
        key.setName(name.trim());
        key.setKeyPrefix(prefix);
        key.setKeyHash(hash(plaintext));
        key.setEnabled(true);
        key.setExpiresAt(expiresAt);
        key.setCreatedBy(createdBy);
        key.setCreatedAt(LocalDateTime.now());
        judgeKeyMapper.insert(key);
        return new CreatedKey(key, plaintext);
    }

    /**
     * Resolves a plaintext key. Returns {@code null} for missing, disabled or expired keys.
     */
    public JudgeKey authenticate(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        JudgeKey key = judgeKeyMapper.findByHash(hash(plaintext.trim()));
        if (key == null || !Boolean.TRUE.equals(key.getEnabled())) {
            return null;
        }
        if (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(LocalDateTime.now())) {
            return null;
        }
        return key;
    }

    public List<JudgeKey> list() {
        return judgeKeyMapper.findAll();
    }

    public JudgeKey require(Long id) {
        JudgeKey key = judgeKeyMapper.findById(id);
        if (key == null) {
            throw BusinessException.of(ErrorCode.NOT_FOUND, "判题机 key 不存在");
        }
        return key;
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        require(id);
        judgeKeyMapper.updateEnabled(id, enabled);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        judgeKeyMapper.deleteById(id);
    }

    /**
     * Updates the diagnostic last-used timestamp. Called only when work is actually claimed, never on
     * an empty poll or when a result is reported.
     */
    @Transactional
    public void touch(Long id) {
        judgeKeyMapper.updateLastUsed(id, LocalDateTime.now());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * Result of key creation: the entity plus the plaintext value that must be shown only once.
     */
    public record CreatedKey(JudgeKey key, String plaintext) {
    }
}
