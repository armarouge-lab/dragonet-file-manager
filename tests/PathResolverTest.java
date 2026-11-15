package br.redhot.dragonet.tui;

import br.redhot.dragonet.env.PathResolver;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathResolverTest {

    private static final Path HOME = Path.of("/home/testuser");

    @Test
    void rootIsProhibited() {
        assertTrue(PathResolver.isProhibitedForDestructive(Path.of("/"), HOME));
    }

    @Test
    void systemDirsAreProhibited() {
        assertTrue(PathResolver.isProhibitedForDestructive(Path.of("/usr"), HOME));
        assertTrue(PathResolver.isProhibitedForDestructive(Path.of("/etc"), HOME));
        assertTrue(PathResolver.isProhibitedForDestructive(Path.of("/var/log"), HOME));
        assertTrue(PathResolver.isProhibitedForDestructive(Path.of("/boot"), HOME));
    }

    @Test
    void homeItselfIsProhibited() {
        assertTrue(PathResolver.isProhibitedForDestructive(HOME, HOME));
    }

    @Test
    void homeSubpathsAreAllowed() {
        assertFalse(PathResolver.isProhibitedForDestructive(
                HOME.resolve("Downloads"), HOME));
        assertFalse(PathResolver.isProhibitedForDestructive(
                HOME.resolve("Downloads/file.txt"), HOME));
    }

    @Test
    void tmpIsAllowed() {
        assertFalse(PathResolver.isProhibitedForDestructive(Path.of("/tmp/foo"), HOME));
    }

    @Test
    void ancestorOfDetection() {
        assertTrue(PathResolver.isAncestorOf(Path.of("/tmp"), Path.of("/tmp/foo")));
        assertTrue(PathResolver.isAncestorOf(Path.of("/tmp"), Path.of("/tmp/foo/bar")));
        assertFalse(PathResolver.isAncestorOf(Path.of("/tmp"), Path.of("/tmp")));
        assertFalse(PathResolver.isAncestorOf(Path.of("/tmp"), Path.of("/other")));
    }
}