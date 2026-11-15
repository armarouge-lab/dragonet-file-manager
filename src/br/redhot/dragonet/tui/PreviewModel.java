package br.redhot.dragonet.tui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates preview content for one entry.
 *
 * <p>Pure logic, no Lanterna. The cache is keyed by
 * {@code (path, mtime, size)} so a cursor moving through a directory
 * does not re-read the same file on every frame, but a modified file
 * gets a fresh preview.
 */
public final class PreviewModel {

    public enum PreviewKind { EMPTY, DIRECTORY, TEXT, BINARY, IMAGE, ERROR }

    public record Preview(PreviewKind kind, List<String> lines, String footer) {
        public Preview {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(lines, "lines");
            Objects.requireNonNull(footer, "footer");
        }

        public static Preview empty() {
            return new Preview(PreviewKind.EMPTY, List.of("(no preview)"), "");
        }

        public static Preview error(String message) {
            return new Preview(PreviewKind.ERROR, List.of(message), "");
        }
    }

    private final long maxBytes;

    private Path cachedPath;
    private Instant cachedMtime;
    private long cachedSize;
    private Preview cachedPreview;

    public PreviewModel(long maxBytes) {
        this.maxBytes = Math.max(256L, maxBytes);
    }

    public Preview preview(FileEntry entry) {
        if (entry == null || entry.isParent()) {
            return Preview.empty();
        }
        Path path = entry.path();
        Instant mtime = entry.lastModified();
        long size = entry.sizeBytes();
        if (path.equals(cachedPath) && mtime.equals(cachedMtime) && size == cachedSize) {
            return cachedPreview;
        }
        Preview fresh = generate(entry);
        cachedPath = path;
        cachedMtime = mtime;
        cachedSize = size;
        cachedPreview = fresh;
        return fresh;
    }

    private Preview generate(FileEntry entry) {
        try {
            if (entry.isDirectory()) {
                return directoryPreview(entry);
            }
            if (entry.isSymlink()) {
                return symlinkPreview(entry);
            }
            if (!Files.isRegularFile(entry.path(), LinkOption.NOFOLLOW_LINKS)) {
                return new Preview(PreviewKind.BINARY, List.of("(special file)"), "");
            }
            switch (FileTypeDetector.detect(entry.path())) {
                case IMAGE -> {
                    return new Preview(PreviewKind.IMAGE,
                            List.of("Image file", formatSize(entry.sizeBytes())),
                            "install chafa for thumbnails");
                }
                case TEXT -> {
                    return textPreview(entry);
                }
                case BINARY -> {
                    return binaryPreview(entry);
                }
                default -> {
                    return Preview.empty();
                }
            }
        } catch (IOException exception) {
            return Preview.error("Cannot read: " + exception.getMessage());
        }
    }

    private Preview directoryPreview(FileEntry entry) throws IOException {
        int entries = 0;
        long totalBytes = 0L;
        boolean truncated = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(entry.path())) {
            for (Path child : stream) {
                if (entries >= 10_000) {
                    truncated = true;
                    break;
                }
                entries = entries + 1;
                try {
                    if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                        totalBytes = totalBytes + Files.size(child);
                    }
                } catch (IOException ignored) {
                    // Unreadable child, skip.
                }
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(entries + (entries == 1 ? " item" : " items"));
        lines.add("Total: " + formatSize(totalBytes));
        if (truncated) {
            lines.add("(truncated at 10000 entries)");
        }
        return new Preview(PreviewKind.DIRECTORY, lines, "");
    }

    private Preview symlinkPreview(FileEntry entry) {
        List<String> lines = new ArrayList<>();
        lines.add("Symlink to: " + entry.symlinkTarget());
        if (entry.isBrokenSymlink()) {
            lines.add("(target does not exist)");
        }
        return new Preview(PreviewKind.DIRECTORY, lines, "");
    }

    private Preview textPreview(FileEntry entry) throws IOException {
        long effectiveLimit = Math.min(maxBytes, entry.sizeBytes());
        if (effectiveLimit <= 0) {
            return new Preview(PreviewKind.TEXT, List.of("(empty file)"), "");
        }
        List<String> lines = new ArrayList<>();
        try (InputStream stream = Files.newInputStream(entry.path())) {
            byte[] buffer = new byte[(int) effectiveLimit];
            int read = stream.read(buffer);
            if (read <= 0) {
                return new Preview(PreviewKind.TEXT, List.of("(empty file)"), "");
            }
            String content = new String(buffer, 0, read, StandardCharsets.UTF_8);
            for (String line : content.split("\n", -1)) {
                String cleaned = line;
                if (cleaned.length() > 400) {
                    cleaned = cleaned.substring(0, 399) + "\u2026";
                }
                lines.add(cleaned);
            }
            if (entry.sizeBytes() > effectiveLimit) {
                lines.add("");
                lines.add("[truncated at " + formatSize(effectiveLimit) + "]");
            }
        }
        return new Preview(PreviewKind.TEXT, lines, "");
    }

    private Preview binaryPreview(FileEntry entry) throws IOException {
        int limit = (int) Math.min(256L, entry.sizeBytes());
        StringBuilder hex = new StringBuilder();
        try (InputStream stream = Files.newInputStream(entry.path())) {
            byte[] buffer = new byte[limit];
            int read = stream.read(buffer);
            for (int index = 0; index < read; index = index + 1) {
                if (index > 0 && index % 16 == 0) {
                    hex.append('\n');
                } else if (index > 0 && index % 8 == 0) {
                    hex.append(' ');
                }
                hex.append(String.format("%02x ", buffer[index] & 0xFF));
            }
        }
        List<String> lines = new ArrayList<>();
        for (String line : hex.toString().split("\n")) {
            lines.add(line);
        }
        return new Preview(PreviewKind.BINARY, lines, "");
    }

    private static String formatSize(long bytes) {
        long divisor = 1024L;
        if (bytes < divisor) {
            return bytes + " B";
        }
        String[] units = {"K", "M", "G", "T"};
        double value = bytes;
        int unitIndex = -1;
        while (value >= divisor && unitIndex < units.length - 1) {
            value = value / divisor;
            unitIndex = unitIndex + 1;
        }
        if (value < 10.0) {
            return String.format("%.1f %sB", value, units[unitIndex]);
        }
        return String.format("%.0f %sB", value, units[unitIndex]);
    }
}
