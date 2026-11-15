package br.redhot.dragonet.tui;

import com.googlecode.lanterna.TextColor;

/**
 * Dragonet color palette.
 *
 * <p>Single source of truth for every color used by the TUI. The
 * primary is a bloody orange ({@code #e64a19}, Material Deep Orange
 * 700) chosen because its luminance is roughly balanced: it has
 * acceptable contrast as foreground on both light and dark terminal
 * backgrounds, and it works as a background for a highlighted cursor.
 *
 * <p>Neutrals ({@link #GRAY}, {@link #GRAY_DIM}) are used for tree
 * guides, sizes and other secondary information. {@link #DEFAULT} is
 * the terminal's own foreground, so body text adapts to whatever the
 * user's terminal theme is.
 *
 * <p>Colors are expressed as {@link TextColor.RGB}. On terminals
 * without truecolor, Lanterna approximates them with the nearest
 * available palette entry.
 */
public final class Colors {

    private Colors() {
        // constants-only holder
    }

    /** Bloody orange — primary accent, directories, separators, cursor background. */
    public static final TextColor ORANGE = new TextColor.RGB(230, 74, 25);

    /** Brighter orange — emphasis on executables and other "hot" items. */
    public static final TextColor ORANGE_BRIGHT = new TextColor.RGB(255, 112, 67);

    /** Neutral gray — tree guides, sizes, secondary metadata. */
    public static final TextColor GRAY = new TextColor.RGB(128, 128, 128);

    /** Darker gray — subtle separators when the orange would be too loud. */
    public static final TextColor GRAY_DIM = new TextColor.RGB(96, 96, 96);

    /** Red — errors, broken symlinks. */
    public static final TextColor RED = new TextColor.RGB(211, 47, 47);

    /** Bright white — text on the orange cursor row. */
    public static final TextColor WHITE = TextColor.ANSI.WHITE_BRIGHT;

    /** Terminal's default foreground — adapts to light/dark themes. */
    public static final TextColor DEFAULT = TextColor.ANSI.DEFAULT;
}