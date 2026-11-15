package br.redhot.dragonet.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * Quote-aware command-line splitter.
 *
 * <p>Splits a user-typed command into argv without invoking a shell.
 * Supports:
 * <ul>
 *   <li>Whitespace separation</li>
 *   <li>Double quotes {@code "..."} and single quotes {@code '...'}
 *       protecting embedded whitespace</li>
 *   <li>Backslash escapes inside and outside quotes</li>
 * </ul>
 *
 * <p>A leading {@code !} marks the command as a shell command. The
 * caller must strip the {@code !} and invoke the shell explicitly; the
 * splitter never passes user input to a shell.
 */
public final class CommandSplitter {

    private CommandSplitter() {
        // static utility
    }

    /**
     * Splits {@code input} into argv tokens.
     *
     * @param input the raw user-typed line
     * @return the argv tokens; empty when input is blank
     * @throws IllegalArgumentException on unterminated quotes
     */
    public static List<String> split(String input) {
        List<String> tokens = new ArrayList<>();
        if (input == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        int index = 0;
        int length = input.length();
        boolean hasToken = false;

        while (index < length) {
            char character = input.charAt(index);
            if (Character.isWhitespace(character)) {
                if (hasToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
                index = index + 1;
                continue;
            }
            hasToken = true;
            if (character == '"' || character == '\'') {
                char quote = character;
                index = index + 1;
                boolean closed = false;
                while (index < length) {
                    char inner = input.charAt(index);
                    if (inner == '\\' && quote == '"' && index + 1 < length) {
                        char next = input.charAt(index + 1);
                        if (next == '"' || next == '\\' || next == '$' || next == '`') {
                            current.append(next);
                            index = index + 2;
                            continue;
                        }
                    }
                    if (inner == quote) {
                        closed = true;
                        index = index + 1;
                        break;
                    }
                    current.append(inner);
                    index = index + 1;
                }
                if (!closed) {
                    throw new IllegalArgumentException("unterminated quote in: " + input);
                }
                continue;
            }
            if (character == '\\' && index + 1 < length) {
                current.append(input.charAt(index + 1));
                index = index + 2;
                continue;
            }
            current.append(character);
            index = index + 1;
        }
        if (hasToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /**
     * Returns whether the input is marked as a shell command (leading {@code !}).
     *
     * @param input the raw user-typed line
     * @return {@code true} if the input starts with {@code !}
     */
    public static boolean isShellCommand(String input) {
        return input != null && !input.isEmpty() && input.charAt(0) == '!';
    }
}
