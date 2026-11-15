package br.redhot.dragonet.engine;

import java.util.Objects;

/**
 * Outcome of a single filesystem operation.
 *
 * <p>{@code targetExists} is set when the operation failed only because
 * the destination already exists; the caller may then retry with
 * overwrite enabled or prompt the user.
 */
public record OperationResult(boolean success, String message, boolean targetExists) {

    public OperationResult {
        Objects.requireNonNull(message, "message");
    }

    public static OperationResult success(String message) {
        return new OperationResult(true, message, false);
    }

    public static OperationResult failure(String message) {
        return new OperationResult(false, message, false);
    }

    public static OperationResult targetExists(String message) {
        return new OperationResult(false, message, true);
    }
}