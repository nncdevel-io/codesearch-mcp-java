package io.nncdevel.codesearch.tool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds an ASCII-art directory tree from a flat list of repository-relative file paths (spec
 * §4.3.3). Top-level entries are rendered at column zero; deeper levels use Unicode box-drawing
 * connectors. Entries are sorted by Unicode code point, directories carry a trailing {@code /}.
 */
public final class TreeBuilder {

    private static final Comparator<String> CODE_POINT_ORDER = TreeBuilder::compareByCodePoint;

    private TreeBuilder() {}

    /**
     * Builds the tree.
     *
     * @param relativePaths file paths relative to the tree root
     * @param maxDepth the maximum depth to render (top-level entries are depth 1)
     * @param showFiles whether to include files (when {@code false}, only directories are shown)
     * @param maxEntries the maximum number of entries to render before truncating
     * @return the rendered tree, entry count, and truncation flag
     */
    public static TreeResult build(List<String> relativePaths, int maxDepth, boolean showFiles, int maxEntries) {
        Node root = new Node();
        for (String path : relativePaths) {
            insert(root, path);
        }
        Renderer renderer = new Renderer(maxDepth, showFiles, maxEntries);
        renderer.renderTopLevel(root);
        return new TreeResult(renderer.out.toString(), renderer.count, renderer.truncated);
    }

    private static void insert(Node root, String path) {
        Node cur = root;
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isEmpty()) {
                continue;
            }
            Node child = cur.children.get(seg);
            if (child == null) {
                child = new Node();
                cur.children.put(seg, child);
            }
            if (i < segments.length - 1) {
                child.directory = true;
            }
            cur = child;
        }
    }

    private static int compareByCodePoint(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(j);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /** A node in the path tree. */
    private static final class Node {
        private final Map<String, Node> children = new TreeMap<>(CODE_POINT_ORDER);
        private boolean directory;
    }

    /** Stateful depth-first renderer that honors the depth, file, and entry limits. */
    private static final class Renderer {
        private final int maxDepth;
        private final boolean showFiles;
        private final int maxEntries;
        private final StringBuilder out = new StringBuilder();
        private int count;
        private boolean truncated;

        private Renderer(int maxDepth, boolean showFiles, int maxEntries) {
            this.maxDepth = maxDepth;
            this.showFiles = showFiles;
            this.maxEntries = maxEntries;
        }

        private void renderTopLevel(Node root) {
            for (Map.Entry<String, Node> e : root.children.entrySet()) {
                if (!visible(e.getValue())) {
                    continue;
                }
                if (!emit(label(e.getKey(), e.getValue()))) {
                    return;
                }
                if (e.getValue().directory) {
                    renderChildren(e.getValue(), "", 2);
                    if (truncated) {
                        return;
                    }
                }
            }
        }

        private void renderChildren(Node node, String prefix, int depth) {
            if (depth > maxDepth) {
                return;
            }
            List<Map.Entry<String, Node>> visible = new ArrayList<>();
            for (Map.Entry<String, Node> e : node.children.entrySet()) {
                if (visible(e.getValue())) {
                    visible.add(e);
                }
            }
            for (int i = 0; i < visible.size(); i++) {
                Map.Entry<String, Node> e = visible.get(i);
                boolean last = i == visible.size() - 1;
                String connector = last ? "└── " : "├── ";
                if (!emit(prefix + connector + label(e.getKey(), e.getValue()))) {
                    return;
                }
                if (e.getValue().directory) {
                    renderChildren(e.getValue(), prefix + (last ? "    " : "│   "), depth + 1);
                    if (truncated) {
                        return;
                    }
                }
            }
        }

        private boolean visible(Node node) {
            return node.directory || showFiles;
        }

        private static String label(String name, Node node) {
            return node.directory ? name + "/" : name;
        }

        /** Appends a line unless the entry limit is reached; returns {@code false} when truncated. */
        private boolean emit(String line) {
            if (count >= maxEntries) {
                truncated = true;
                return false;
            }
            if (count > 0) {
                out.append('\n');
            }
            out.append(line);
            count++;
            return true;
        }
    }
}
