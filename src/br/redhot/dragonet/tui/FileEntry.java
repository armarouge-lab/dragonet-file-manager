package br.redhot.dragonet.tui;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * One row in the file list.
 *
 * <p>Captured once at load time so the renderer does not touch the
 * filesystem. Symlinks carry their target and broken-ness so the
 * renderer can color them without extra I/O.
 */
public record FileEntry(
        String name,
        Path path,
        FileType type,
        long sizeBytes,
        Instant lastModified,
        boolean isHidden,
        boolean isExecutable,
        boolean isSymlink,
        Path symlinkTarget,
        boolean isBrokenSymlink,
        boolean isSymlinkToDirectory
) {

    public FileEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lastModified, "lastModified");
    }

    public enum FileType {
        DIRECTORY, FILE, SYMLINK, OTHER
    }

    public boolean isDirectory() {
        return type == FileType.DIRECTORY;
    }

    /**
     * Returns whether {@code Enter} should navigate into this entry.
     *
     * <p>True for real directories and for symlinks that resolve to a
     * directory. A broken symlink is never navigable.
     *
     * @return {@code true} if the entry can be entered
     */
    public boolean isNavigable() {
        if (type == FileType.DIRECTORY) {
            return true;
        }
        return type == FileType.SYMLINK && !isBrokenSymlink && isSymlinkToDirectory;
    }

    /**
     * Returns whether this is the synthetic {@code ..} entry.
     *
     * @return {@code true} for the parent entry
     */
    public boolean isParent() {
        return name.equals("..") && type == FileType.DIRECTORY;
    }
}