package br.redhot.dragonet.engine;

import br.redhot.dragonet.env.PathResolver;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Recursive copy from a single source to a destination path.
 *
 * <p>Symlinks are recreated as symlinks (never followed). Attributes
 * are preserved where the filesystem supports it. A pre-existing
 * destination triggers {@link OperationResult#targetExists(String)}
 * unless {@code overwrite} is true, in which case the copy replaces it.
 *
 * <p>Sparse detection is exposed via {@link #isSparse(Path)} so the
 * caller can prompt the user before the copy starts; the operation
 * itself does not prompt.
 */
public final class CopyOperation implements Operation {

    private final Path source;
    private final Path target;
    private final Path homeDirectory;
    private final boolean overwrite;

    public CopyOperation(Path source, Path target, Path homeDirectory, boolean overwrite) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory");
        this.overwrite = overwrite;
    }

    public static boolean isSparse(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                return false;
            }
            Object blocks = Files.getAttribute(path, "unix:blocks");
            if (blocks instanceof Number) {
                long allocated = ((Number) blocks).longValue() * 512L;
                return allocated < attributes.size();
            }
        } catch (Exception ignored) {
            // Not a Unix filesystem or attribute not readable.
        }
        return false;
    }

    @Override
    public OperationResult execute() {
        if (PathResolver.isProhibitedForDestructive(target, homeDirectory)) {
            return OperationResult.failure("refusing to write to protected path: " + target);
        }
        if (PathResolver.isAncestorOf(source, target)) {
            return OperationResult.failure("refusing to copy into itself: " + target);
        }
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !overwrite) {
                return OperationResult.targetExists("exists: " + target.getFileName());
            }
            copyEntry(source, target, overwrite);
            return OperationResult.success("Copied: " + source.getFileName());
        } catch (FileAlreadyExistsException exception) {
            return OperationResult.targetExists("exists: " + target.getFileName());
        } catch (IOException exception) {
            return OperationResult.failure("copy failed: " + exception.getMessage());
        }
    }

    private static void copyEntry(Path source, Path target, boolean overwrite) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

        if (attributes.isSymbolicLink()) {
            Path linkTarget = Files.readSymbolicLink(source);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !overwrite) {
                throw new FileAlreadyExistsException(target.toString());
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(target);
            }
            Files.createSymbolicLink(target, linkTarget);
            return;
        }

        if (attributes.isDirectory()) {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(target);
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path child : stream) {
                    copyEntry(child, target.resolve(child.getFileName()), overwrite);
                }
            }
            return;
        }

        if (attributes.isRegularFile()) {
            StandardCopyOption[] options = overwrite
                    ? new StandardCopyOption[]{StandardCopyOption.COPY_ATTRIBUTES,
                        StandardCopyOption.REPLACE_EXISTING}
                    : new StandardCopyOption[]{StandardCopyOption.COPY_ATTRIBUTES};
            Files.copy(source, target, options);
            return;
        }

        // FIFOs, sockets, devices: skip silently to avoid blocking I/O.
        Files.deleteIfExists(target);
    }
}
