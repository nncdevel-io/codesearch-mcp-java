package io.nncdevel.codesearch.tool;

/**
 * Validated input for {@code search_code} (spec §4.1.1). Range validation (bounds on context and
 * {@code maxResults}) is enforced by the MCP input schema; the tool only clamps {@code maxResults}
 * to the hard cap.
 *
 * @param pattern the Rust-regex search pattern
 * @param repository the repository id
 * @param path the repository-relative search root, or {@code null}/empty for the whole repo
 * @param glob an optional filename glob filter, or {@code null}
 * @param type an optional ripgrep type filter, or {@code null}
 * @param caseSensitive whether matching is case-sensitive
 * @param outputMode the desired output mode
 * @param contextBefore the number of context lines before each match
 * @param contextAfter the number of context lines after each match
 * @param maxResults the requested maximum number of results
 */
public record SearchRequest(
        String pattern,
        String repository,
        String path,
        String glob,
        String type,
        boolean caseSensitive,
        OutputMode outputMode,
        int contextBefore,
        int contextAfter,
        int maxResults) {}
