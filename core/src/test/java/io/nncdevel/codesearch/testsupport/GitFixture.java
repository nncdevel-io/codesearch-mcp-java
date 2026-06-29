package io.nncdevel.codesearch.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Test helper that builds real Git repositories in temp directories: a working repo with committed
 * files and a bare clone usable as a "remote". All input is test-controlled.
 */
public final class GitFixture {

    private GitFixture() {}

    /** Creates a working git repo on {@code main} containing the given files, and returns its path. */
    public static Path createWorkingRepo(Path baseDir, Map<String, String> files) throws Exception {
        Path work = baseDir.resolve("work-" + System.nanoTime());
        Files.createDirectories(work);
        git(work, "init", "-b", "main");
        git(work, "config", "user.email", "test@example.com");
        git(work, "config", "user.name", "Test");
        writeFiles(work, files);
        git(work, "add", "-A");
        git(work, "commit", "-m", "initial");
        return work;
    }

    /** Creates a bare remote on {@code main} containing the given files, and returns its path. */
    public static Path createBareRemote(Path baseDir, Map<String, String> files) throws Exception {
        Path work = createWorkingRepo(baseDir, files);
        Path bare = baseDir.resolve("remote-" + System.nanoTime() + ".git");
        git(baseDir, "clone", "--bare", work.toString(), bare.toString());
        return bare;
    }

    /** Adds a commit with the given files to a bare remote (via a fresh clone) and returns nothing. */
    public static void commitToRemote(Path baseDir, Path bareRemote, Map<String, String> files) throws Exception {
        Path work = baseDir.resolve("push-" + System.nanoTime());
        git(baseDir, "clone", bareRemote.toString(), work.toString());
        git(work, "config", "user.email", "test@example.com");
        git(work, "config", "user.name", "Test");
        writeFiles(work, files);
        git(work, "add", "-A");
        git(work, "commit", "-m", "update");
        git(work, "push", "origin", "HEAD:main");
    }

    private static void writeFiles(Path root, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path p = root.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
    }

    /** Runs a git command, throwing if it fails. */
    public static void git(Path workdir, String... args) throws Exception {
        List<String> argv = new ArrayList<>();
        argv.add("git");
        for (String a : args) {
            argv.add(a);
        }
        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.directory(workdir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("git timed out: " + argv);
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException("git failed: " + argv + "\n" + output);
        }
    }
}
