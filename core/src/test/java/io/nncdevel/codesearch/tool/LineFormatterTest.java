package io.nncdevel.codesearch.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LineFormatterTest {

    @Test
    void formatsWithRightAlignedNumbersAndTab() {
        String out = LineFormatter.format(List.of("public final class A {", "  int x;"), 40, 41);
        assertEquals("40\tpublic final class A {\n41\t  int x;", out);
    }

    @Test
    void padsToWidthOfLastLineNumber() {
        String out = LineFormatter.format(List.of("a", "b"), 9, 10);
        assertEquals(" 9\ta\n10\tb", out);
    }

    @Test
    void singleLineHasNoTrailingNewline() {
        String out = LineFormatter.format(List.of("only"), 1, 1);
        assertEquals("1\tonly", out);
        assertTrue(!out.endsWith("\n"));
    }

    @Test
    void emptyLinesYieldEmptyString() {
        assertEquals("", LineFormatter.format(List.of(), 1, 0));
    }
}
