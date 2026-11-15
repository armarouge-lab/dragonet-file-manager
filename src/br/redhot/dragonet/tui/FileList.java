package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The list of entries shown for one directory, plus a cursor.
 *
 * <p>Immutable directory and entries; only the cursor index mutates.
 * Navigation up or down changes the cursor, not the list. Navigating
 * into a directory produces a new {@code FileList} via {@link #load}.
 */
public final class FileList {

    private final Path directory;
    private final List<FileEntry> entries;
    private int cursorIndex;

    private FileList(Path directory, List<FileEntry> entries, int cursorIndex) {
        this.directory = directory;
        this.entries = entries;
        this.cursorIndex = cursorIndex;
    }

    /**
     * Reads a directory and produces a flat list.
     *
     * <p>The synthetic {@code ..} entry is prepended unless the
     * directory is a filesystem root. Hidden entries are skipped when
     * {@code config.showHidden()} is false. Entries are sorted with
     * directories first, then by the configured key.
     *
     * @param directory the directory to read
     * @param config    the configuration driving filtering and sorting
     * @return a populated {@code FileList} with the cursor on the first
     *         non-{@code ..} entry, or on {@code ..} if it is the only one
     * @throws IOException if the directory cannot be read
     */
    public static FileList load(Path directory, Config config) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(config, "config");

        Path absoluteDirectory = directory.toAbsolutePath().normalize();
        List<FileEntry> entries = new ArrayList<>();

        Path parent = absoluteDirectory.getParent();
        if (parent != null) {
            entries.add(createParentEntry(parent));
        }

        List<FileEntry> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(absoluteDirectory)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                boolean isHidden = name.startsWith(".");
                if (isHidden && !config.showHidden()) {
                    continue;
                }
                children.add(createEntry(child, isHidden));
            }
        }

        Collator collator = config.sortLocale() ? Collator.getInstance() : null;
        children.sort(buildComparator(config, collator));
        entries.addAll(children);

        int initialCursor = 0;
        if (!entries.isEmpty() && entries.get(0).isParent() && entries.size() > 1) {
            initialCursor = 1;
        }

        return new FileList(absoluteDirectory, entries, initialCursor);
    }

    public Path directory() {
        return directory;
    }

    public List<FileEntry> entries() {
        return entries;
    }

    public int cursorIndex() {
        return cursorIndex;
    }

    public FileEntry current() {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.get(cursorIndex);
    }

    public void moveUp() {
        if (cursorIndex > 0) {
            cursorIndex = cursorIndex - 1;
        }
    }

    public void moveDown() {
        if (cursorIndex < entries.size() - 1) {
            cursorIndex = cursorIndex + 1;
        }
    }

    public void moveToFirst() {
        if (!entries.isEmpty()) {
            cursorIndex = 0;
        }
    }

    public void moveToLast() {
        if (!entries.isEmpty()) {
            cursorIndex = entries.size() - 1;
        }
    }

    /**
     * Moves the cursor to the entry with the given name.
     *
     * <p>No-op if no such entry exists. Used to restore the cursor to
     * the directory the user just left.
     *
     * @param name the basename to look for
     */
    public void moveToEntryNamed(String name) {
        for (int index = 0; index < entries.size(); index = index + 1) {
            if (entries.get(index).name().equals(name)) {
                cursorIndex = index;
                return;
            }
        }
    }

    private static FileEntry createParentEntry(Path parent) {
        return new FileEntry("..", parent, FileEntry.FileType.DIRECTORY,
                0L, Instant.EPOCH, false, false, false, null, false, false);
    }

    private static FileEntry createEntry(Path child, boolean isHidden) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

        String name = child.getFileName().toString();
        long sizeBytes = attributes.size();
        Instant lastModified = attributes.lastModifiedTime().toInstant();

        if (attributes.isSymbolicLink()) {
            Path symlinkTarget = null;
            try {
                symlinkTarget = Files.readSymbolicLink(child);
            } catch (IOException ignored) {
                // Target unreadable; keep null.
            }
            boolean isBrokenSymlink = false;
            boolean isSymlinkToDirectory = false;
            try {
                BasicFileAttributes targetAttributes = Files.readAttributes(
                        child, BasicFileAttributes.class);
                isSymlinkToDirectory = targetAttributes.isDirectory();
            } catch (IOException exception) {
                isBrokenSymlink = true;
            }
            return new FileEntry(name, child, FileEntry.FileType.SYMLINK, sizeBytes,
                    lastModified, isHidden, false, true, symlinkTarget,
                    isBrokenSymlink, isSymlinkToDirectory);
        }

        if (attributes.isDirectory()) {
            return new FileEntry(name, child, FileEntry.FileType.DIRECTORY, sizeBytes,
                    lastModified, isHidden, false, false, null, false, false);
        }

        if (attributes.isRegularFile()) {
            boolean isExecutable = Files.isExecutable(child);
            return new FileEntry(name, child, FileEntry.FileType.FILE, sizeBytes,
                    lastModified, isHidden, isExecutable, false, null, false, false);
        }

        return new FileEntry(name, child, FileEntry.FileType.OTHER, sizeBytes,
                lastModified, isHidden, false, false, null, false, false);
    }

    private static Comparator<FileEntry> buildComparator(Config config, Collator collator) {
        Comparator<FileEntry> byKey = switch (config.sort()) {
            case NAME -> collator != null
                    ? (a, b) -> collator.compare(a.name(), b.name())
                    : Comparator.comparing(FileEntry::name);
            case SIZE -> Comparator.comparingLong(FileEntry::sizeBytes);
            case MODIFIED -> Comparator.comparing(FileEntry::lastModified);
            case TYPE -> Comparator
                    .comparing((FileEntry entry) -> extensionOf(entry.name()))
                    .thenComparing(FileEntry::name);
        };
        if (config.sortReverse()) {
            byKey = byKey.reversed();
        }
        return Comparator
                .comparing((FileEntry entry) -> !entry.isDirectory())
                .thenComparing(byKey);
    }

    private static String extensionOf(String name) {
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex <= 0) {
            return "";
        }
        return name.substring(dotIndex + 1);
    }
}