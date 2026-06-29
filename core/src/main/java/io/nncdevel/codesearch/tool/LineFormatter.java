package io.nncdevel.codesearch.tool;

import java.util.List;

/**
 * Formats file content with right-aligned line-number prefixes for {@code read_file} (spec §4.4.3):
 * each line is {@code <line number><TAB><content>}, padded to the width of the last line number.
 */
public final class LineFormatter {

    private LineFormatter() {}

    /**
     * Formats the given lines with line-number-and-tab prefixes.
     *
     * @param lines the content lines, in order, starting at {@code startLine}
     * @param startLine the 1-based number of the first line
     * @param endLine the 1-based number of the last line, used to size the padding width
     * @return the formatted content; an empty string when {@code lines} is empty
     */
    public static String format(List<String> lines, int startLine, int endLine) {
        if (lines.isEmpty()) {
            return "";
        }
        int width = Integer.toString(Math.max(endLine, startLine)).length();
        StringBuilder out = new StringBuilder();
        int lineNumber = startLine;
        for (String line : lines) {
            String number = Integer.toString(lineNumber);
            out.append(" ".repeat(width - number.length()))
                    .append(number)
                    .append('\t')
                    .append(line);
            out.append('\n');
            lineNumber++;
        }
        out.setLength(out.length() - 1); // drop the trailing newline
        return out.toString();
    }
}
