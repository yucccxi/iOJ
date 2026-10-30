package me.iynotuo.judge.judge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {

    @Test
    void ignoresTrailingSpacesAndTrailingBlankLines() {
        assertThat(OutputComparator.matches("1 2\n3\n", "1 2   \n3\t\n\n\n")).isTrue();
    }

    @Test
    void treatsDifferentLineEndingsAsEqual() {
        assertThat(OutputComparator.matches("a\nb\n", "a\r\nb\r\n")).isTrue();
    }

    @Test
    void detectsDifferentContent() {
        assertThat(OutputComparator.matches("3\n", "4\n")).isFalse();
        assertThat(OutputComparator.matches("3\n", "3\n0\n")).isFalse();
    }

    @Test
    void emptyOutputsAreEqual() {
        assertThat(OutputComparator.matches("", "\n\n")).isTrue();
        assertThat(OutputComparator.matches(null, "  \n")).isTrue();
    }

    @Test
    void keepsLeadingSpaces() {
        assertThat(OutputComparator.matches("  a\n", "a\n")).isFalse();
    }
}
