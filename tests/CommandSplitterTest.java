package br.redhot.dragonet.tui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandSplitterTest {

    @Test
    void simpleCommand() {
        assertEquals(List.of("vim", "file.txt"),
                CommandSplitter.split("vim file.txt"));
    }

    @Test
    void doubleQuotesKeepSpaces() {
        assertEquals(List.of("vim", "my file.txt"),
                CommandSplitter.split("vim \"my file.txt\""));
    }

    @Test
    void singleQuotesKeepSpaces() {
        assertEquals(List.of("cat", "a b c"),
                CommandSplitter.split("cat 'a b c'"));
    }

    @Test
    void escapedSpaceOutsideQuotes() {
        assertEquals(List.of("vim", "my file.txt"),
                CommandSplitter.split("vim my\\ file.txt"));
    }

    @Test
    void multipleArguments() {
        assertEquals(List.of("cp", "src", "dst", "-v"),
                CommandSplitter.split("cp src dst -v"));
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertTrue(CommandSplitter.split("").isEmpty());
        assertTrue(CommandSplitter.split("   ").isEmpty());
        assertTrue(CommandSplitter.split(null).isEmpty());
    }

    @Test
    void unterminatedQuoteThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> CommandSplitter.split("vim \"unterminated"));
    }

    @Test
    void shellCommandMarkerDetected() {
        assertTrue(CommandSplitter.isShellCommand("!ls -la"));
        assertFalse(CommandSplitter.isShellCommand("ls -la"));
        assertFalse(CommandSplitter.isShellCommand(""));
    }

    @Test
    void emptyQuotedStringIsToken() {
        assertEquals(List.of("vim", ""),
                CommandSplitter.split("vim \"\""));
    }

    @Test
    void backslashEscapesQuote() {
        assertEquals(List.of("echo", "a\"b"),
                CommandSplitter.split("echo \"a\\\"b\""));
    }
}
