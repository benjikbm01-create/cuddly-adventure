package dev.hollowatlas.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.client.ClientConfig;
import dev.hollowatlas.client.ClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversation UI. Works in first and third person (the world keeps rendering behind it).
 * Mouse: click a choice. Keyboard: 1-9 to choose, Up/Down + Enter, Backspace to go back,
 * Space to finish the text, Esc to leave. Choices are only ever sent with the server's sequence id.
 */
public class DialogueScreen extends Screen {
    private JsonObject node;
    private final List<Choice> choices = new ArrayList<>();
    private int focus = 0;
    private long shownAt;
    private boolean sent = false;

    private record Choice(String text, String tag, boolean enabled, String hint) {}

    public DialogueScreen(JsonObject node) {
        super(Text.translatable("screen.hollowatlas.dialogue"));
        show(node);
    }

    public void show(JsonObject n) {
        node = n;
        choices.clear();
        for (JsonElement e : n.getAsJsonArray("choices")) {
            JsonObject c = e.getAsJsonObject();
            choices.add(new Choice(localized(ClientState.s(c, "key", ""), ClientState.s(c, "text", "")),
                    ClientState.s(c, "tag", ""), c.get("enabled").getAsBoolean(), ClientState.s(c, "hint", null)));
        }
        focus = 0;
        while (focus < choices.size() - 1 && !choices.get(focus).enabled) focus++;
        shownAt = System.currentTimeMillis();
        skipped = false;
        sent = false;
    }

    private static String localized(String key, String fallback) {
        return key != null && !key.isEmpty() && I18n.hasTranslation(key) ? I18n.translate(key) : fallback;
    }

    private String fullText() { return localized(ClientState.s(node, "key", ""), ClientState.s(node, "text", "")); }

    private boolean skipped = false;

    private int visibleChars() {
        int speed = ClientConfig.get().dialogueSpeed;
        if (speed == 0 || skipped) return Integer.MAX_VALUE;
        long cps = speed == 1 ? 90 : speed == 2 ? 45 : 25;
        long elapsed = Math.max(0L, System.currentTimeMillis() - shownAt);
        return (int) Math.min(Integer.MAX_VALUE, elapsed * cps / 1000L);   // long math: never overflows to negative
    }

    private boolean textDone() { return visibleChars() >= fullText().length(); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, height / 2, width, height, 0x00000000, 0xA0000000);
    }

    private int panelX() { return Math.max(10, width / 2 - 220); }
    private int panelW() { return Math.min(440, width - 20); }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        float scale = ClientConfig.get().dialogueTextScale;
        int px = panelX(), pw = panelW();
        int textW = (int) ((pw - 100) / scale);
        String full = fullText();
        String shown = full.substring(0, Math.max(0, Math.min(full.length(), visibleChars())));
        int textH = (int) (Ui.wrappedHeight(textRenderer, full, textW) * scale);
        int choiceH = choices.size() * 16 + 6;
        int ph = Math.max(90, textH + choiceH + 38);
        int py = height - ph - 10;
        Ui.panel(c, px, py, pw, ph);
        // portrait
        Entity e = MinecraftClient.getInstance().world == null ? null : MinecraftClient.getInstance().world.getEntityById(ClientState.i(node, "entity", -1));
        Ui.inset(c, px + 8, py + 8, 80, 80);
        if (e instanceof LivingEntity le) {
            InventoryScreen.drawEntity(c, px + 9, py + 9, px + 87, py + 87, 36, 0.0625f, mouseX, mouseY, le);
        }
        String speaker = ClientState.s(node, "speaker", "");
        String title = ClientState.s(node, "title", "");
        int tx = px + 96;
        if (!speaker.isEmpty()) {
            Ui.title(c, textRenderer, speaker, tx, py + 9);
            if (!title.isEmpty()) c.drawText(textRenderer, Text.literal(title), tx + textRenderer.getWidth(speaker) + 6, py + 9, Ui.MUTED, false);
        }
        c.getMatrices().push();
        c.getMatrices().translate(tx, py + 23, 0);
        c.getMatrices().scale(scale, scale, 1);
        Ui.wrapped(c, textRenderer, shown, 0, 0, textW, Ui.TEXT);
        c.getMatrices().pop();
        if (!textDone()) return;
        int cy = py + 28 + textH;
        for (int i = 0; i < choices.size(); i++) {
            Choice ch = choices.get(i);
            int y = cy + i * 16;
            boolean hover = Ui.inside(mouseX, mouseY, tx - 2, y - 2, pw - 104, 14);
            if (hover && ch.enabled) focus = i;
            if (i == focus) c.fill(tx - 2, y - 2, px + pw - 8, y + 12, Ui.SELECT);
            String prefix = (i + 1) + ". " + tagLabel(ch.tag);
            int color = !ch.enabled ? 0xFF6C6458 : i == focus ? Ui.TITLE : Ui.TEXT;
            c.drawText(textRenderer, Text.literal(prefix + ch.text), tx, y, color, false);
            if (!ch.enabled && ch.hint != null && hover) c.drawTooltip(textRenderer, Text.literal(ch.hint), mouseX, mouseY);
        }
        String help = (ClientState.b(node, "can_back") ? "[Backspace] Back   " : "") + "[Esc] Leave";
        c.drawText(textRenderer, Text.literal(help), px + pw - 8 - textRenderer.getWidth(help), py + ph - 12, Ui.MUTED, false);
    }

    private static String tagLabel(String tag) {
        return switch (tag) {
            case "investigate" -> "[Investigate] ";
            case "persuade" -> "[Persuade] ";
            case "intimidate" -> "[Intimidate] ";
            case "evidence" -> "[Evidence] ";
            case "quest" -> "✦ ";
            case "trade" -> "[Trade] ";
            default -> "";
        };
    }

    /** Test/accessibility helper: index of the first offered choice containing the text, or -1. */
    public int indexOf(String part) {
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).text.contains(part)) return i;
        return -1;
    }

    public List<String> choiceTexts() {
        List<String> out = new ArrayList<>();
        for (Choice c : choices) out.add(c.text);
        return out;
    }

    public void choose(int index) {
        if (sent || index < 0 || index >= choices.size() || !choices.get(index).enabled) return;
        if (!textDone()) { skipped = true; return; }
        sent = true;   // guard against double activation until the server answers
        JsonObject o = new JsonObject();
        o.addProperty("seq", ClientState.i(node, "seq", -1));
        o.addProperty("index", index);
        ClientState.send("dlg_choose", o);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!textDone()) { skipped = true; return true; }
        float scale = ClientConfig.get().dialogueTextScale;
        int px = panelX(), pw = panelW();
        int textW = (int) ((pw - 100) / scale);
        int textH = (int) (Ui.wrappedHeight(textRenderer, fullText(), textW) * scale);
        int ph = Math.max(90, textH + choices.size() * 16 + 6 + 38);
        int py = height - ph - 10;
        int cy = py + 28 + textH;
        for (int i = 0; i < choices.size(); i++) {
            if (Ui.inside(mx, my, px + 94, cy + i * 16 - 2, pw - 104, 14)) { choose(i); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (key == GLFW.GLFW_KEY_SPACE && !textDone()) { skipped = true; return true; }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) { choose(key - GLFW.GLFW_KEY_1); return true; }
        if (key == GLFW.GLFW_KEY_DOWN) { do { focus = (focus + 1) % choices.size(); } while (!choices.get(focus).enabled && choices.size() > 1); return true; }
        if (key == GLFW.GLFW_KEY_UP) { do { focus = (focus - 1 + choices.size()) % choices.size(); } while (!choices.get(focus).enabled && choices.size() > 1); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_E) { choose(focus); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE && ClientState.b(node, "can_back") && !sent) {
            sent = true;
            JsonObject o = new JsonObject();
            o.addProperty("seq", ClientState.i(node, "seq", -1));
            ClientState.send("dlg_back", o);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void close() {
        JsonObject o = new JsonObject();
        o.addProperty("seq", ClientState.i(node, "seq", -1));
        ClientState.send("dlg_close", o);
        super.close();
    }
}
