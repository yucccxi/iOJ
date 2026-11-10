package me.iynotuo.judge.judge.dto;

import me.iynotuo.judge.judge.entity.JudgeNode;
import me.iynotuo.judge.judge.service.JudgeNodeService;
import me.iynotuo.judge.protocol.Language;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Judge node status shown to administrators.
 */
public record JudgeNodeVO(String nodeId,
                          String hostname,
                          String version,
                          List<Language> languages,
                          int maxConcurrency,
                          int runningTasks,
                          String isolateVersion,
                          String status,
                          LocalDateTime lastHeartbeatAt) {

    public static JudgeNodeVO from(JudgeNode node) {
        return new JudgeNodeVO(node.getId(), node.getHostname(), node.getVersion(),
                JudgeNodeService.parseLanguages(node.getLanguages()),
                node.getMaxConcurrency() == null ? 0 : node.getMaxConcurrency(),
                node.getRunningTasks() == null ? 0 : node.getRunningTasks(),
                node.getIsolateVersion(), node.getStatus(), node.getLastHeartbeatAt());
    }
}
