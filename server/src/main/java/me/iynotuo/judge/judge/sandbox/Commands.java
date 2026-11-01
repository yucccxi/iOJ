package me.iynotuo.judge.judge.sandbox;

import java.util.Arrays;
import java.util.List;

/**
 * Helpers for the command templates that live in configuration.
 */
public final class Commands {

    private Commands() {
    }

    /**
     * Splits a configured command line on whitespace.
     *
     * <p>Quoting is deliberately not interpreted: the templates come from configuration and never from
     * user input, so a shell-like parser would only add surprise.</p>
     *
     * @param command command line such as {@code "javac -encoding UTF-8 -d . Main.java"}; {@code null} or
     *                blank yields an empty list
     * @return the non-empty tokens in order; never {@code null}
     */
    public static List<String> split(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        return Arrays.stream(command.trim().split("\\s+"))
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
