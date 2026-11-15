package br.redhot.dragonet.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigWithShowHiddenTest {

    @Test
    void withShowHiddenReturnsNewInstance() {
        Config original = Config.defaults();
        Config flipped = original.withShowHidden(true);
        assertFalse(original.showHidden(), "original must be untouched");
        assertTrue(flipped.showHidden(), "flipped must have new value");
    }

    @Test
    void withShowHiddenPreservesOtherFields() {
        Config original = Config.defaults();
        Config flipped = original.withShowHidden(true);
        assertFalse(flipped.preview());
        assertTrue(flipped.trashEnabled());
        assertTrue(flipped.confirmDelete());
    }
}
