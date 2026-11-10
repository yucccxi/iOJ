package me.iynotuo.judge.protocol;

/**
 * Language command snapshot sent with a judge task.
 *
 * <p>The main server is the source of truth for compile/run commands, so every agent judges the same
 * language the same way. Agent-local settings such as the isolate binary and box ids are deliberately
 * not part of this record.</p>
 *
 * @param sourceFile           file name the source code is written to
 * @param compileCommand       compile command, or {@code null} for interpreted languages
 * @param runCommand           command that runs the submission
 * @param runWrapper           shell wrapper used by the local engine; may be {@code null}
 * @param timeMultiplier       multiplier applied to the problem time limit
 * @param isolateProcesses     process/thread limit inside the isolate sandbox
 * @param isolateMemoryExtraMb extra memory reserved for the runtime
 * @param copySourceToRun      whether the source file must be copied into run directories even when
 *                             a compile command is configured (for example Python syntax pre-check)
 */
public record JudgeLanguageConfig(String sourceFile,
                                  String compileCommand,
                                  String runCommand,
                                  String runWrapper,
                                  double timeMultiplier,
                                  Integer isolateProcesses,
                                  Integer isolateMemoryExtraMb,
                                  boolean copySourceToRun) {
}
