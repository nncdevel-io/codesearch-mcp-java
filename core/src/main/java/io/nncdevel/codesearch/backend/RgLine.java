package io.nncdevel.codesearch.backend;

/**
 * A single line (match or context) from ripgrep output.
 *
 * @param lineNumber the 1-based line number
 * @param content the line content with trailing end-of-line characters stripped
 */
public record RgLine(int lineNumber, String content) {}
