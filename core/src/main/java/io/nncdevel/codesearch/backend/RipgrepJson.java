package io.nncdevel.codesearch.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses ripgrep's {@code --json} newline-delimited output into matches with context (spec §4.1).
 */
public final class RipgrepJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RipgrepJson() {}

    /**
     * Parses match and context events. Context lines are associated with each match by line-number
     * proximity within the same file, bounded by {@code contextBefore}/{@code contextAfter}.
     *
     * @param stdout the raw {@code rg --json} output
     * @param contextBefore the number of context lines before a match to attach
     * @param contextAfter the number of context lines after a match to attach
     * @return the parsed matches in output order; empty when there are no matches
     */
    public static List<RgMatch> parseMatches(String stdout, int contextBefore, int contextAfter) {
        List<String> matchFiles = new ArrayList<>();
        List<Integer> matchLines = new ArrayList<>();
        List<String> matchContents = new ArrayList<>();

        List<String> ctxFiles = new ArrayList<>();
        List<RgLine> ctxLines = new ArrayList<>();

        for (String line : stdout.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node;
            try {
                node = MAPPER.readTree(line);
            } catch (Exception e) {
                continue; // tolerate non-JSON noise
            }
            String type = node.path("type").asText("");
            JsonNode data = node.path("data");
            switch (type) {
                case "match" -> {
                    String file = normalizePath(text(data.path("path")));
                    if (file == null) {
                        continue;
                    }
                    matchFiles.add(file);
                    matchLines.add(data.path("line_number").asInt());
                    matchContents.add(stripEol(text(data.path("lines"))));
                }
                case "context" -> {
                    String file = normalizePath(text(data.path("path")));
                    if (file == null) {
                        continue;
                    }
                    ctxFiles.add(file);
                    ctxLines.add(new RgLine(data.path("line_number").asInt(), stripEol(text(data.path("lines")))));
                }
                default -> {
                    // begin/end/summary: ignored
                }
            }
        }

        List<RgMatch> matches = new ArrayList<>();
        for (int i = 0; i < matchFiles.size(); i++) {
            String file = matchFiles.get(i);
            int matchLine = matchLines.get(i);
            List<RgLine> before = new ArrayList<>();
            List<RgLine> after = new ArrayList<>();
            for (int j = 0; j < ctxFiles.size(); j++) {
                if (!ctxFiles.get(j).equals(file)) {
                    continue;
                }
                RgLine cl = ctxLines.get(j);
                if (contextBefore > 0 && cl.lineNumber() >= matchLine - contextBefore && cl.lineNumber() < matchLine) {
                    before.add(cl);
                } else if (contextAfter > 0
                        && cl.lineNumber() > matchLine
                        && cl.lineNumber() <= matchLine + contextAfter) {
                    after.add(cl);
                }
            }
            before.sort((a, b) -> Integer.compare(a.lineNumber(), b.lineNumber()));
            after.sort((a, b) -> Integer.compare(a.lineNumber(), b.lineNumber()));
            matches.add(new RgMatch(file, matchLine, matchContents.get(i), before, after));
        }
        return matches;
    }

    private static String normalizePath(String file) {
        if (file == null) {
            return null;
        }
        return file.startsWith("./") ? file.substring(2) : file;
    }

    private static String text(JsonNode lines) {
        JsonNode t = lines.path("text");
        return t.isMissingNode() || t.isNull() ? null : t.asText();
    }

    private static String stripEol(String s) {
        if (s == null) {
            return "";
        }
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) {
            end--;
        }
        return s.substring(0, end);
    }
}
