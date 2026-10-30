package me.iynotuo.judge.judge;

import java.util.ArrayList;
import java.util.List;

/**
 * Output comparison: ignores trailing spaces and tabs on each line and trailing blank lines at the end (the usual OJ behaviour).
 */
public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean matches(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    static List<String> normalize(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : unified.split("\n", -1)) {
            lines.add(stripTrailingBlank(line));
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static String stripTrailingBlank(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        return line.substring(0, end);
    }
}
