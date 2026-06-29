package io.nncdevel.codesearch.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TreeBuilderTest {

    @Test
    void rendersBoxDrawingTreeInCodePointOrder() {
        TreeResult r = TreeBuilder.build(List.of("a/b/c.txt", "a/d.txt", "e.txt"), 5, true, 1000);
        String expected = String.join("\n", "a/", "├── b/", "│   └── c.txt", "└── d.txt", "e.txt");
        assertEquals(expected, r.tree());
        assertEquals(5, r.entryCount());
        assertFalse(r.truncated());
    }

    @Test
    void hidesFilesWhenShowFilesFalse() {
        TreeResult r = TreeBuilder.build(List.of("a/b/c.txt", "a/d.txt", "e.txt"), 5, false, 1000);
        String expected = String.join("\n", "a/", "└── b/");
        assertEquals(expected, r.tree());
        assertEquals(2, r.entryCount());
    }

    @Test
    void limitsDepth() {
        TreeResult r = TreeBuilder.build(List.of("a/b/c/d.txt"), 2, true, 1000);
        String expected = String.join("\n", "a/", "└── b/");
        assertEquals(expected, r.tree());
    }

    @Test
    void truncatesAtMaxEntries() {
        TreeResult r = TreeBuilder.build(List.of("a.txt", "b.txt", "c.txt"), 5, true, 2);
        assertTrue(r.truncated());
        assertEquals(2, r.entryCount());
        assertEquals(String.join("\n", "a.txt", "b.txt"), r.tree());
    }

    @Test
    void emptyInputYieldsEmptyTree() {
        TreeResult r = TreeBuilder.build(List.of(), 5, true, 1000);
        assertEquals("", r.tree());
        assertEquals(0, r.entryCount());
        assertFalse(r.truncated());
    }
}
