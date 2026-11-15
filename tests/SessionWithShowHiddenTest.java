package br.redhot.dragonet.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionWithShowHiddenTest {

    @Test
    void withShowHiddenReturnsNewInstance() {
        Session original = Session.defaults();
        Session flipped = original.withShowHidden(true);
        assertFalse(original.showHidden(), "original must be untouched");
        assertTrue(flipped.showHidden(), "flipped must have new value");
    }
}
