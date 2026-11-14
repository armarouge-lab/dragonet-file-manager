package br.redhot.dragonet;

/**
 * Entry point for {@code dragon}. Delegates to {@link Cli} and converts
 * the returned {@link ExitCode} into the process exit status.
 *
 * <p>This is the <em>only</em> class that calls {@link System#exit}.
 * Everything else returns an {@code ExitCode}; that keeps {@code Cli}
 * testable without spawning a JVM.
 */
public final class Main {

    private Main() {
        // entry point; not instantiable
    }

    public static void main(String[] args) {
        int exitCode = Cli.run(args).value();
        System.exit(exitCode);
    }
}