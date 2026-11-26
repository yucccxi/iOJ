package me.iynotuo.judge.agent;

import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;

import java.nio.file.Path;
import java.util.List;

/**
 * Transport used by the agent loop. Kept as an interface so the loop can be tested without HTTP.
 */
public interface JudgeAgentClient {

    JudgeBootstrapConfig fetchConfig();

    List<JudgeTask> claim(int maxTasks, List<Language> languages);

    /**
     * Fetches and parses test data directly, bypassing the local disk cache.
     */
    List<JudgeTestCase> fetchTestcases(Long taskId, String claimToken);

    /**
     * Downloads the raw ZIP bundle to {@code target}, replacing an existing file.
     */
    void downloadTestcaseBundle(Long taskId, String claimToken, Path target);

    JudgeNodeHeartbeatResponse heartbeatNode(JudgeNodeInfo info);

    JudgeHeartbeatResponse heartbeatTask(Long taskId, String claimToken);

    void reportResult(Long taskId, JudgeResult result);

    void release(Long taskId, String claimToken, String reason);
}
