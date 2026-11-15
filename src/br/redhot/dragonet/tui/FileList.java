package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FileList {

    private final Path directory;
    private final List<FileEntry> entries;
    private int cursorIndex;

    private FileList(Path directory, List<FileEntry> entries, int cursorIndex) {
        this.directory = directory;
        this.entries = entries;
        this.cursorIndex = cursorIndex;
    }

    public static FileList load(Path directory, Config config) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(config, "config");

        Path absoluteDirectory = directory.toAbsolutePath().normalize();
        List<FileEntry> entries = new ArrayList<>();

        Path parent = absoluteDirectory.getParent();
        if (parent != null) {
            entries.add(createParentEntry(parent));
        }

        entries.addAll(loadChildren(absoluteDirectory, config));

        int initialCursor = 0;
        if (!entries.isEmpty() && entries.get(0).isParent() && entries.size() > 1) {
            initialCursor = 1;
        }

        return new FileList(absoluteDirectory, entries, initialCursor);
    }

    public static List<FileEntry> loadChildren(Path directory, Config config) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(config, "config");

        List<FileEntry> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
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
        return children;
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
                0L, Instant.EPOCH, false, false, false, null, false, false,
                "", "");
    }

    private static FileEntry createEntry(Path child, boolean isHidden) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

        String name = child.getFileName().toString();
        long sizeBytes = attributes.size();
        Instant lastModified = attributes.lastModifiedTime().toInstant();
        String modeString = "";
        String ownerName = "";

        try {
            PosixFileAttributes posixAttributes = Files.readAttributes(
                    child, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (posixAttributes != null) {
                char typeCharacter;
                if (attributes.isDirectory()) {
                    typeCharacter = 'd';
                } else if (attributes.isSymbolicLink()) {
                    typeCharacter = 'l';
                } else if (attributes.isRegularFile()) {
                    typeCharacter = '-';
                } else {
                    typeCharacter = '?';
                }
                modeString = typeCharacter
                        + PosixFilePermissions.toString(posixAttributes.permissions());
                UserPrincipal owner = posixAttributes.owner();
                if (owner != null) {
                    ownerName = owner.getName();
                }
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem or unreadable: leave empty.
        }

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
                    isBrokenSymlink, isSymlinkToDirectory, modeString, ownerName);
        }

        if (attributes.isDirectory()) {
            return new FileEntry(name, child, FileEntry.FileType.DIRECTORY, sizeBytes,
                    lastModified, isHidden, false, false, null, false, false,
                    modeString, ownerName);
        }

        if (attributes.isRegularFile()) {
            boolean isExecutable = Files.isExecutable(child);
            return new FileEntry(name, child, FileEntry.FileType.FILE, sizeBytes,
                    lastModified, isHidden, isExecutable, false, null, false, false,
                    modeString, ownerName);
        }

        return new FileEntry(name, child, FileEntry.FileType.OTHER, sizeBytes,
                lastModified, isHidden, false, false, null, false, false,
                modeString, ownerName);
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