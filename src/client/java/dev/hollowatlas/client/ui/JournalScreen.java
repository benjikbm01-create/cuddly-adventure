package dev.hollowatlas.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.client.ClientConfig;
import dev.hollowatlas.client.ClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Journal with three tabs: Quests (active/completed, objectives, optional hints, history),
 * Case Board (evidence by kind; theories clearly marked as guesses) and Settings.
 * Only quests the player has actually discovered are ever sent by the server.
 */
public class JournalScreen extends Screen {
    private int tab;
    private int selected = 0;
    private int scroll = 0;
    private final List<JsonObject> quests = new ArrayList<>();
    private final List<JsonObject> cases = new ArrayList<>();

    public JournalScreen(int tab) {
        super(Text.translatable("screen.hollowatlas.journal"));
        this.tab = tab;
    }

    public void refresh() {
        quests.clear();
        cases.clear();
        JsonObject j = ClientState.journal;
        if (j.has("quests")) {
            for (JsonElement e : j.getAsJsonArray("quests")) if ("ACTIVE".equals(ClientState.s(e.getAsJsonObject(), "status", ""))) quests.add(e.getAsJsonObject());
            for (JsonElement e : j.getAsJsonArray("quests")) if (!"ACTIVE".equals(ClientState.s(e.getAsJsonObject(), "status", ""))) quests.add(e.getAsJsonObject());
        }
        if (j.has("cases")) for (JsonElement e : j.getAsJsonArray("cases")) cases.add(e.getAsJsonObject());
        int n = tab == 0 ? quests.size() : cases.size();
        if (selected >= n) selected = Math.max(0, n - 1);
        if (client != null) rebuild();
    }

    private int px() { return Math.max(4, width / 2 - 230); }
    private int py() { return Math.max(4, height / 2 - 120); }
    private int pw() { return Math.min(460, width - 8); }
    private int ph() { return Math.min(240, height - 8); }

    @Override
    protected void init() { refresh(); }

    private void rebuild() {
        clearChildren();
        int x = px(), y = py();
        String[] names = {"Quests", "Case Board", "Settings"};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            ButtonWidget b = ButtonWidget.builder(Text.literal(names[i]), btn -> { tab = t; selected = 0; scroll = 0; refresh(); })
                    .dimensions(x + 6 + i * 84, y - 18, 80, 16).build();
            b.active = tab != i;
            addDrawableChild(b);
        }
        if (tab == 0 && !quests.isEmpty()) {
            JsonObject q = quests.get(selected);
            if ("ACTIVE".equals(ClientState.s(q, "status", ""))) {
                boolean tracked = ClientState.b(q, "tracked");
                addDrawableChild(ButtonWidget.builder(Text.literal(tracked ? "Untrack" : "Track"), b -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("quest", tracked ? "" : q.get("id").getAsString());
                    ClientState.send("track", o);
                }).dimensions(x + pw() - 90, y + ph() - 22, 80, 16).build());
                if (ClientState.b(q, "abandonable")) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("Abandon"), b -> {
                        JsonObject o = new JsonObject();
                        o.addProperty("quest", q.get("id").getAsString());
                        ClientState.send("abandon", o);
                    }).dimensions(x + pw() - 176, y + ph() - 22, 80, 16).build());
                }
            }
        }
        if (tab == 1 && !cases.isEmpty()) {
            JsonObject cs = cases.get(selected);
            int hy = y + ph() - 22 - 18 * (cs.getAsJsonArray("hypotheses").size() - 1);
            for (JsonElement he : cs.getAsJsonArray("hypotheses")) {
                JsonObject h = he.getAsJsonObject();
                boolean avail = ClientState.b(h, "available");
                boolean chosen = h.get("id").getAsString().equals(ClientState.s(cs, "guess", ""));
                String label = (chosen ? "✓ " : "") + ClientState.s(h, "text", "") + (avail ? "" : "  (needs " + ClientState.i(h, "missing", 1) + " more)");
                ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("case", cs.get("id").getAsString());
                    o.addProperty("id", h.get("id").getAsString());
                    ClientState.send("hypothesis", o);
                }).dimensions(x + 150, hy, pw() - 160, 16).build();
                b.active = avail && !chosen;
                addDrawableChild(b);
                hy += 18;
            }
        }
        if (tab == 2) settingsWidgets(x, y);
    }

    private void settingsWidgets(int x, int y) {
        ClientConfig cfg = ClientConfig.get();
        int bx = x + 12, by = y + 24, bw = 200;
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD: " + (cfg.showHud ? "On" : "Off")), b -> { cfg.showHud = !cfg.showHud; ClientConfig.save(); rebuild(); }).dimensions(bx, by, bw, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Objective tracker: " + (cfg.showTracker ? "On" : "Off")), b -> { cfg.showTracker = !cfg.showTracker; ClientConfig.save(); rebuild(); }).dimensions(bx, by + 18, bw, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Guidance markers: " + (cfg.showMarkers ? "On" : "Off")), b -> { cfg.showMarkers = !cfg.showMarkers; ClientConfig.save(); rebuild(); }).dimensions(bx, by + 36, bw, 16).build());
        String[] speeds = {"Instant", "Fast", "Normal", "Slow"};
        addDrawableChild(ButtonWidget.builder(Text.literal("Dialogue speed: " + speeds[cfg.dialogueSpeed]), b -> { cfg.dialogueSpeed = (cfg.dialogueSpeed + 1) % 4; ClientConfig.save(); rebuild(); }).dimensions(bx, by + 54, bw, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(String.format("Dialogue text size: %.0f%%", cfg.dialogueTextScale * 100)), b -> {
            cfg.dialogueTextScale = cfg.dialogueTextScale >= 1.45f ? 0.75f : cfg.dialogueTextScale + 0.25f; ClientConfig.save(); rebuild();
        }).dimensions(bx, by + 72, bw, 16).build());
        boolean guidance = ClientState.b(ClientState.journal, "guidance");
        addDrawableChild(ButtonWidget.builder(Text.literal("Quest hints in journal: " + (guidance ? "On" : "Off")), b -> {
            JsonObject o = new JsonObject();
            o.addProperty("key", "guidance");
            o.addProperty("value", guidance ? "off" : "on");
            ClientState.send("setting", o);
        }).dimensions(bx, by + 90, bw, 16).build());
        String diff = ClientState.s(ClientState.hud, "difficulty", "normal");
        String[] levels = {"story", "normal", "hard", "expert"};
        int idx = java.util.Arrays.asList(levels).indexOf(diff);
        addDrawableChild(ButtonWidget.builder(Text.literal("Difficulty: " + cap(diff)), b -> {
            JsonObject o = new JsonObject();
            o.addProperty("key", "difficulty");
            o.addProperty("value", levels[(idx + 1) % levels.length]);
            ClientState.send("setting", o);
        }).dimensions(bx, by + 108, bw, 16).build());
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int x = px(), y = py(), w = pw(), h = ph();
        Ui.panel(c, x, y, w, h);
        // re-render widgets above the panel
        for (var child : children()) if (child instanceof net.minecraft.client.gui.Drawable d) d.render(c, mx, my, delta);
        switch (tab) {
            case 0 -> renderQuests(c, x, y, w, h, mx, my);
            case 1 -> renderCases(c, x, y, w, h, mx, my);
            default -> renderSettings(c, x, y);
        }
    }

    private void renderQuests(DrawContext c, int x, int y, int w, int h, int mx, int my) {
        Ui.inset(c, x + 6, y + 6, 136, h - 12);
        if (quests.isEmpty()) {
            Ui.wrapped(c, textRenderer, "No entries yet. Talk to people; the journal fills itself.", x + 150, y + 12, w - 160, Ui.MUTED);
            return;
        }
        int ly = y + 10;
        boolean shownCompletedHeader = false;
        c.drawText(textRenderer, Text.literal("Active"), x + 10, ly, Ui.MUTED, false);
        ly += 11;
        for (int i = 0; i < quests.size(); i++) {
            JsonObject q = quests.get(i);
            boolean active = "ACTIVE".equals(ClientState.s(q, "status", ""));
            if (!active && !shownCompletedHeader) {
                shownCompletedHeader = true;
                ly += 4;
                c.drawText(textRenderer, Text.literal("Completed"), x + 10, ly, Ui.MUTED, false);
                ly += 11;
            }
            if (i == selected) c.fill(x + 7, ly - 2, x + 141, ly + 9, Ui.SELECT);
            String t = (ClientState.b(q, "tracked") ? "◆ " : "") + ClientState.s(q, "title", "");
            c.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(t, 128)), x + 10, ly, active ? ("main".equals(ClientState.s(q, "category", "")) ? Ui.TITLE : Ui.TEXT) : Ui.MUTED, false);
            ly += 12;
        }
        JsonObject q = quests.get(selected);
        int dx = x + 150, dw = w - 160, dy = y + 10;
        Ui.title(c, textRenderer, ClientState.s(q, "title", ""), dx, dy);
        dy += 12;
        String cat = ClientState.s(q, "category", "side");
        c.drawText(textRenderer, Text.literal(cat.equals("main") ? "Main story" : cat.equals("sandbox") || cat.equals("test") ? "Developer test (non-canonical)" : "Side errand"), dx, dy, Ui.MUTED, false);
        dy += 14;
        String summary = ClientState.s(q, "summary", "");
        if (!summary.isEmpty()) dy += Ui.wrapped(c, textRenderer, summary, dx, dy, dw, Ui.MUTED) + 4;
        String stage = ClientState.s(q, "stage_text", "");
        if (!stage.isEmpty()) dy += Ui.wrapped(c, textRenderer, stage, dx, dy, dw, Ui.TEXT) + 6;
        if (q.has("objectives")) {
            for (JsonElement oe : q.getAsJsonArray("objectives")) {
                JsonObject o = oe.getAsJsonObject();
                int done = o.get("done").getAsInt(), count = o.get("count").getAsInt();
                String line = (done >= count ? "✔ " : "○ ") + ClientState.s(o, "text", "")
                        + (count > 1 ? " (" + Math.min(done, count) + "/" + count + ")" : "") + (ClientState.b(o, "optional") ? " (optional)" : "");
                dy += Ui.wrapped(c, textRenderer, line, dx + 4, dy, dw - 4, done >= count ? Ui.MUTED : Ui.GOOD) + 2;
            }
        }
        String hint = ClientState.s(q, "hint", "");
        if (!hint.isEmpty()) { dy += 4; dy += Ui.wrapped(c, textRenderer, "Hint: " + hint, dx, dy, dw, Ui.ACCENT) + 4; }
        JsonArray log = q.has("log") ? q.getAsJsonArray("log") : new JsonArray();
        if (!log.isEmpty() && dy < y + h - 40) {
            dy += 4;
            c.drawText(textRenderer, Text.literal("Earlier"), dx, dy, Ui.MUTED, false);
            dy += 11;
            for (int i = log.size() - 1; i >= 0 && dy < y + h - 34; i--) dy += Ui.wrapped(c, textRenderer, "- " + log.get(i).getAsString(), dx, dy, dw, 0xFF8A8172) + 1;
        }
    }

    private void renderCases(DrawContext c, int x, int y, int w, int h, int mx, int my) {
        Ui.inset(c, x + 6, y + 6, 136, h - 12);
        if (cases.isEmpty()) {
            Ui.wrapped(c, textRenderer, "No open cases. Things you notice in the world will be pinned here.", x + 150, y + 12, w - 160, Ui.MUTED);
            return;
        }
        for (int i = 0; i < cases.size(); i++) {
            int ly = y + 10 + i * 12;
            if (i == selected) c.fill(x + 7, ly - 2, x + 141, ly + 9, Ui.SELECT);
            c.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(ClientState.s(cases.get(i), "title", ""), 128)), x + 10, ly, Ui.TEXT, false);
        }
        JsonObject cs = cases.get(selected);
        int dx = x + 150, dw = w - 160, dy = y + 10;
        Ui.title(c, textRenderer, ClientState.s(cs, "title", ""), dx, dy);
        dy += 13;
        String sum = ClientState.s(cs, "summary", "");
        if (!sum.isEmpty()) dy += Ui.wrapped(c, textRenderer, sum, dx, dy, dw, Ui.MUTED) + 4;
        int hypCount = cs.getAsJsonArray("hypotheses").size();
        int limit = y + h - 30 - 18 * hypCount;
        for (String kind : new String[]{"fact", "testimony", "rumor"}) {
            List<JsonObject> evs = new ArrayList<>();
            for (JsonElement e : cs.getAsJsonArray("evidence")) if (kind.equals(ClientState.s(e.getAsJsonObject(), "kind", ""))) evs.add(e.getAsJsonObject());
            if (evs.isEmpty()) continue;
            int color = kind.equals("fact") ? Ui.FACT : kind.equals("testimony") ? Ui.TESTIMONY : Ui.RUMOR;
            String head = kind.equals("fact") ? "Observed facts" : kind.equals("testimony") ? "Testimony (what people say)" : "Rumors (unconfirmed)";
            c.drawText(textRenderer, Text.literal(head), dx, dy, color, false);
            dy += 11;
            for (JsonObject e : evs) {
                if (dy > limit) break;
                String src = ClientState.s(e, "source", "");
                dy += Ui.wrapped(c, textRenderer, "• " + ClientState.s(e, "title", "") + ": " + ClientState.s(e, "text", "") + (src.isEmpty() ? "" : " - " + src),
                        dx + 4, dy, dw - 4, Ui.TEXT) + 2;
            }
            dy += 3;
        }
        String guess = ClientState.s(cs, "guess", "");
        String g = "Your theory (a guess, not a fact): ";
        String gt = "none yet";
        for (JsonElement he : cs.getAsJsonArray("hypotheses")) if (he.getAsJsonObject().get("id").getAsString().equals(guess)) gt = ClientState.s(he.getAsJsonObject(), "text", "");
        c.drawText(textRenderer, Text.literal(g + gt), dx, y + h - 34 - 18 * (hypCount - 1) - 12, Ui.ACCENT, false);
    }

    private void renderSettings(DrawContext c, int x, int y) {
        Ui.title(c, textRenderer, "Settings", x + 12, y + 10);
        int ty = y + 24;
        String[] notes = {"", "", "Markers are a small compass pip; off by default feels more exploratory.", "", "",
                "Hints are short nudges shown under quest objectives.",
                "Story: forgiving. Normal: intended. Hard: closer to the designed challenge. Expert: punishing parry windows."};
        for (int i = 0; i < notes.length; i++) {
            if (!notes[i].isEmpty()) Ui.wrapped(c, textRenderer, notes[i], x + 220, ty + i * 18 + 4, pw() - 232, Ui.MUTED);
        }
        Ui.wrapped(c, textRenderer, "Controls are remappable in Options > Controls > The Hollow Atlas.", x + 12, y + ph() - 24, pw() - 24, Ui.MUTED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = px(), y = py();
        if (Ui.inside(mx, my, x + 6, y + 6, 136, ph() - 12)) {
            if (tab == 0) {
                int ly = y + 21;
                boolean header = false;
                for (int i = 0; i < quests.size(); i++) {
                    boolean active = "ACTIVE".equals(ClientState.s(quests.get(i), "status", ""));
                    if (!active && !header) { header = true; ly += 15; }
                    if (my >= ly - 2 && my < ly + 10) { selected = i; rebuild(); return true; }
                    ly += 12;
                }
            } else if (tab == 1) {
                int i = (int) ((my - (y + 8)) / 12);
                if (i >= 0 && i < cases.size()) { selected = i; rebuild(); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (dev.hollowatlas.client.Keys.JOURNAL.matchesKey(key, scan)) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
