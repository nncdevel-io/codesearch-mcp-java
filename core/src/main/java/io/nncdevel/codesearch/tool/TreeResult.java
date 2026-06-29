package io.nncdevel.codesearch.tool;

/**
 * Rendered tree and its metadata (spec §4.3.2).
 *
 * @param tree the ASCII-art tree text
 * @param entryCount the number of rendered entries
 * @param truncated whether rendering stopped at the entry limit
 */
public record TreeResult(String tree, int entryCount, boolean truncated) {}
