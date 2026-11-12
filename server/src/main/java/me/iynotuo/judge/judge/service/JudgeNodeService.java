package me.iynotuo.judge.judge.service;

import me.iynotuo.judge.common.exception.BusinessException;
import me.iynotuo.judge.common.exception.ErrorCode;
import me.iynotuo.judge.core.config.JudgeProperties;
import me.iynotuo.judge.judge.entity.JudgeNode;
import me.iynotuo.judge.judge.mapper.JudgeNodeMapper;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Maintains the judge node registry and offline detection.
 */
@Service
public class JudgeNodeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeNodeService.class);

    private final JudgeNodeMapper judgeNodeMapper;
    private final JudgeProperties judgeProperties;

    public JudgeNodeService(JudgeNodeMapper judgeNodeMapper, JudgeProperties judgeProperties) {
        this.judgeNodeMapper = judgeNodeMapper;
        this.judgeProperties = judgeProperties;
    }

    /**
     * Records a node heartbeat and refreshes its capabilities.
     */
    @Transactional
    public void heartbeat(JudgeNodeInfo info, Long keyId) {
        if (info == null || info.nodeId() == null || info.nodeId().isBlank()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "nodeId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        JudgeNode existing = judgeNodeMapper.findById(info.nodeId());
        if (existing != null && !Objects.equals(existing.getKeyId(), keyId)) {
            throw BusinessException.of(ErrorCode.CONFLICT, "该 nodeId 已被其他 key 注册");
        }
        JudgeNode node = existing == null ? new JudgeNode() : existing;
        node.setId(info.nodeId().trim());
        node.setKeyId(keyId);
        node.setHostname(info.hostname());
        node.setVersion(info.version());
        node.setLanguages(joinLanguages(info.languages()));
        node.setMaxConcurrency(Math.max(1, info.maxConcurrency()));
        node.setRunningTasks(Math.max(0, info.runningTasks()));
        node.setIsolateVersion(info.isolateVersion());
        node.setStatus("ONLINE");
        node.setLastHeartbeatAt(now);
        node.setUpdatedAt(now);
        if (existing == null) {
            node.setCreatedAt(now);
            judgeNodeMapper.insert(node);
        } else {
            judgeNodeMapper.updateHeartbeat(node);
        }
    }

    /**
     * Ensures a node exists when it claims work, even if its first heartbeat has not arrived yet.
     */
    @Transactional
    public void ensureNode(String nodeId, Long keyId, List<Language> languages) {
        if (nodeId == null || nodeId.isBlank()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "nodeId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        JudgeNode existing = judgeNodeMapper.findById(nodeId);
        if (existing != null && !Objects.equals(existing.getKeyId(), keyId)) {
            throw BusinessException.of(ErrorCode.CONFLICT, "该 nodeId 已被其他 key 注册");
        }
        JudgeNode node = existing == null ? new JudgeNode() : existing;
        node.setId(nodeId.trim());
        node.setKeyId(keyId);
        node.setLanguages(joinLanguages(languages));
        node.setStatus("ONLINE");
        node.setLastHeartbeatAt(now);
        node.setUpdatedAt(now);
        if (existing == null) {
            node.setHostname(null);
            node.setVersion(null);
            node.setMaxConcurrency(1);
            node.setRunningTasks(0);
            node.setIsolateVersion(null);
            node.setCreatedAt(now);
            judgeNodeMapper.insert(node);
        } else {
            judgeNodeMapper.updateHeartbeat(node);
        }
    }

    public List<JudgeNode> list() {
        return judgeNodeMapper.findAll();
    }

    @Scheduled(fixedDelayString = "${ioj.judge.dispatch.node-monitor-interval-ms:30000}")
    public void markOfflineNodes() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(nodeOfflineSeconds());
        int changed = judgeNodeMapper.markOfflineBefore(threshold);
        if (changed > 0) {
            log.info("标记 {} 个判题节点为离线", changed);
        }
    }

    /**
     * Effective node offline threshold returned to agents and used by the offline monitor.
     *
     * <p>The lower bound avoids a heartbeat interval that would hammer the server when the
     * configuration is accidentally set to a few seconds.</p>
     *
     * @return offline threshold in seconds, at least 30
     */
    public int nodeOfflineSeconds() {
        long configured = judgeProperties.getDispatch().getNodeOfflineSeconds();
        return (int) Math.max(30, Math.min(Integer.MAX_VALUE, configured));
    }

    private String joinLanguages(List<Language> languages) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }
        return String.join(",", languages.stream().map(Enum::name).toList());
    }

    /**
     * Parses the comma-separated capability string used by the database column.
     */
    public static List<Language> parseLanguages(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Language::valueOf)
                .toList();
    }
}
