package io.nncdevel.codesearch.backend;

import java.util.List;

/**
 * A single ripgrep match with its surrounding context lines.
 *
 * @param file the repository-relative file path
 * @param lineNumber the 1-based line number of the match
 * @param lineContent the matched line content
 * @param contextBefore context lines preceding the match, in ascending line order
 * @param contextAfter context lines following the match, in ascending line order
 */
public record RgMatch(
        String file, int lineNumber, String lineContent, List<RgLine> contextBefore, List<RgLine> contextAfter) {

    /** Defensively copies the context lists. */
    public RgMatch {
        contextBefore = List.copyOf(contextBefore);
        contextAfter = List.copyOf(contextAfter);
    }
}
