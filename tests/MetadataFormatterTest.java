package br.redhot.dragonet.tui;

import br.redhot.dragonet.config.Config;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataFormatterTest {

    // ----- availableLevels -----

    @Test
    void tinyTerminalOnlyClean() {
        assertEquals(1, MetadataFormatter.availableLevels(30).length);
        assertEquals(MetadataFormatter.LEVEL_CLEAN,
                MetadataFormatter.availableLevels(30)[0]);
    }

    @Test
    void narrowTerminalOnlySizeAndClean() {
        int[] levels = MetadataFormatter.availableLevels(50);
        assertEquals(2, levels.length);
        assertEquals(MetadataFormatter.LEVEL_SIZE, levels[0]);
        assertEquals(MetadataFormatter.LEVEL_CLEAN, levels[1]);
    }

    @Test
    void mediumAndWideAllFourLevels() {
        assertEquals(4, MetadataFormatter.availableLevels(70).length);
        assertEquals(4, MetadataFormatter.availableLevels(100).length);
    }

    // ----- effectiveLevel -----

    @Test
    void effectiveKeepsAvailable() {
        assertEquals(MetadataFormatter.LEVEL_FULL,
                MetadataFormatter.effectiveLevel(MetadataFormatter.LEVEL_FULL, 100));
    }

    @Test
    void effectiveSnapsFullToSizeAtNarrow() {
        assertEquals(MetadataFormatter.LEVEL_SIZE,
                MetadataFormatter.effectiveLevel(MetadataFormatter.LEVEL_FULL, 50));
    }

    @Test
    void effectiveSnapsEverythingToCleanAtTiny() {
        assertEquals(MetadataFormatter.LEVEL_CLEAN,
                MetadataFormatter.effectiveLevel(MetadataFormatter.LEVEL_SIZE, 30));
    }

    // ----- nextLevel -----

    @Test
    void nextLevelCyclesFullRange() {
        assertEquals(MetadataFormatter.LEVEL_SIZE_DATE,
                MetadataFormatter.nextLevel(MetadataFormatter.LEVEL_SIZE, 100));
        assertEquals(MetadataFormatter.LEVEL_FULL,
                MetadataFormatter.nextLevel(MetadataFormatter.LEVEL_SIZE_DATE, 100));
        assertEquals(MetadataFormatter.LEVEL_CLEAN,
                MetadataFormatter.nextLevel(MetadataFormatter.LEVEL_FULL, 100));
        assertEquals(MetadataFormatter.LEVEL_SIZE,
                MetadataFormatter.nextLevel(MetadataFormatter.LEVEL_CLEAN, 100));
    }

    @Test
    void nextLevelCyclesNarrowRange() {
        assertEquals(MetadataFormatter.LEVEL_CLEAN,
                MetadataFormatter.nextLevel(MetadataFormatter.LEVEL_SIZE, 50));
        assertEquals(MetadataFormatter.LEVEL_SIZE,
                MetadataFormatter.nextLevel(MetadataFormatter.LEVEL_CLEAN, 50));
    }

    // ----- levelLabel -----

    @Test
    void levelLabelsMatchDesign() {
        assertEquals("Size", MetadataFormatter.levelLabel(MetadataFormatter.LEVEL_SIZE));
        assertEquals("Size+Date", MetadataFormatter.levelLabel(MetadataFormatter.LEVEL_SIZE_DATE));
        assertEquals("Full", MetadataFormatter.levelLabel(MetadataFormatter.LEVEL_FULL));
        assertEquals("Clean", MetadataFormatter.levelLabel(MetadataFormatter.LEVEL_CLEAN));
    }

    // ----- planFor -----

    @Test
    void planSizeOnlyAtWide() {
        MetadataFormatter.DisplayPlan plan = MetadataFormatter.planFor(
                MetadataFormatter.LEVEL_SIZE, 100);
        assertTrue(plan.showSize);
        assertFalse(plan.showDate);
        assertFalse(plan.showMode);
        assertFalse(plan.showOwner);
        assertFalse(plan.compactSize);
    }

    @Test
    void planFullIncludesEverythingAtWide() {
        MetadataFormatter.DisplayPlan plan = MetadataFormatter.planFor(
                MetadataFormatter.LEVEL_FULL, 100);
        assertTrue(plan.showSize);
        assertTrue(plan.showDate);
        assertTrue(plan.showMode);
        assertTrue(plan.showOwner);
        assertFalse(plan.compactDate);
    }

    @Test
    void planFullAtMediumDropsOwnerAndCompactsDate() {
        MetadataFormatter.DisplayPlan plan = MetadataFormatter.planFor(
                MetadataFormatter.LEVEL_FULL, 70);
        assertTrue(plan.showSize);
        assertTrue(plan.showDate);
        assertTrue(plan.showMode);
        assertFalse(plan.showOwner);
        assertTrue(plan.compactDate);
    }

    @Test
    void planSizeAtNarrowCompactsSize() {
        MetadataFormatter.DisplayPlan plan = MetadataFormatter.planFor(
                MetadataFormatter.LEVEL_SIZE, 50);
        assertTrue(plan.showSize);
        assertTrue(plan.compactSize);
    }

    @Test
    void planCleanShowsNothing() {
        MetadataFormatter.DisplayPlan plan = MetadataFormatter.planFor(
                MetadataFormatter.LEVEL_CLEAN, 100);
        assertFalse(plan.showSize);
        assertFalse(plan.showDate);
        assertFalse(plan.showMode);
        assertFalse(plan.showOwner);
    }

    // ----- formatSize -----

    @Test
    void formatSizeBytes() {
        assertEquals("100B", MetadataFormatter.formatSize(
                100, Config.Humanize.BINARY, false));
        assertEquals("100B", MetadataFormatter.formatSize(
                100, Config.Humanize.BINARY, true));
    }

    @Test
    void formatSizeBinaryFull() {
        assertEquals("4.0K", MetadataFormatter.formatSize(
                4096, Config.Humanize.BINARY, false));
        assertEquals("2.1M", MetadataFormatter.formatSize(
                2_200_000, Config.Humanize.BINARY, false));
    }

    @Test
    void formatSizeBinaryCompact() {
        assertEquals("4K", MetadataFormatter.formatSize(
                4096, Config.Humanize.BINARY, true));
        assertEquals("2M", MetadataFormatter.formatSize(
                2_200_000, Config.Humanize.BINARY, true));
    }

    @Test
    void formatSizeDecimal() {
        assertEquals("4.1K", MetadataFormatter.formatSize(
                4096, Config.Humanize.DECIMAL, false));
    }

    @Test
    void formatSizeLargeValueDropsDecimal() {
        assertEquals("42K", MetadataFormatter.formatSize(
                42 * 1024, Config.Humanize.BINARY, false));
    }

    // ----- formatDate -----

    @Test
    void formatDateRelativeNow() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("now", MetadataFormatter.formatDate(
                now.minusSeconds(30), now, true, false));
    }

    @Test
    void formatDateRelativeMinutes() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("5m", MetadataFormatter.formatDate(
                now.minusSeconds(300), now, true, false));
    }

    @Test
    void formatDateRelativeHours() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("2h", MetadataFormatter.formatDate(
                now.minusSeconds(7200), now, true, false));
    }

    @Test
    void formatDateRelativeDays() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        assertEquals("3d", MetadataFormatter.formatDate(
                now.minusSeconds(3 * 86_400), now, true, false));
    }

    @Test
    void formatDateAbsoluteWhenRelativeDisabled() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        String result = MetadataFormatter.formatDate(
                now.minusSeconds(60), now, false, false);
        assertFalse(result.contains("m"));
        assertFalse(result.contains("h"));
        assertFalse(result.contains("d"));
    }

    @Test
    void formatDateCompactShorterThanFull() {
        Instant timestamp = Instant.parse("2026-11-11T03:00:00Z");
        Instant now = Instant.parse("2026-12-01T00:00:00Z");
        String full = MetadataFormatter.formatDate(timestamp, now, false, false);
        String compact = MetadataFormatter.formatDate(timestamp, now, false, true);
        assertTrue(full.length() > compact.length());
    }
}