package io.nncdevel.codesearch.tool;

/**
 * Output of the {@code read_file} tool (spec §4.4.2).
 *
 * @param repository the repository id
 * @param filePath the repository-relative file path
 * @param startLine the actual first line returned (1-based)
 * @param endLine the actual last line returned (1-based; may be {@code startLine - 1} when empty)
 * @param totalLines the total number of lines in the file
 * @param content the line-number-prefixed content
 * @param gitUrl the hosting URL anchored at the returned range
 */
public record ReadFileResult(
        String repository,
        String filePath,
        int startLine,
        int endLine,
        int totalLines,
        String content,
        String gitUrl) {}
