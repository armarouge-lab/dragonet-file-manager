package br.redhot.dragonet.tui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Cheap content-based file type detection for the preview pane.
 *
 * <p>No external commands, no {@code file(1)}. Reads the first
 * {@value #SNIFF_BYTES} bytes and classifies:
 * <ul>
 *   <li>{@link Kind#TEXT} if all bytes are printable UTF-8 or common
 *       whitespace (tab, newline, carriage return) and no NUL is seen.</li>
 *   <li>{@link Kind#IMAGE} if the file extension matches a known image
 *       type (content sniffing is left to V0.5b's {@code file(1)}
 *       fallback).</li>
 *   <li>{@link Kind#BINARY} otherwise.</li>
 * </ul>
 *
 * <p>Good enough for the preview pane: it never claims a PDF is text,
 * and it never tries to render a JPEG as characters.
 */
public final class FileTypeDetector {

    private static final int SNIFF_BYTES = 8192;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "tif", "tiff", "ico", "svg");

    private FileTypeDetector() {
        // static utility
    }

    public enum Kind { TEXT, BINARY, IMAGE }

    public static Kind detect(Path path) {
        if (isImageExtension(path)) {
            return Kind.IMAGE;
        }
        if (looksLikeText(path)) {
            return Kind.TEXT;
        }
        return Kind.BINARY;
    }

    public static boolean isImageExtension(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) {
            return false;
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(extension);
    }

    private static boolean looksLikeText(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[SNIFF_BYTES];
            int read = stream.read(buffer);
            if (read <= 0) {
                return true;
            }
            for (int index = 0; index < read; index = index + 1) {
                int value = buffer[index] & 0xFF;
                if (value == 0) {
                    return false;
                }
                if (value == 9 || value == 10 || value == 13) {
                    continue;
                }
                if (value < 0x20) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
