package io.nncdevel.codesearch.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GitUrlTest {

    private static final String GH = "https://github.com/example/main-app";
    private static final String GL = "https://gitlab.com/example/main-app";
    private static final String BB = "https://bitbucket.org/example/main-app";
    private static final String GT = "https://gitea.example.com/example/main-app";

    @Test
    void githubSingleLine() {
        assertEquals(
                GH + "/blob/main/src/App.java#L42", GitUrl.singleLine(GH, Hosting.GITHUB, "main", "src/App.java", 42));
    }

    @Test
    void gitlabSingleLine() {
        assertEquals(
                GL + "/-/blob/main/src/App.java#L42",
                GitUrl.singleLine(GL, Hosting.GITLAB, "main", "src/App.java", 42));
    }

    @Test
    void bitbucketSingleLine() {
        assertEquals(
                BB + "/src/main/src/App.java#lines-42",
                GitUrl.singleLine(BB, Hosting.BITBUCKET, "main", "src/App.java", 42));
    }

    @Test
    void giteaSingleLine() {
        assertEquals(
                GT + "/src/branch/main/src/App.java#L42",
                GitUrl.singleLine(GT, Hosting.GITEA, "main", "src/App.java", 42));
    }

    @Test
    void githubRange() {
        assertEquals(GH + "/blob/main/a.txt#L10-L20", GitUrl.range(GH, Hosting.GITHUB, "main", "a.txt", 10, 20));
    }

    @Test
    void gitlabRange() {
        assertEquals(GL + "/-/blob/main/a.txt#L10-20", GitUrl.range(GL, Hosting.GITLAB, "main", "a.txt", 10, 20));
    }

    @Test
    void bitbucketRange() {
        assertEquals(BB + "/src/main/a.txt#lines-10:20", GitUrl.range(BB, Hosting.BITBUCKET, "main", "a.txt", 10, 20));
    }

    @Test
    void giteaRange() {
        assertEquals(GT + "/src/branch/main/a.txt#L10-L20", GitUrl.range(GT, Hosting.GITEA, "main", "a.txt", 10, 20));
    }

    @Test
    void trailingSlashOnBaseIsTrimmed() {
        assertEquals(GH + "/blob/main/a.txt#L1", GitUrl.singleLine(GH + "/", Hosting.GITHUB, "main", "a.txt", 1));
    }

    @Test
    void pathSeparatorsArePreservedButSpacesEncoded() {
        assertEquals(
                GH + "/blob/main/dir/my%20file.txt#L1",
                GitUrl.singleLine(GH, Hosting.GITHUB, "main", "dir/my file.txt", 1));
    }

    @Test
    void reservedAndNonAsciiAreEncoded() {
        assertEquals("a%23b", GitUrl.encode("a#b"));
        assertEquals("a%3Fb", GitUrl.encode("a?b"));
        assertEquals("100%25", GitUrl.encode("100%"));
        assertEquals("%E6%97%A5%E6%9C%AC", GitUrl.encode("日本"));
        assertEquals("a/b", GitUrl.encode("a/b"));
    }

    @Test
    void hostingWireRoundTrips() {
        for (Hosting h : Hosting.values()) {
            assertEquals(h, Hosting.fromWire(h.wire()));
        }
    }
}
