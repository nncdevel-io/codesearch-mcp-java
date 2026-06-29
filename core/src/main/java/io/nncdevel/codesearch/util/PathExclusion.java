package io.nncdevel.codesearch.util;

import java.util.List;

/** Applies a repository's {@code exclude_paths} prefix filter to result paths (spec §4, TASK-028). */
public final class PathExclusion {

    private PathExclusion() {}

    /**
     * Tests whether a repository-relative path is excluded by any configured prefix.
     *
     * @param filePath the repository-relative path to test
     * @param excludePrefixes the configured exclusion prefixes (may be empty)
     * @return {@code true} if {@code filePath} starts with any non-empty prefix
     */
    public static boolean isExcluded(String filePath, List<String> excludePrefixes) {
        for (String prefix : excludePrefixes) {
            if (!prefix.isEmpty() && filePath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
