package dev.hollowatlas.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

import java.util.List;

/** Shared look for Hollow Atlas screens: dark walnut panels with brass trim. */
public final class Ui {
    public static final int PANEL = 0xE6191410, PANEL_LIGHT = 0xE6282018, TRIM = 0xFFB89254, TRIM_DARK = 0xFF6E5530,
            TITLE = 0xFFF0D59A, TEXT = 0xFFE9E1D0, MUTED = 0xFF9E9483, ACCENT = 0xFF8FD3C6, GOOD = 0xFF9BD27A, BAD = 0xFFE07A62,
            FACT = 0xFF9BD27A, TESTIMONY = 0xFF8FB8E8, RUMOR = 0xFFD8B26A, STAMINA = 0xFF7BC85A, FOCUS = 0xFFB98CF0, SELECT = 0x40F0D59A;

    private Ui() {}

    public static void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, PANEL);
        c.drawBorder(x, y, w, h, TRIM);
        c.drawBorder(x + 2, y + 2, w - 4, h - 4, TRIM_DARK);
    }

    public static void inset(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, PANEL_LIGHT);
        c.drawBorder(x, y, w, h, TRIM_DARK);
    }

    public static void bar(DrawContext c, int x, int y, int w, int h, float frac, int color, boolean flash) {
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        c.fill(x, y, x + w, y + h, 0xFF2A241C);
        int fw = Math.round(w * Math.max(0, Math.min(1, frac)));
        c.fill(x, y, x + fw, y + h, flash ? 0xFFE05A40 : color);
        c.fill(x, y, x + fw, y + 1, 0x40FFFFFF);
    }

    /** Draws wrapped text and returns the height used. */
    public static int wrapped(DrawContext c, TextRenderer tr, String text, int x, int y, int width, int color) {
        List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(text), width);
        for (int i = 0; i < lines.size(); i++) c.drawText(tr, lines.get(i), x, y + i * 10, color, false);
        return lines.size() * 10;
    }

    public static int wrappedHeight(TextRenderer tr, String text, int width) {
        return tr.wrapLines(StringVisitable.plain(text), width).size() * 10;
    }

    public static void title(DrawContext c, TextRenderer tr, String text, int x, int y) {
        c.drawText(tr, Text.literal(text), x, y, TITLE, true);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
