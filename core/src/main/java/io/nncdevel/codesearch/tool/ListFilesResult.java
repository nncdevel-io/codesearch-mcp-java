package io.nncdevel.codesearch.tool;

import java.util.List;

/**
 * Output of the {@code list_files} tool (spec §4.2.2).
 *
 * @param files the matching files, ordered by last-modified descending and capped at the maximum
 * @param truncated whether files were dropped to honor the cap
 */
public record ListFilesResult(List<FileEntry> files, boolean truncated) {

    /** Defensively copies the file list. */
    public ListFilesResult {
        files = List.copyOf(files);
    }
}
