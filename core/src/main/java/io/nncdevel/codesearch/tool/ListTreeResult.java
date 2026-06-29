package io.nncdevel.codesearch.tool;

/**
 * Output of the {@code list_tree} tool (spec §4.3.2).
 *
 * @param repository the repository id
 * @param rootPath the relative path the tree is rooted at ({@code ""} for the repository root)
 * @param tree the ASCII-art tree text
 * @param truncated whether rendering stopped at the entry limit
 * @param entryCount the number of rendered entries
 */
public record ListTreeResult(String repository, String rootPath, String tree, boolean truncated, int entryCount) {}
