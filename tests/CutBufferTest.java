package br.redhot.dragonet.tui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutBufferTest {

    @Test
    void startsEmpty() {
        CutBuffer buffer = new CutBuffer();
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.count());
    }

    @Test
    void setAndClear() {
        CutBuffer buffer = new CutBuffer();
        buffer.set(CutBuffer.Mode.MOVE,
                List.of(Path.of("/a"), Path.of("/b")));
        assertEquals(2, buffer.count());
        assertEquals(CutBuffer.Mode.MOVE, buffer.mode());
        buffer.clear();
        assertTrue(buffer.isEmpty());
    }

    @Test
    void overwriteReplacesContents() {
        CutBuffer buffer = new CutBuffer();
        buffer.set(CutBuffer.Mode.MOVE, List.of(Path.of("/a")));
        buffer.set(CutBuffer.Mode.COPY, List.of(Path.of("/b"), Path.of("/c")));
        assertEquals(CutBuffer.Mode.COPY, buffer.mode());
        assertEquals(2, buffer.count());
    }
}
