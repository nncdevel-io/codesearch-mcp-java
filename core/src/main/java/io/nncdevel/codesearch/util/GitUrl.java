package io.nncdevel.codesearch.util;

import java.nio.charset.StandardCharsets;

/**
 * Generates Git hosting URLs with line anchors (spec §6).
 *
 * <p>Path and branch are percent-encoded keeping {@code '/'} unescaped; spaces become {@code %20},
 * non-ASCII bytes are UTF-8 percent-encoded, and reserved characters ({@code ? # %}) are encoded.
 */
public final class GitUrl {

    private GitUrl() {}

    /**
     * Builds a single-line anchor URL.
     *
     * @param baseUrl the repository base URL, with or without a trailing slash
     * @param hosting the hosting service that determines the URL shape
     * @param branch the branch name (percent-encoded, slashes preserved)
     * @param path the repository-relative path (percent-encoded, slashes preserved)
     * @param line the 1-based line number to anchor
     * @return the hosting URL with a single-line anchor
     */
    public static String singleLine(String baseUrl, Hosting hosting, String branch, String path, int line) {
        String b = trimTrailingSlash(baseUrl);
        String br = encode(branch);
        String p = encode(path);
        return switch (hosting) {
            case GITHUB -> b + "/blob/" + br + "/" + p + "#L" + line;
            case GITLAB -> b + "/-/blob/" + br + "/" + p + "#L" + line;
            case BITBUCKET -> b + "/src/" + br + "/" + p + "#lines-" + line;
            case GITEA -> b + "/src/branch/" + br + "/" + p + "#L" + line;
        };
    }

    /**
     * Builds a line-range anchor URL.
     *
     * @param baseUrl the repository base URL, with or without a trailing slash
     * @param hosting the hosting service that determines the URL shape
     * @param branch the branch name (percent-encoded, slashes preserved)
     * @param path the repository-relative path (percent-encoded, slashes preserved)
     * @param startLine the 1-based start line of the range
     * @param endLine the 1-based end line of the range
     * @return the hosting URL with a line-range anchor
     */
    public static String range(
            String baseUrl, Hosting hosting, String branch, String path, int startLine, int endLine) {
        String b = trimTrailingSlash(baseUrl);
        String br = encode(branch);
        String p = encode(path);
        return switch (hosting) {
            case GITHUB -> b + "/blob/" + br + "/" + p + "#L" + startLine + "-L" + endLine;
            case GITLAB -> b + "/-/blob/" + br + "/" + p + "#L" + startLine + "-" + endLine;
            case BITBUCKET -> b + "/src/" + br + "/" + p + "#lines-" + startLine + ":" + endLine;
            case GITEA -> b + "/src/branch/" + br + "/" + p + "#L" + startLine + "-L" + endLine;
        };
    }

    /**
     * Builds a file URL with no line anchor (used for file-level results).
     *
     * @param baseUrl the repository base URL, with or without a trailing slash
     * @param hosting the hosting service that determines the URL shape
     * @param branch the branch name (percent-encoded, slashes preserved)
     * @param path the repository-relative path (percent-encoded, slashes preserved)
     * @return the hosting URL pointing at the file with no anchor
     */
    public static String file(String baseUrl, Hosting hosting, String branch, String path) {
        String b = trimTrailingSlash(baseUrl);
        String br = encode(branch);
        String p = encode(path);
        return switch (hosting) {
            case GITHUB -> b + "/blob/" + br + "/" + p;
            case GITLAB -> b + "/-/blob/" + br + "/" + p;
            case BITBUCKET -> b + "/src/" + br + "/" + p;
            case GITEA -> b + "/src/branch/" + br + "/" + p;
        };
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Percent-encodes a path-like value, leaving {@code '/'} and unreserved characters intact. */
    static String encode(String value) {
        StringBuilder out = new StringBuilder(value.length());
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte rawByte : bytes) {
            int c = rawByte & 0xFF;
            if (c == '/' || isUnreserved(c)) {
                out.append((char) c);
            } else {
                out.append('%');
                out.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
                out.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return out.toString();
    }

    private static boolean isUnreserved(int c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '-'
                || c == '.'
                || c == '_'
                || c == '~';
    }
}
