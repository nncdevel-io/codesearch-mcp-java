package io.nncdevel.codesearch.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RipgrepArgsTest {

    @Test
    void searchBuildsJsonArgvWithDefaults() {
        List<String> argv = RipgrepArgs.search("foo", null, null, false, 0, 0, false, null);
        assertEquals(List.of("rg", "--json", "--ignore-case", "-e", "foo", "--", "."), argv);
    }

    @Test
    void searchCaseSensitiveGlobTypeAndContext() {
        List<String> argv = RipgrepArgs.search("Bar", "**/*.java", "java", true, 2, 3, true, "src");
        assertTrue(argv.contains("--case-sensitive"));
        assertTrue(argv.indexOf("-g") >= 0 && argv.contains("**/*.java"));
        assertTrue(argv.contains("--type") && argv.contains("java"));
        assertEquals("2", argv.get(argv.indexOf("-B") + 1));
        assertEquals("3", argv.get(argv.indexOf("-A") + 1));
        assertEquals("src", argv.get(argv.size() - 1));
    }

    @Test
    void contextOmittedWhenNotIncluded() {
        List<String> argv = RipgrepArgs.search("x", null, null, false, 5, 5, false, null);
        assertFalse(argv.contains("-B"));
        assertFalse(argv.contains("-A"));
    }

    @Test
    void listFilesIncludesIgnoredAndHiddenButNotGit() {
        List<String> argv = RipgrepArgs.listFiles("**/*.ts", "app");
        assertTrue(argv.contains("--files"));
        assertTrue(argv.contains("--no-ignore"));
        assertTrue(argv.contains("--hidden"));
        assertTrue(argv.contains("!.git/**"));
        assertTrue(argv.contains("**/*.ts"));
        assertEquals("app", argv.get(argv.size() - 1));
    }
}
