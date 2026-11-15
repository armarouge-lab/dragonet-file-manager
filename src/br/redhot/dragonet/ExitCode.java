package br.redhot.dragonet;

/**
 * Process exit codes for the Dragonet CLI.
 *
 * <p>Codes in the 0..7 range are project-specific. {@link #EX_USAGE}
 * follows {@code sysexits.h} ({@code EX_USAGE = 64}) so shell scripts
 * that already special-case usage errors keep working.
 *
 * <p>Every {@code main} in the project must exit through
 * {@link #value()}, never through a raw integer literal.
 */
public enum ExitCode {

    /** Operation completed successfully. */
    EX_OK(0),

    /** A syscall failed (ENOSPC, EACCES, ...). */
    EX_OP_FAILED(1),

    /** Nothing to do. */
    EX_NOTHING(2),

    /** Another instance holds the lock. */
    EX_LOCKED(3),

    /** Configuration file is invalid. */
    EX_CONFIG_ERROR(4),

    /** A precondition failed (path vanished, mount gone, ...). */
    EX_PRECOND(5),

    /** Multi-file operation completed with partial failures. */
    EX_PARTIAL(6),

    /** The user canceled the operation. */
    EX_CANCELED(7),

    /** No handler is available for the requested file type. */
    EX_NO_HANDLER(8),

    /** Command-line arguments are invalid (sysexits.h EX_USAGE). */
    EX_USAGE(64);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    /**
     * Returns the numeric value handed to {@link System#exit(int)}.
     *
     * @return the process exit code
     */
    public int value() {
        return code;
    }
}