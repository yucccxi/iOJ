package me.iynotuo.judge.agent;

/**
 * Fatal startup problem. The agent exits instead of claiming tasks with an incomplete toolchain.
 */
public class AgentStartupException extends RuntimeException {

    private final int exitCode;

    public AgentStartupException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
