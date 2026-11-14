package br.redhot.dragonet;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Structured logger for the Dragonet CLI.
 *
 * <p>Prefixes are seven characters wide so messages align vertically:
 * <pre>
 * [info] ...
 * [ OK ] ...
 * [warn] ...
 * [FAIL] ...
 * [dbg]  ...
 * </pre>
 *
 * <p>{@code [info]} and {@code [ OK ]} go to {@code stdout};
 * {@code [warn]}, {@code [FAIL]} and {@code [dbg]} go to {@code stderr}.
 * The {@code [dbg]} stream is suppressed unless the logger was
 * constructed with {@code debugEnabled = true}.
 *
 * <p>Instances are constructed via {@link #forCli(boolean)} for normal
 * CLI use, or via {@link #forStreams(PrintStream, PrintStream, boolean)}
 * for tests that need to capture output.
 */
public final class Logger {

    private final PrintStream outStream;
    private final PrintStream errStream;
    private final boolean debugEnabled;

    private Logger(PrintStream outStream, PrintStream errStream, boolean debugEnabled) {
        this.outStream = Objects.requireNonNull(outStream, "outStream");
        this.errStream = Objects.requireNonNull(errStream, "errStream");
        this.debugEnabled = debugEnabled;
    }

    /**
     * Creates a logger bound to {@link System#out} and {@link System#err}.
     *
     * @param debugEnabled whether {@code [dbg]} messages should be emitted
     * @return a logger suitable for CLI use
     */
    public static Logger forCli(boolean debugEnabled) {
        return new Logger(System.out, System.err, debugEnabled);
    }

    /**
     * Creates a logger bound to caller-supplied streams. Intended for tests.
     *
     * @param outStream    stream for {@code [info]} and {@code [ OK ]}
     * @param errStream    stream for {@code [warn]}, {@code [FAIL]} and {@code [dbg]}
     * @param debugEnabled whether {@code [dbg]} messages should be emitted
     * @return a logger bound to the supplied streams
     */
    public static Logger forStreams(PrintStream outStream,
                                    PrintStream errStream,
                                    boolean debugEnabled) {
        return new Logger(outStream, errStream, debugEnabled);
    }

    public void info(String message) {
        outStream.printf("[info] %s%n", message);
    }

    public void ok(String message) {
        outStream.printf("[ OK ] %s%n", message);
    }

    public void warn(String message) {
        errStream.printf("[warn] %s%n", message);
    }

    public void fail(String message) {
        errStream.printf("[FAIL] %s%n", message);
    }

    public void fail(String message, Throwable cause) {
        errStream.printf("[FAIL] %s%n", message);
        if (debugEnabled && cause != null) {
            cause.printStackTrace(errStream);
        }
    }

    public void debug(String message) {
        if (debugEnabled) {
            errStream.printf("[dbg]  %s%n", message);
        }
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }
}