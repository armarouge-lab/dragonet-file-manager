package br.redhot.dragonet.tui;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptTest {

    @Test
    void typedCharactersAppend() {
        Prompt prompt = new Prompt("name: ");
        prompt.handleCharacter('a', false, false);
        prompt.handleCharacter('b', false, false);
        prompt.handleCharacter('c', false, false);
        assertEquals("abc", prompt.text());
        assertEquals(3, prompt.cursorPosition());
    }

    @Test
    void initialTextPrefillsAtEnd() {
        Prompt prompt = new Prompt("rename: ", "hello.txt");
        assertEquals("hello.txt", prompt.text());
        assertEquals(9, prompt.cursorPosition());
    }

    @Test
    void enterSubmits() {
        Prompt prompt = new Prompt("name: ");
        prompt.handleCharacter('a', false, false);
        Prompt.Result result = prompt.handleKey(new KeyStroke(KeyType.Enter));
        assertEquals(Prompt.Result.Status.SUBMITTED, result.status());
        assertEquals("a", result.text());
    }

    @Test
    void escapeCancels() {
        Prompt prompt = new Prompt("name: ");
        Prompt.Result result = prompt.handleKey(new KeyStroke(KeyType.Escape));
        assertEquals(Prompt.Result.Status.CANCELLED, result.status());
    }

    @Test
    void ctrlCCancels() {
        Prompt prompt = new Prompt("name: ");
        Prompt.Result result = prompt.handleCharacter('c', true, false);
        assertEquals(Prompt.Result.Status.CANCELLED, result.status());
    }

    @Test
    void ctrlXClosesApp() {
        Prompt prompt = new Prompt("name: ");
        Prompt.Result result = prompt.handleCharacter('x', true, false);
        assertEquals(Prompt.Result.Status.CLOSE_APP, result.status());
    }

    @Test
    void backspaceRemovesPreviousChar() {
        Prompt prompt = new Prompt("name: ", "abc");
        prompt.handleKey(new KeyStroke(KeyType.Backspace));
        assertEquals("ab", prompt.text());
        assertEquals(2, prompt.cursorPosition());
    }

    @Test
    void backspaceAtStartIsNoOp() {
        Prompt prompt = new Prompt("name: ");
        prompt.handleKey(new KeyStroke(KeyType.Backspace));
        assertEquals("", prompt.text());
    }

    @Test
    void deleteRemovesCurrentChar() {
        Prompt prompt = new Prompt("name: ", "abc");
        prompt.handleKey(new KeyStroke(KeyType.Home));
        prompt.handleKey(new KeyStroke(KeyType.Delete));
        assertEquals("bc", prompt.text());
    }

    @Test
    void arrowsMoveCursor() {
        Prompt prompt = new Prompt("name: ", "abc");
        prompt.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals(2, prompt.cursorPosition());
        prompt.handleKey(new KeyStroke(KeyType.ArrowLeft));
        prompt.handleKey(new KeyStroke(KeyType.ArrowLeft));
        prompt.handleKey(new KeyStroke(KeyType.ArrowLeft));
        assertEquals(0, prompt.cursorPosition());
        prompt.handleKey(new KeyStroke(KeyType.ArrowRight));
        assertEquals(1, prompt.cursorPosition());
    }

    @Test
    void ctrlKTruncatesToCursor() {
        Prompt prompt = new Prompt("name: ", "hello.txt");
        prompt.handleKey(new KeyStroke(KeyType.Home));
        prompt.handleCharacter('h', false, false);
        prompt.handleCharacter('i', false, false);
        prompt.handleCharacter('h', true, false);  // ^H: backspace
        prompt.handleCharacter('k', true, false);  // ^K: truncate from cursor
        assertEquals("h", prompt.text());
    }

    @Test
    void ctrlUClears() {
        Prompt prompt = new Prompt("name: ", "hello");
        prompt.handleCharacter('u', true, false);
        assertEquals("", prompt.text());
        assertEquals(0, prompt.cursorPosition());
    }

    @Test
    void ctrlAAndCtrlEMoveToEnds() {
        Prompt prompt = new Prompt("name: ", "abc");
        prompt.handleCharacter('a', true, false);
        assertEquals(0, prompt.cursorPosition());
        prompt.handleCharacter('e', true, false);
        assertEquals(3, prompt.cursorPosition());
    }

    @Test
    void altCharactersIgnored() {
        Prompt prompt = new Prompt("name: ");
        prompt.handleCharacter('a', false, true);
        assertEquals("", prompt.text());
    }
}