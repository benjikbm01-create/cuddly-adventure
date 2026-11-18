package dev.hollowatlas.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.client.ClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Developer menu (test world only). Buttons send intents to DevTestService, which re-checks that the
 * world is a developer testing world before doing anything. Lists only test quests and test places.
 */
public class DevMenuScreen extends Screen {
    private static final String[] TABS = {"Combat", "Items", "Abilities", "World", "Quests", "Travel", "Saves"};
    private static int tab = 0;
    private static JsonObject info = new JsonObject();
    private int page = 0;

    public DevMenuScreen() { super(Text.translatable("screen.hollowatlas.dev_menu")); }

    public static void update(JsonObject o) { info = o; }

    private record Btn(String label, String action, String arg) {}

    private List<Btn> buttons() {
        List<Btn> b = new ArrayList<>();
        switch (tab) {
            case 0 -> {
                b.add(new Btn("Spawn melee brigand", "spawn_brigand", ""));
                b.add(new Btn("Spawn ranged slinger", "spawn_slinger", ""));
                b.add(new Btn("Spawn one of each", "spawn_pair", ""));
                b.add(new Btn("Remove all enemies", "clear_enemies", ""));
                b.add(new Btn("Refill health/stamina/focus", "refill", ""));
                b.add(new Btn("Difficulty: Story", "difficulty", "story"));
                b.add(new Btn("Difficulty: Normal", "difficulty", "normal"));
                b.add(new Btn("Difficulty: Hard", "difficulty", "hard"));
                b.add(new Btn("Difficulty: Expert", "difficulty", "expert"));
            }
            case 1 -> {
                b.add(new Btn("Give all weapons + shield", "give_weapons", ""));
                b.add(new Btn("Give leather armor set", "give_armor", ""));
                b.add(new Btn("Give food and tonics", "give_food", ""));
                b.add(new Btn("Give crafting materials", "give_materials", ""));
                b.add(new Btn("Give test key item", "give_token", ""));
                b.add(new Btn("+50 coins", "coins", "50"));
                b.add(new Btn("+500 coins", "coins", "500"));
                b.add(new Btn("+1000 coins", "coins", "1000"));
                b.add(new Btn("Fill pack (overflow test)", "fill_pack", ""));
                b.add(new Btn("Clear pack (keeps key items)", "clear_pack", ""));
                b.add(new Btn("Reset shop stock", "reset_shops", ""));
            }
            case 2 -> {
                b.add(new Btn("Unlock Windstep", "unlock_windstep", ""));
                b.add(new Btn("Unlock Echo Sight", "unlock_echo_sight", ""));
                b.add(new Btn("Reset ability cooldowns", "reset_cooldowns", ""));
                b.add(new Btn("Refill stamina/focus", "refill", ""));
            }
            case 3 -> {
                b.add(new Btn("Time: morning", "time_morning", ""));
                b.add(new Btn("Time: noon", "time_noon", ""));
                b.add(new Btn("Time: evening", "time_evening", ""));
                b.add(new Btn("Time: night", "time_night", ""));
                b.add(new Btn("Skip to next morning", "next_day", ""));
                b.add(new Btn("Weather: clear", "weather_clear", ""));
                b.add(new Btn("Weather: rain", "weather_rain", ""));
                b.add(new Btn("World event: toggle footbridge", "toggle_gap", ""));
                b.add(new Btn("Reset gathering spots", "reset_nodes", ""));
            }
            case 4 -> {
                if (info.has("quests")) for (JsonElement e : info.getAsJsonArray("quests")) {
                    JsonObject q = e.getAsJsonObject();
                    b.add(new Btn("Start: " + q.get("title").getAsString(), "start_quest", q.get("id").getAsString()));
                }
                b.add(new Btn("RESET all test quests", "reset_quests", ""));
            }
            case 5 -> {
                if (info.has("anchors")) for (JsonElement e : info.getAsJsonArray("anchors")) {
                    String a = e.getAsString();
                    b.add(new Btn(a.substring(3).replace('_', ' ').replace('.', ' '), "tp", a));
                }
            }
            default -> {
                b.add(new Btn("Set checkpoint here", "checkpoint_here", ""));
                b.add(new Btn("Test death (respawn at checkpoint)", "die", ""));
                b.add(new Btn("Save world now", "save_now", ""));
            }
        }
        return b;
    }

    @Override
    protected void init() {
        ClientState.send("dev", action("info", ""));
        rebuild();
    }

    private static JsonObject action(String a, String arg) {
        JsonObject o = new JsonObject();
        o.addProperty("action", a);
        o.addProperty("arg", arg);
        return o;
    }

    private int px() { return Math.max(4, width / 2 - 220); }
    private int py() { return Math.max(4, height / 2 - 115); }

    public void rebuild() {
        clearChildren();
        int x = px(), y = py();
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            ButtonWidget bt = ButtonWidget.builder(Text.literal(TABS[i]), b -> { tab = t; page = 0; rebuild(); }).dimensions(x + 4 + i * 62, y - 18, 60, 16).build();
            bt.active = tab != i;
            addDrawableChild(bt);
        }
        List<Btn> all = buttons();
        int perPage = 20;
        int start = page * perPage;
        for (int i = start; i < Math.min(all.size(), start + perPage); i++) {
            Btn bn = all.get(i);
            int k = i - start;
            int bx = x + 10 + (k % 2) * 215, by = y + 26 + (k / 2) * 19;
            addDrawableChild(ButtonWidget.builder(Text.literal(bn.label()), b -> ClientState.send("dev", action(bn.action(), bn.arg())))
                    .dimensions(bx, by, 205, 17).build());
        }
        if (all.size() > perPage) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Next page"), b -> { page = (page + 1) % ((all.size() + perPage - 1) / perPage); rebuild(); })
                    .dimensions(x + 340, y + 212, 90, 16).build());
        }
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int x = px(), y = py();
        Ui.panel(c, x, y, 440, 232);
        for (var child : children()) if (child instanceof net.minecraft.client.gui.Drawable d) d.render(c, mx, my, delta);
        Ui.title(c, textRenderer, "Developer Menu - test world only (no story content)", x + 10, y + 9);
        if (tab == 4 && info.has("quests")) {
            int ly = y + 26 + ((buttons().size() + 1) / 2) * 19 + 4;
            for (JsonElement e : info.getAsJsonArray("quests")) {
                JsonObject q = e.getAsJsonObject();
                if (ly > y + 222) break;
                c.drawText(textRenderer, Text.literal(q.get("title").getAsString() + ": " + q.get("status").getAsString()), x + 12, ly, Ui.MUTED, false);
                ly += 10;
            }
        }
        String foot = "Difficulty: " + ClientState.s(info, "difficulty", "?") + "   Active encounter enemies: " + ClientState.i(info, "enemies", 0);
        c.drawText(textRenderer, Text.literal(foot), x + 10, y + 220, Ui.MUTED, false);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (dev.hollowatlas.client.Keys.DEV_MENU != null && dev.hollowatlas.client.Keys.DEV_MENU.matchesKey(key, scan)) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
