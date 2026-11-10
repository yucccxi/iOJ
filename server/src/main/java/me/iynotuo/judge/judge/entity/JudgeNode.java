package me.iynotuo.judge.judge.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * A judge agent that has registered with the main server.
 */
@Data
public class JudgeNode {

    private String id;

    private Long keyId;

    private String hostname;

    private String version;

    /**
     * Comma-separated language names, for example {@code JAVA,CPP,PYTHON3}.
     */
    private String languages;

    private Integer maxConcurrency;

    private Integer runningTasks;

    private String isolateVersion;

    /**
     * ONLINE / OFFLINE / DRAINING.
     */
    private String status;

    private LocalDateTime lastHeartbeatAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
