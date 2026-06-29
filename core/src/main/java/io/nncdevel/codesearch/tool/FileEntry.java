package io.nncdevel.codesearch.tool;

/**
 * A file listed by {@code list_files} (spec §4.2.2).
 *
 * @param repository the repository id
 * @param filePath the repository-relative file path
 * @param lastModified the last modification time in ISO-8601 (UTC)
 * @param gitUrl the hosting URL pointing at the file
 */
public record FileEntry(String repository, String filePath, String lastModified, String gitUrl) {}
