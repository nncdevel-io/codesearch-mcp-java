package io.nncdevel.codesearch.util;

import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;

/**
 * Relative-path validation and workspace containment.
 *
 * <p>Rejects absolute paths and {@code ..} segments ({@link ErrorCode#INVALID_PATH}), and confines
 * resolved paths inside the repository root via real-path resolution (spec §3.2, §5.4).
 */
public final class PathSafe {

    private PathSafe() {}

    /**
     * Validates a repository-relative POSIX path and returns it normalized (no leading slash, no
     * trailing slash, {@code "."} collapsed). The empty string denotes the repository root.
     *
     * @param relativePath the repository-relative path to validate, or {@code null}/empty for root
     * @return the normalized relative path ({@code ""} for the repository root)
     * @throws ToolException with {@link ErrorCode#INVALID_PATH} for absolute paths, backslashes, NUL
     *     bytes, or {@code ..} segments
     */
    public static String validateRelative(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return "";
        }
        if (relativePath.indexOf('\\') >= 0) {
            throw invalid(relativePath, "backslash is not allowed; use POSIX '/'");
        }
        if (relativePath.startsWith("/")) {
            throw invalid(relativePath, "absolute paths are not allowed");
        }
        if (relativePath.indexOf('\0') >= 0) {
            throw invalid(relativePath, "NUL byte is not allowed");
        }
        StringBuilder normalized = new StringBuilder();
        for (String segment : relativePath.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw invalid(relativePath, "'..' segments are not allowed");
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        return normalized.toString();
    }

    /**
     * Resolves a validated relative path inside {@code repoRoot}, confirming via real path that it
     * does not escape the root and that it exists.
     *
     * @param repoRoot the repository root directory
     * @param relativePath the repository-relative path to resolve
     * @return the resolved, contained real path
     * @throws ToolException with {@link ErrorCode#INVALID_PATH} if the resolved path escapes the
     *     root, or {@link ErrorCode#PATH_NOT_FOUND} if it does not exist
     */
    public static Path resolveWithin(Path repoRoot, String relativePath) {
        String normalized = validateRelative(relativePath);
        Path rootReal = toRealPath(repoRoot);
        Path target = normalized.isEmpty() ? rootReal : rootReal.resolve(normalized);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.exists(target)) {
            // Confirm the lexical target is still contained before reporting "not found".
            if (!target.normalize().startsWith(rootReal)) {
                throw invalid(relativePath, "path escapes the repository root");
            }
            throw new ToolException(
                    ErrorCode.PATH_NOT_FOUND, "Path does not exist in the repository", Map.of("path", relativePath));
        }
        Path targetReal = toRealPath(target);
        if (!targetReal.equals(rootReal) && !targetReal.startsWith(rootReal)) {
            throw invalid(relativePath, "path escapes the repository root");
        }
        return targetReal;
    }

    private static Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new ToolException(
                    ErrorCode.PATH_NOT_FOUND, "Path does not exist in the repository", Map.of("path", path.toString()));
        }
    }

    private static ToolException invalid(String path, String reason) {
        return new ToolException(ErrorCode.INVALID_PATH, "Invalid path", Map.of("path", path, "reason", reason));
    }
}
