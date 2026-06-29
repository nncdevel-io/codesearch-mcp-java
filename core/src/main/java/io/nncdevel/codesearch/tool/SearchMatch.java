package io.nncdevel.codesearch.tool;

import java.util.List;

/**
 * A single {@code search_code} hit in content mode (spec §4.1.2).
 *
 * @param repository the repository id
 * @param filePath the repository-relative file path
 * @param lineNumber the 1-based line number of the match
 * @param lineContent the matched line content
 * @param contextBefore preceding context lines, in ascending line order
 * @param contextAfter following context lines, in ascending line order
 * @param gitUrl the hosting URL anchored at the match (or its context range)
 */
public record SearchMatch(
        String repository,
        String filePath,
        int lineNumber,
        String lineContent,
        List<ContextLine> contextBefore,
        List<ContextLine> contextAfter,
        String gitUrl) {

    /** Defensively copies the context lists. */
    public SearchMatch {
        contextBefore = List.copyOf(contextBefore);
        contextAfter = List.copyOf(contextAfter);
    }
}
