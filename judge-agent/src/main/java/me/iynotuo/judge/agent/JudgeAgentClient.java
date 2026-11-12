package me.iynotuo.judge.agent;

import me.iynotuo.judge.protocol.JudgeBootstrapConfig;
import me.iynotuo.judge.protocol.JudgeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeHeartbeatResponse;
import me.iynotuo.judge.protocol.JudgeNodeInfo;
import me.iynotuo.judge.protocol.JudgeResult;
import me.iynotuo.judge.protocol.JudgeTask;
import me.iynotuo.judge.protocol.JudgeTestCase;
import me.iynotuo.judge.protocol.Language;

import java.util.List;

/**
 * Transport used by the agent loop. Kept as an interface so the loop can be tested without HTTP.
 */
public interface JudgeAgentClient {

    JudgeBootstrapConfig fetchConfig();

    List<JudgeTask> claim(int maxTasks, List<Language> languages);

    /**
     * Downloads test data when a task was claimed in bundle mode.
     */
    List<JudgeTestCase> fetchTestcases(Long taskId, String claimToken);

    JudgeNodeHeartbeatResponse heartbeatNode(JudgeNodeInfo info);

    JudgeHeartbeatResponse heartbeatTask(Long taskId, String claimToken);

    void reportResult(Long taskId, JudgeResult result);

    void release(Long taskId, String claimToken, String reason);
}
