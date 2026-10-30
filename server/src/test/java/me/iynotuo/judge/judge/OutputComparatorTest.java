package me.iynotuo.judge.judge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OutputComparator}: whitespace and line-ending tolerance on
 * one hand, strictness on real content differences on the other.
 */
class OutputComparatorTest {

    /**
     * Treats trailing spaces, tabs and trailing blank lines as insignificant.
     */
    @Test
    void ignoresTrailingSpacesAndTrailingBlankLines() {
        assertThat(OutputComparator.matches("1 2\n3\n", "1 2   \n3\t\n\n\n")).isTrue();
    }

    /**
     * Treats CRLF and LF line endings as equal.
     */
    @Test
    void treatsDifferentLineEndingsAsEqual() {
        assertThat(OutputComparator.matches("a\nb\n", "a\r\nb\r\n")).isTrue();
    }

    /**
     * Detects different values and extra trailing lines.
     */
    @Test
    void detectsDifferentContent() {
        assertThat(OutputComparator.matches("3\n", "4\n")).isFalse();
        assertThat(OutputComparator.matches("3\n", "3\n0\n")).isFalse();
    }

    /**
     * Treats empty output and whitespace-only output as equal.
     */
    @Test
    void emptyOutputsAreEqual() {
        assertThat(OutputComparator.matches("", "\n\n")).isTrue();
        assertThat(OutputComparator.matches(null, "  \n")).isTrue();
    }

    /**
     * Keeps leading spaces significant.
     */
    @Test
    void keepsLeadingSpaces() {
        assertThat(OutputComparator.matches("  a\n", "a\n")).isFalse();
    }
}
