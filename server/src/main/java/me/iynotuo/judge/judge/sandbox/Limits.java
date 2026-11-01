package me.iynotuo.judge.judge.sandbox;

/**
 * Resource limits applied to one sandboxed command.
 *
 * <p>The values are already resolved by the caller (problem limits multiplied by the language factor,
 * for example); a sandbox never reads problem or language configuration on its own.</p>
 *
 * @param timeMs      CPU time limit in milliseconds
 * @param wallSeconds wall-clock limit in seconds, kept above the CPU limit so a blocked program still ends
 * @param cgMemKb     memory limit in kilobytes
 * @param fsizeKb     maximum size of a file the program may write, in kilobytes
 */
public record Limits(int timeMs, int wallSeconds, int cgMemKb, int fsizeKb) {
}
