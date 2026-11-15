package br.redhot.dragonet.undo;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One entry in the undo journal.
 *
 * <p>Wire format (one line per entry):
 * {@code <epoch> <kind> key1=value1 key2=value2 ...} where values are
 * URL-encoded. Designed for append-only writing with a single
 * {@code fsync} per entry and for robust round-tripping of paths that
 * contain spaces, quotes, newlines or non-UTF-8 bytes.
 */
public record UndoEntry(long epochSeconds, OperationKind kind, Map<String, String> arguments) {

    public enum OperationKind { MOVE, RENAME, TRASH, MKDIR, UNDO }

    public UndoEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(arguments, "arguments");
        arguments = Map.copyOf(arguments);
    }

    public static UndoEntry move(Path source, Path target, long epochSeconds) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("source", source.toString());
        args.put("target", target.toString());
        return new UndoEntry(epochSeconds, OperationKind.MOVE, args);
    }

    public static UndoEntry rename(Path source, Path target, long epochSeconds) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("source", source.toString());
        args.put("target", target.toString());
        return new UndoEntry(epochSeconds, OperationKind.RENAME, args);
    }

    public static UndoEntry trash(Path originalPath, String uuid, long epochSeconds) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("original", originalPath.toString());
        args.put("uuid", uuid);
        return new UndoEntry(epochSeconds, OperationKind.TRASH, args);
    }

    public static UndoEntry mkdir(Path path, long epochSeconds) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("path", path.toString());
        return new UndoEntry(epochSeconds, OperationKind.MKDIR, args);
    }

    public static UndoEntry undo(long targetEpochSeconds, long epochSeconds) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("target_epoch", Long.toString(targetEpochSeconds));
        return new UndoEntry(epochSeconds, OperationKind.UNDO, args);
    }

    public String format() {
        StringBuilder builder = new StringBuilder();
        builder.append(epochSeconds).append(' ').append(kind.name().toLowerCase());
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            builder.append(' ').append(entry.getKey()).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    public static UndoEntry parse(String line) {
        if (line == null || line.isBlank() || line.startsWith("#")) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        long epoch;
        try {
            epoch = Long.parseLong(parts[0]);
        } catch (NumberFormatException exception) {
            return null;
        }
        OperationKind kind;
        try {
            kind = OperationKind.valueOf(parts[1].toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        Map<String, String> args = new LinkedHashMap<>();
        for (int index = 2; index < parts.length; index = index + 1) {
            int equalsIndex = parts[index].indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String key = parts[index].substring(0, equalsIndex);
            String value = URLDecoder.decode(
                    parts[index].substring(equalsIndex + 1), StandardCharsets.UTF_8);
            args.put(key, value);
        }
        return new UndoEntry(epoch, kind, args);
    }
}
