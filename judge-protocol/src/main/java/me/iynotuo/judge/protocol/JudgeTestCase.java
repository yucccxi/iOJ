package me.iynotuo.judge.protocol;

/**
 * One hidden test case delivered to a judge agent.
 *
 * @param ordinal test case number, starting at 1
 * @param input   standard input fed to the contestant program
 * @param output  expected standard output
 */
public record JudgeTestCase(int ordinal, String input, String output) {
}
