package me.iynotuo.judge.core;

/**
 * One hidden test case, decoupled from the database entity.
 *
 * @param ordinal test case number, starting at 1
 * @param input   standard input fed to the contestant program
 * @param output  expected standard output
 */
public record JudgeTestcase(int ordinal, String input, String output) {
}
