package me.iynotuo.judge.agent;

import me.iynotuo.judge.protocol.JudgeBootstrapConfig;

/**
 * Fetches and validates the server configuration once per agent startup.
 */
public interface AgentBootstrap {

    BootstrapResult bootstrap();

    /**
     * Validated configuration that the claim loop may use.
     *
     * @param config server bootstrap configuration
     */
    record BootstrapResult(JudgeBootstrapConfig config) {
    }
}
