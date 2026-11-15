package br.redhot.dragonet.engine;

import br.redhot.dragonet.env.PathResolver;

import java.nio.file.FileAlreadyExistsException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Creates an empty file. Fails if the target already exists. */
public final class CreateFileOperation implements Operation {

    private final Path target;
    private final Path homeDirectory;

    public CreateFileOperation(Path target, Path homeDirectory) {
        this.target = Objects.requireNonNull(target, "target");
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
    }

    @Override
    public OperationResult execute() {
        if (PathResolver.isProhibitedForDestructive(target, homeDirectory)) {
            return OperationResult.failure(
                    "Refusing to create file in protected path: " + target);
        }
        try {
            Files.createFile(target);
            return OperationResult.success("Created file: " + target.getFileName());
        } catch (FileAlreadyExistsException exception) {
            return OperationResult.targetExists(
                    "Already exists: " + target.getFileName());
        } catch (IOException exception) {
            return OperationResult.failure("touch failed: " + exception.getMessage());
        }
    }
}