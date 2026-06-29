package io.nncdevel.codesearch.backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@code ripgrep} argument vectors (no shell). Pure and side-effect free for testability
 * (design §3, spec §4.1/§4.2).
 */
public final class RipgrepArgs {

    private RipgrepArgs() {}

    /**
     * Builds the argv for content search via {@code rg --json}. The same JSON output is used to derive
     * {@code files_with_matches} and {@code count} results, so context flags are added only when
     * requested.
     *
     * @param pattern the Rust-regex search pattern
     * @param glob an optional filename glob filter, or {@code null}/empty for none
     * @param type an optional ripgrep type filter (e.g. {@code "java"}), or {@code null}/empty
     * @param caseSensitive whether matching is case-sensitive
     * @param contextBefore the number of lines of context before each match
     * @param contextAfter the number of lines of context after each match
     * @param includeContext whether to add the {@code -B}/{@code -A} context flags
     * @param path the repository-relative search root, or {@code null}/empty for the whole repo
     * @return the ripgrep argument vector
     */
    public static List<String> search(
            String pattern,
            String glob,
            String type,
            boolean caseSensitive,
            int contextBefore,
            int contextAfter,
            boolean includeContext,
            String path) {
        List<String> argv = new ArrayList<>();
        argv.add("rg");
        argv.add("--json");
        argv.add(caseSensitive ? "--case-sensitive" : "--ignore-case");
        if (glob != null && !glob.isEmpty()) {
            argv.add("-g");
            argv.add(glob);
        }
        if (type != null && !type.isEmpty()) {
            argv.add("--type");
            argv.add(type);
        }
        if (includeContext) {
            if (contextBefore > 0) {
                argv.add("-B");
                argv.add(Integer.toString(contextBefore));
            }
            if (contextAfter > 0) {
                argv.add("-A");
                argv.add(Integer.toString(contextAfter));
            }
        }
        argv.add("-e");
        argv.add(pattern);
        argv.add("--");
        argv.add(searchTarget(path));
        return argv;
    }

    /**
     * Builds the argv for {@code rg --files}. Unlike content search, file listing includes
     * {@code .gitignore}'d and hidden files (requirements §4.2.3), but never the {@code .git}
     * directory.
     *
     * @param glob an optional filename glob filter, or {@code null}/empty for none
     * @param path the repository-relative search root, or {@code null}/empty for the whole repo
     * @return the ripgrep argument vector
     */
    public static List<String> listFiles(String glob, String path) {
        List<String> argv = new ArrayList<>();
        argv.add("rg");
        argv.add("--files");
        argv.add("--no-ignore");
        argv.add("--hidden");
        if (glob != null && !glob.isEmpty()) {
            argv.add("-g");
            argv.add(glob);
        }
        argv.add("-g");
        argv.add("!.git/**");
        argv.add("--");
        argv.add(searchTarget(path));
        return argv;
    }

    private static String searchTarget(String path) {
        return (path == null || path.isEmpty()) ? "." : path;
    }
}
