package br.redhot.dragonet.tui;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * One row in the file list.
 *
 * <p>Captured once at load time so the renderer does not touch the
 * filesystem. Symlinks carry their target and broken-ness so the
 * renderer can color them without extra I/O. POSIX mode and owner are
 * pre-formatted strings (empty on non-POSIX filesystems).
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
        boolean isSymlinkToDirectory,
        String modeString,
        String ownerName
) {

    public FileEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lastModified, "lastModified");
        Objects.requireNonNull(modeString, "modeString");
        Objects.requireNonNull(ownerName, "ownerName");
    }

    public enum FileType {
        DIRECTORY, FILE, SYMLINK, OTHER
    }

    public boolean isDirectory() {
        return type == FileType.DIRECTORY;
    }

    public boolean isNavigable() {
        if (type == FileType.DIRECTORY) {
            return true;
        }
        return type == FileType.SYMLINK && !isBrokenSymlink && isSymlinkToDirectory;
    }

    public boolean isParent() {
        return name.equals("..") && type == FileType.DIRECTORY;
    }
}