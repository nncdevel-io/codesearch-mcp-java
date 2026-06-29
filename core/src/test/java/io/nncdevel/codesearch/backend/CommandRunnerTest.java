package io.nncdevel.codesearch.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nncdevel.codesearch.error.ToolException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandRunnerTest {

    private final CommandRunner runner = new CommandRunner();

    @Test
    void capturesStdoutAndExitCode() {
        CommandResult r = runner.run(List.of("git", "--version"), null, Map.of(), Duration.ofSeconds(10));
        assertTrue(r.succeeded());
        assertEquals(0, r.exitCode());
        assertTrue(r.stdout().contains("git version"));
        assertFalse(r.timedOut());
    }

    @Test
    void nonZeroExitIsReported() {
        CommandResult r = runner.run(
                List.of("git", "rev-parse", "--verify", "--quiet", "refs/heads/__no_such_branch_xyz__"),
                null,
                Map.of(),
                Duration.ofSeconds(10));
        assertFalse(r.succeeded());
        assertFalse(r.exitCode() == 0);
    }

    @Test
    void missingBinaryThrowsBackendFailure() {
        assertThrows(
                ToolException.class,
                () -> runner.run(List.of("definitely-not-a-real-binary-xyz"), null, Map.of(), Duration.ofSeconds(5)));
    }

    @Test
    void timeoutIsDetected() {
        CommandResult r = runner.run(List.of("sleep", "5"), null, Map.of(), Duration.ofMillis(200));
        assertTrue(r.timedOut());
        assertFalse(r.succeeded());
    }
}
