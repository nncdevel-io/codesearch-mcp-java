package io.nncdevel.codesearch.backend;

/**
 * Outcome of running an external command.
 *
 * @param exitCode process exit code ({@code -1} when it timed out)
 * @param stdout captured standard output
 * @param stderr captured standard error
 * @param timedOut whether the process was killed for exceeding its time budget
 */
public record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {

    /**
     * Tests whether the command completed normally with a zero exit code.
     *
     * @return {@code true} if the process did not time out and exited with code 0
     */
    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }
}
