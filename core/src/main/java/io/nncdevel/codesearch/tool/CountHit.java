package io.nncdevel.codesearch.tool;

/**
 * A per-file match count for {@code search_code} in {@code count} mode (spec §4.1.4).
 *
 * @param repository the repository id
 * @param filePath the repository-relative file path
 * @param matchCount the number of matches in the file
 * @param gitUrl the hosting URL pointing at the file
 */
public record CountHit(String repository, String filePath, int matchCount, String gitUrl) {}
