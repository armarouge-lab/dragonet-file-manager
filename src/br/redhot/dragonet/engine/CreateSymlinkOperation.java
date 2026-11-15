package br.redhot.dragonet.engine;

import br.redhot.dragonet.env.PathResolver;

import java.nio.file.FileAlreadyExistsException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Creates a symbolic link. Target is not validated (may be broken). */
public final class CreateSymlinkOperation implements Operation {

    private final Path linkPath;
    private final Path linkTarget;
    private final Path homeDirectory;

    public CreateSymlinkOperation(Path linkPath, Path linkTarget, Path homeDirectory) {
        this.linkPath = Objects.requireNonNull(linkPath, "linkPath");
        this.linkTarget = Objects.requireNonNull(linkTarget, "linkTarget");
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
    }

    @Override
    public OperationResult execute() {
        if (PathResolver.isProhibitedForDestructive(linkPath, homeDirectory)) {
            return OperationResult.failure(
                    "Refusing to create symlink in protected path: " + linkPath);
        }
        try {
            Files.createSymbolicLink(linkPath, linkTarget);
            return OperationResult.success("Created symlink: " + linkPath.getFileName()
                    + " -> " + linkTarget);
        } catch (FileAlreadyExistsException exception) {
            return OperationResult.targetExists(
                    "Already exists: " + linkPath.getFileName());
        } catch (IOException exception) {
            return OperationResult.failure("symlink failed: " + exception.getMessage());
        }
    }
}