package br.redhot.dragonet.engine;

import br.redhot.dragonet.env.PathResolver;

import java.nio.file.FileAlreadyExistsException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Creates a single directory. Fails if the parent is missing. */
public final class CreateDirectoryOperation implements Operation {

    private final Path target;
    private final Path homeDirectory;

    public CreateDirectoryOperation(Path target, Path homeDirectory) {
        this.target = Objects.requireNonNull(target, "target");
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
    }

    @Override
    public OperationResult execute() {
        if (PathResolver.isProhibitedForDestructive(target, homeDirectory)) {
            return OperationResult.failure(
                    "Refusing to create directory in protected path: " + target);
        }
        try {
            Files.createDirectory(target);
            return OperationResult.success("Created directory: " + target.getFileName());
        } catch (FileAlreadyExistsException exception) {
            return OperationResult.targetExists(
                    "Already exists: " + target.getFileName());
        } catch (IOException exception) {
            return OperationResult.failure("mkdir failed: " + exception.getMessage());
        }
    }
}