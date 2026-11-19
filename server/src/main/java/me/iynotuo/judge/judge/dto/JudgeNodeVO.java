package me.iynotuo.judge.judge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.iynotuo.judge.judge.entity.JudgeNode;
import me.iynotuo.judge.judge.service.JudgeNodeService;
import me.iynotuo.judge.protocol.Language;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Judge node status shown to administrators.
 */
public record JudgeNodeVO(String nodeId,
                          @Schema(nullable = true) String hostname,
                          @Schema(nullable = true) String version,
                          List<Language> languages,
                          int maxConcurrency,
                          int runningTasks,
                          @Schema(nullable = true) String isolateVersion,
                          String status,
                          @Schema(nullable = true) LocalDateTime lastHeartbeatAt) {

    public static JudgeNodeVO from(JudgeNode node) {
        return new JudgeNodeVO(node.getId(), node.getHostname(), node.getVersion(),
                JudgeNodeService.parseLanguages(node.getLanguages()),
                node.getMaxConcurrency() == null ? 0 : node.getMaxConcurrency(),
                node.getRunningTasks() == null ? 0 : node.getRunningTasks(),
                node.getIsolateVersion(), node.getStatus(), node.getLastHeartbeatAt());
    }
}
