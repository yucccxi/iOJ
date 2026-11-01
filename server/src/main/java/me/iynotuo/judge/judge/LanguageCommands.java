package me.iynotuo.judge.judge;

import me.iynotuo.judge.config.JudgeProperties;

/**
 * Helpers shared by the judge engines for the language command templates.
 */
public final class LanguageCommands {

    private LanguageCommands() {
    }

    /**
     * Resolves the per-case time limit applied to a submission run.
     *
     * <p>The problem limit is multiplied by the language time multiplier so slower languages (for example
     * Java) get proportionally more time for their fixed start-up cost.</p>
     *
     * @param context the judging run
     * @return the limit in milliseconds, rounded up and clamped to at least 100 ms
     */
    public static int timeLimitMs(JudgeContext context) {
        double multiplier = context.language().getTimeMultiplier() <= 0
                ? 1.0 : context.language().getTimeMultiplier();
        int problemLimit = context.problem().getTimeLimitMs() == null
                ? 1000 : context.problem().getTimeLimitMs();
        return (int) Math.max(100, Math.ceil(problemLimit * multiplier));
    }

    /**
     * Substitutes the placeholders of a configured command template.
     *
     * @param template      command template such as {@code java -Xmx{memoryMb}m Main}; may be {@code null}
     * @param memoryLimitMb problem memory limit in megabytes
     * @param timeLimitMs   resolved time limit in milliseconds
     * @return the rendered command line, or an empty string when the template is {@code null}
     */
    public static String render(String template, int memoryLimitMb, int timeLimitMs) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{memoryMb}", String.valueOf(memoryLimitMb))
                .replace("{memoryKb}", String.valueOf(memoryLimitMb * 1024L))
                .replace("{timeMs}", String.valueOf(timeLimitMs));
    }

    /**
     * Tells whether the language needs a compile step.
     *
     * @param language the language configuration
     * @return {@code true} when a compile command is configured
     */
    public static boolean requiresCompile(JudgeProperties.LanguageConfig language) {
        return language.getCompileCommand() != null && !language.getCompileCommand().isBlank();
    }
}
