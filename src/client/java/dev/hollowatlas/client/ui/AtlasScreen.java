package dev.hollowatlas.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.client.ClientState;
import dev.hollowatlas.world.LocationIndex;
import dev.hollowatlas.world.RegionModel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Local Atlas v0.1: a surveyed overview of the starting region (rendered from the real world),
 * with only the places the player has discovered, their position and their tracked lead.
 * Zoom with the scroll wheel, pan by dragging. The continental atlas is future work.
 */
public class AtlasScreen extends Screen {
    private static final Identifier MAP = HollowAtlas.id("textures/gui/atlas_map.png");
    private static final int TEX = 751;
    private double zoom = 1.0, panX = 0, panZ = 0;
    private boolean centered = false;

    public AtlasScreen() { super(Text.translatable("screen.hollowatlas.atlas")); }

    @Override public boolean shouldPause() { return false; }

    private int size() { return Math.min(width - 20, height - 30); }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        RegionModel m = LocationIndex.model();
        int ox = m.layout.origin[0], oz = m.layout.origin[1], half = m.half;
        if (!centered && client.player != null) {
            panX = client.player.getX() - ox;
            panZ = client.player.getZ() - oz;
            centered = true;
        }
        int s = size();
        int x0 = width / 2 - s / 2, y0 = height / 2 - s / 2 + 6;
        Ui.panel(c, x0 - 6, y0 - 18, s + 12, s + 24);
        Ui.title(c, textRenderer, "The Atlas - Westreach (local survey)", x0, y0 - 13);
        double blocksAcross = (2 * half + 1) / zoom;
        double scale = s / blocksAcross;        // pixels per block
        double left = panX - blocksAcross / 2, top = panZ - blocksAcross / 2;
        c.enableScissor(x0, y0, x0 + s, y0 + s);
        c.fill(x0, y0, x0 + s, y0 + s, 0xFF1E2A1E);
        // map texture: local coord -> texture pixel = local + half
        int drawW = (int) Math.round(TEX * scale);
        int dx = (int) Math.round(x0 + (-half - left) * scale), dy = (int) Math.round(y0 + (-half - top) * scale);
        c.getMatrices().push();
        c.getMatrices().translate(dx, dy, 0);
        c.getMatrices().scale((float) scale, (float) scale, 1f);
        c.drawTexture(MAP, 0, 0, 0f, 0f, TEX, TEX, TEX, TEX);
        c.getMatrices().pop();
        // discovered sites
        JsonObject j = ClientState.journal;
        String hover = null;
        if (j.has("sites")) {
            for (JsonElement e : j.getAsJsonArray("sites")) {
                JsonObject site = e.getAsJsonObject();
                double lx = site.get("x").getAsDouble() - ox, lz = site.get("z").getAsDouble() - oz;
                int sx = (int) (x0 + (lx - left) * scale), sy = (int) (y0 + (lz - top) * scale);
                c.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFF2A1E10);
                c.fill(sx - 2, sy - 2, sx + 2, sy + 2, 0xFFF0D59A);
                if (zoom >= 1.6) c.drawText(textRenderer, Text.literal(site.get("name").getAsString()), sx + 5, sy - 4, 0xFFFFF3D6, true);
                if (Math.abs(mx - sx) < 5 && Math.abs(my - sy) < 5) hover = site.get("name").getAsString();
            }
        }
        // tracked objective marker
        if (j.has("quests")) {
            for (JsonElement e : j.getAsJsonArray("quests")) {
                JsonObject q = e.getAsJsonObject();
                if (!ClientState.b(q, "tracked") || !q.has("objectives")) continue;
                for (JsonElement oe : q.getAsJsonArray("objectives")) {
                    JsonObject o = oe.getAsJsonObject();
                    if (!o.has("mx") || o.get("done").getAsInt() >= o.get("count").getAsInt()) continue;
                    int sx = (int) (x0 + (o.get("mx").getAsInt() - ox - left) * scale), sy = (int) (y0 + (o.get("mz").getAsInt() - oz - top) * scale);
                    c.drawCenteredTextWithShadow(textRenderer, Text.literal("◆"), sx, sy - 4, 0xFFFF8A50);
                }
            }
        }
        // player
        if (client.player != null) {
            double lx = client.player.getX() - ox, lz = client.player.getZ() - oz;
            int sx = (int) (x0 + (lx - left) * scale), sy = (int) (y0 + (lz - top) * scale);
            c.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFF000000);
            c.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFFFFFFFF);
            double yaw = Math.toRadians(client.player.getYaw());
            int fx = (int) (sx - Math.sin(yaw) * 7), fz = (int) (sy + Math.cos(yaw) * 7);
            c.fill(fx - 1, fz - 1, fx + 1, fz + 1, 0xFFFF5050);
        }
        c.disableScissor();
        if (hover != null) c.drawTooltip(textRenderer, Text.literal(hover), mx, my);
        String help = "Scroll: zoom   Drag: pan   " + (j.has("sites") ? j.getAsJsonArray("sites").size() : 0) + " places charted";
        c.drawText(textRenderer, Text.literal(help), x0, y0 + s + 2, Ui.MUTED, false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        zoom = Math.max(1.0, Math.min(6.0, zoom * (v > 0 ? 1.25 : 0.8)));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        RegionModel m = LocationIndex.model();
        double blocksAcross = (2 * m.half + 1) / zoom;
        double scale = size() / blocksAcross;
        panX -= dx / scale;
        panZ -= dy / scale;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (dev.hollowatlas.client.Keys.ATLAS.matchesKey(key, scan)) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
