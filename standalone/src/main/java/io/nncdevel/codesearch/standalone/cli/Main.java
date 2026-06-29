package io.nncdevel.codesearch.standalone.cli;

import io.nncdevel.codesearch.standalone.ServerComponents;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * CLI entry point exposing the {@code serve} and {@code sync} subcommands (design §4.1).
 */
@Command(
        name = "codesearch-mcp",
        mixinStandardHelpOptions = true,
        version = "codesearch-mcp " + ServerComponents.VERSION,
        subcommands = {ServeCommand.class, SyncCommand.class},
        description = "Model Context Protocol server for searching Git-managed source code.")
public final class Main implements Runnable {

    /** Required by picocli. */
    public Main() {}

    /**
     * Process entry point.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /** Prints usage when invoked without a subcommand. */
    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
