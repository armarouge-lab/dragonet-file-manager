package br.redhot.dragonet.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @Test
    void defaultsAreSane() {
        Config config = Config.defaults();
        assertEquals(1, config.version());
        assertEquals("redhot", config.theme());
        assertFalse(config.showHidden());
        assertEquals(Config.SortOrder.NAME, config.sort());
        assertFalse(config.sortReverse());
        assertFalse(config.preview());
        assertEquals(1_048_576L, config.previewMaxBytes());
        assertTrue(config.confirmDelete());
        assertTrue(config.trashEnabled());
        assertFalse(config.trashAutoTrim());
        assertEquals(30, config.trashMaxAgeDays());
        assertEquals(10L, config.trashMaxSizeGigabytes());
        assertFalse(config.followSymlinks());
        assertEquals(500, config.historyLimit());
        assertEquals(100, config.undoLimit());
        assertEquals(Config.Humanize.BINARY, config.humanize());
        assertTrue(config.dateRelative());
        assertTrue(config.sortLocale());
        assertTrue(config.watchDirectory());
        assertEquals(Config.TreeStyle.AUTO, config.treeStyle());
        assertEquals(Config.ImpMode.AUTO, config.impMode());
        assertEquals(0, config.infoLevel());
        assertEquals("chafa", config.chafaCommand());
        assertEquals("file", config.fileCommand());
        assertEquals("", config.openTextEditor());
        assertEquals("", config.openMediaPlayer());
    }

    @Test
    void builderDefaultsMatchConfigDefaults() {
        Config fromDefaults = Config.defaults();
        Config fromBuilder = new ConfigBuilder().build();
        assertEquals(fromDefaults, fromBuilder);
    }

    @Test
    void builderAppliesKeyValuePairs() throws ConfigException {
        ConfigBuilder builder = new ConfigBuilder();
        Path source = Path.of("/tmp/test.conf");
        builder.set("theme", "mono", source, 1);
        builder.set("show_hidden", "true", source, 2);
        builder.set("sort", "size", source, 3);
        builder.set("info_level", "2", source, 4);
        builder.set("trash_auto_trim", "yes", source, 5);

        Config config = builder.build();
        assertEquals("mono", config.theme());
        assertTrue(config.showHidden());
        assertEquals(Config.SortOrder.SIZE, config.sort());
        assertEquals(2, config.infoLevel());
        assertTrue(config.trashAutoTrim());
    }

    @Test
    void invalidSortOrderThrows() {
        ConfigBuilder builder = new ConfigBuilder();
        ConfigException exception = assertThrows(ConfigException.class,
                () -> builder.set("sort", "banana", Path.of("/tmp/test.conf"), 1));
        assertEquals("sort", exception.key());
        assertEquals("banana", exception.value());
    }

    @Test
    void invalidInfoLevelThrows() {
        ConfigBuilder builder = new ConfigBuilder();
        assertThrows(ConfigException.class,
                () -> builder.set("info_level", "5", Path.of("/tmp/test.conf"), 1));
    }

    @Test
    void invalidIntegerThrows() {
        ConfigBuilder builder = new ConfigBuilder();
        assertThrows(ConfigException.class,
                () -> builder.set("preview_max_bytes", "not-a-number", Path.of("/tmp/test.conf"), 1));
    }

    @Test
    void sessionRoundTrip(@TempDir Path tempDirectory) throws Exception {
        Path sessionFile = tempDirectory.resolve("session.conf");
        Session original = new Session(Config.SortOrder.SIZE, true, true, false, 2);
        original.save(sessionFile);

        Session loaded = Session.load(sessionFile);
        assertEquals(original, loaded);
    }

    @Test
    void sessionMissingFileReturnsDefaults(@TempDir Path tempDirectory) {
        Path sessionFile = tempDirectory.resolve("does-not-exist.conf");
        Session loaded = Session.load(sessionFile);
        assertEquals(Session.defaults(), loaded);
    }

    @Test
    void sessionCorruptFileReturnsDefaults(@TempDir Path tempDirectory) throws Exception {
        Path sessionFile = tempDirectory.resolve("session.conf");
        Files.writeString(sessionFile, "this is not a valid session file\n");
        Session loaded = Session.load(sessionFile);
        assertEquals(Session.defaults(), loaded);
    }
}