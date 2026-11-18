package dev.hollowatlas.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.client.ClientConfig;
import dev.hollowatlas.client.ClientState;
import dev.hollowatlas.client.Keys;
import dev.hollowatlas.inventory.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Information HUD. Every number shown comes from the latest server "hud"/"journal" message; if the
 * server goes quiet the bars grey out instead of showing stale values as live.
 */
public final class HudRenderer {
    private HudRenderer() {}

    public static void init() {
        HudRenderCallback.EVENT.register(HudRenderer::render);
    }

    private static void render(DrawContext c, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || !ClientConfig.get().showHud) return;
        if (mc.player.isSpectator()) return;
        TextRenderer tr = mc.textRenderer;
        JsonObject hud = ClientState.hud;
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();
        boolean stale = System.currentTimeMillis() - ClientState.lastHudAt > 3000;

        // stamina (left of hotbar) and focus (right of hotbar), above vanilla hearts/hunger
        float st = ClientState.f(hud, "stamina", 0), stMax = ClientState.f(hud, "stamina_max", 100);
        float fo = ClientState.f(hud, "focus", 0), foMax = ClientState.f(hud, "focus_max", 100);
        boolean exhausted = ClientState.b(hud, "exhausted");
        int barY = h - 50;
        int left = w / 2 - 91, right = w / 2 + 10;
        if (!mc.player.isCreative()) barY = h - 58;
        Ui.bar(c, left, barY, 81, 4, stMax <= 0 ? 0 : st / stMax, stale ? 0xFF666666 : Ui.STAMINA, exhausted && (mc.world.getTime() / 4) % 2 == 0);
        if (hasFocusAbility(hud)) Ui.bar(c, right, barY, 81, 4, foMax <= 0 ? 0 : fo / foMax, stale ? 0xFF666666 : Ui.FOCUS, false);
        if (ClientState.b(hud, "guarding")) c.drawCenteredTextWithShadow(tr, Text.translatable("hud.hollowatlas.guarding"), w / 2, barY - 10, Ui.ACCENT);

        // coins
        String coins = Integer.toString(ClientState.i(hud, "coins", 0));
        c.drawText(tr, Text.literal("● " + coins), w / 2 + 96, h - 10, 0xFFE8C060, true);

        // ability slots (Z / X)
        JsonArray abilities = hud.has("abilities") ? hud.getAsJsonArray("abilities") : new JsonArray();
        int ax = w / 2 - 91 - 52;
        for (int slot = 0; slot < 2; slot++) {
            JsonObject ab = null;
            for (JsonElement e : abilities) if (e.getAsJsonObject().get("slot").getAsInt() == slot) ab = e.getAsJsonObject();
            int x = ax + slot * 24, y = h - 23;
            c.fill(x, y, x + 22, y + 22, 0xB0000000);
            c.drawBorder(x, y, 22, 22, ab == null ? 0xFF444444 : Ui.TRIM);
            if (ab != null) {
                String token = ClientState.s(ab, "token", "");
                ItemStack icon = token.isEmpty() ? ItemStack.EMPTY : new ItemStack(net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(token)));
                if (!icon.isEmpty()) c.drawItem(icon, x + 3, y + 3);
                float left2 = ClientState.f(ab, "cd_left", 0), total = Math.max(1, ClientState.f(ab, "cd_total", 1));
                if (left2 > 0) {
                    int hh = Math.round(20 * left2 / total);
                    c.fill(x + 1, y + 21 - hh, x + 21, y + 21, 0xA0000000);
                    c.drawCenteredTextWithShadow(tr, Integer.toString((int) Math.ceil(left2 / 20f)), x + 11, y + 7, 0xFFFFFFFF);
                }
            }
            Text keyName = (slot == 0 ? Keys.ABILITY_1 : Keys.ABILITY_2).getBoundKeyLocalizedText();
            c.drawText(tr, keyName, x + 1, y - 9, Ui.MUTED, true);
        }

        // cooldown overlay on hotbar ability tokens
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!(s.getItem() instanceof ModItems.AbilityItem ai)) continue;
            for (JsonElement e : abilities) {
                JsonObject ab = e.getAsJsonObject();
                if (!ab.get("id").getAsString().equals(ai.ability)) continue;
                float l = ClientState.f(ab, "cd_left", 0), t = Math.max(1, ClientState.f(ab, "cd_total", 1));
                if (l <= 0) continue;
                int sx = w / 2 - 91 + i * 20 + 3, sy = h - 19;
                int hh = Math.round(16 * l / t);
                c.fill(sx, sy + 16 - hh, sx + 16, sy + 16, 0x90000000);
            }
        }

        // tracked objective (top-left)
        if (ClientConfig.get().showTracker) tracker(c, tr, mc);

        // contextual hint
        if (ClientState.hintKey != null && System.currentTimeMillis() < ClientState.hintUntil) {
            Text t = Text.translatable(ClientState.hintKey);
            int tw = tr.getWidth(t) + 12;
            c.fill(w / 2 - tw / 2, 28, w / 2 + tw / 2, 44, 0xC0101010);
            c.drawBorder(w / 2 - tw / 2, 28, tw, 16, Ui.TRIM_DARK);
            c.drawCenteredTextWithShadow(tr, t, w / 2, 32, Ui.TEXT);
        }
    }

    private static boolean hasFocusAbility(JsonObject hud) {
        if (!hud.has("abilities")) return false;
        for (JsonElement e : hud.getAsJsonArray("abilities")) if ("focus".equals(ClientState.s(e.getAsJsonObject(), "pool", ""))) return true;
        return false;
    }

    private static void tracker(DrawContext c, TextRenderer tr, MinecraftClient mc) {
        JsonObject j = ClientState.journal;
        String tracked = ClientState.s(j, "tracked", "");
        if (tracked.isEmpty() || !j.has("quests")) return;
        for (JsonElement e : j.getAsJsonArray("quests")) {
            JsonObject q = e.getAsJsonObject();
            if (!q.get("id").getAsString().equals(tracked) || !"ACTIVE".equals(ClientState.s(q, "status", ""))) continue;
            int x = 6, y = 6;
            String title = ClientState.s(q, "title", "");
            c.fill(x - 3, y - 3, x + 170, y + 11, 0x80000000);
            c.drawText(tr, Text.literal(title), x, y, Ui.TITLE, true);
            y += 13;
            if (!q.has("objectives")) return;
            for (JsonElement oe : q.getAsJsonArray("objectives")) {
                JsonObject o = oe.getAsJsonObject();
                int done = o.get("done").getAsInt(), count = o.get("count").getAsInt();
                boolean complete = done >= count;
                String line = (complete ? "✔ " : "○ ") + ClientState.s(o, "text", "")
                        + (count > 1 ? " (" + Math.min(done, count) + "/" + count + ")" : "")
                        + (ClientState.b(o, "optional") ? " *" : "");
                int lh = Ui.wrappedHeight(tr, line, 164);
                c.fill(x - 3, y - 2, x + 170, y + lh, 0x60000000);
                Ui.wrapped(c, tr, line, x, y, 164, complete ? Ui.MUTED : Ui.TEXT);
                y += lh + 2;
                if (!complete && ClientConfig.get().showMarkers && o.has("mx")) marker(c, tr, mc, o);
            }
            return;
        }
    }

    /** Optional guidance: a small compass pip at the top of the screen with distance. */
    private static void marker(DrawContext c, TextRenderer tr, MinecraftClient mc, JsonObject o) {
        Vec3d target = new Vec3d(o.get("mx").getAsInt() + 0.5, o.get("my").getAsInt(), o.get("mz").getAsInt() + 0.5);
        Vec3d d = target.subtract(mc.player.getPos());
        double dist = Math.sqrt(d.x * d.x + d.z * d.z);
        if (dist < 3) return;
        float targetYaw = (float) (MathHelper.atan2(d.z, d.x) * 57.29577951308232) - 90f;
        float rel = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        int w = c.getScaledWindowWidth();
        int x = w / 2 + (int) MathHelper.clamp(rel * 2.2f, -w / 2f + 30, w / 2f - 30);
        c.drawCenteredTextWithShadow(tr, Text.literal("◆"), x, 8, Math.abs(rel) < 12 ? 0xFFF0D59A : 0xFFB09060);
        c.drawCenteredTextWithShadow(tr, Text.literal((int) dist + "m"), x, 18, Ui.MUTED);
    }
}
