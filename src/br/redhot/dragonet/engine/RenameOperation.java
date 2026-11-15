package br.redhot.dragonet.engine;

import br.redhot.dragonet.env.PathResolver;

import java.nio.file.FileAlreadyExistsException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Renames a single entry to a new basename in the same directory.
 *
 * <p>Same-name renames are treated as no-ops. If the target already
 * exists and {@code overwrite} is false, the operation returns
 * {@link OperationResult#targetExists(String)} so the caller can prompt
 * for confirmation.
 */
public final class RenameOperation implements Operation {

    private final Path source;
    private final String newName;
    private final Path homeDirectory;
    private final boolean overwrite;

    public RenameOperation(Path source, String newName, Path homeDirectory, boolean overwrite) {
        this.source = Objects.requireNonNull(source, "source");
        this.newName = Objects.requireNonNull(newName, "newName");
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
        this.overwrite = overwrite;
    }

    @Override
    public OperationResult execute() {
        String currentName = source.getFileName() == null
                ? "" : source.getFileName().toString();
        if (newName.equals(currentName)) {
            return OperationResult.success("No change: " + currentName);
        }
        if (newName.isBlank() || newName.contains("/")) {
            return OperationResult.failure("Invalid name: " + newName);
        }
        if (PathResolver.isProhibitedForDestructive(source, homeDirectory)) {
            return OperationResult.failure(
                    "Refusing to rename protected path: " + source);
        }
        Path target = source.resolveSibling(newName);
        try {
            if (overwrite) {
                Files.move(source, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(source, target);
            }
            return OperationResult.success("Renamed to: " + newName);
        } catch (FileAlreadyExistsException exception) {
            return OperationResult.targetExists(
                    "Already exists: " + newName);
        } catch (IOException exception) {
            return OperationResult.failure("rename failed: " + exception.getMessage());
        }
    }
}