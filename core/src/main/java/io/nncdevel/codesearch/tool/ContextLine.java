package io.nncdevel.codesearch.tool;

/**
 * A context line accompanying a search match (spec §4.1.2).
 *
 * @param lineNumber the 1-based line number
 * @param content the line content
 */
public record ContextLine(int lineNumber, String content) {}
