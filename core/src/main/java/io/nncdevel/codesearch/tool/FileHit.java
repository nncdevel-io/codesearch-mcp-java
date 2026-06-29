package io.nncdevel.codesearch.tool;

/**
 * A file-level hit for {@code search_code} in {@code files_with_matches} mode (spec §4.1.3).
 *
 * @param repository the repository id
 * @param filePath the repository-relative file path
 * @param gitUrl the hosting URL pointing at the file
 */
public record FileHit(String repository, String filePath, String gitUrl) {}
