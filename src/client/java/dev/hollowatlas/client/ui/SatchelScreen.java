package dev.hollowatlas.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.client.ClientState;
import dev.hollowatlas.inventory.ItemCategory;
import dev.hollowatlas.inventory.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Category-based RPG inventory over the player's REAL inventory (no separate fake storage).
 * Left: category filter + search; middle: the items actually carried; right: details, equipment
 * and the nine-slot loadout bar. Moves are requested from the server, which validates them.
 */
public class SatchelScreen extends Screen {
    private ItemCategory filter = null;   // null = all
    private TextFieldWidget search;
    private final List<Integer> rows = new ArrayList<>();   // inventory slot indices
    private int selected = -1;
    private int scroll = 0;

    public SatchelScreen() { super(Text.translatable("screen.hollowatlas.satchel")); }

    private int px() { return Math.max(4, width / 2 - 230); }
    private int py() { return Math.max(4, height / 2 - 118); }
    private int pw() { return Math.min(460, width - 8); }
    private int ph() { return Math.min(236, height - 8); }

    @Override
    protected void init() {
        search = new TextFieldWidget(textRenderer, px() + 8, py() + 8, 96, 14, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search..."));
        search.setChangedListener(s -> { scroll = 0; rebuild(); });
        rebuild();
    }

    private void rebuild() {
        String q = search == null ? "" : search.getText();
        clearChildren();
        addDrawableChild(search);
        int x = px() + 8, y = py() + 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("All"), b -> { filter = null; scroll = 0; rebuild(); }).dimensions(x, y, 96, 15).build()).active = filter != null;
        int i = 1;
        for (ItemCategory cat : ItemCategory.values()) {
            ButtonWidget bw = ButtonWidget.builder(Text.translatable(cat.key()), b -> { filter = cat; scroll = 0; rebuild(); })
                    .dimensions(x, y + i * 17, 96, 15).build();
            bw.active = filter != cat;
            addDrawableChild(bw);
            i++;
        }
        rows.clear();
        PlayerInventory inv = client.player.getInventory();
        for (int slot = 0; slot < inv.size(); slot++) {
            ItemStack s = inv.getStack(slot);
            if (s.isEmpty()) continue;
            if (filter != null && ItemCategory.of(s) != filter) continue;
            if (!q.isEmpty() && !s.getName().getString().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) continue;
            rows.add(slot);
        }
        if (!rows.contains(selected)) selected = rows.isEmpty() ? -1 : rows.get(0);
        // actions for the selected item
        if (selected >= 0) {
            ItemStack s = inv.getStack(selected);
            int ax = px() + pw() - 150, ay = py() + 120;
            if (s.getItem() instanceof ArmorItem) {
                addDrawableChild(ButtonWidget.builder(Text.literal(selected >= 36 && selected < 40 ? "Unequip" : "Equip"), b -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("slot", selected);
                    ClientState.send("inv_equip", o);
                }).dimensions(ax, ay, 140, 15).build());
                ay += 17;
            }
            if (s.getItem() instanceof ModItems.AbilityItem ai) {
                for (int k = 0; k < 2; k++) {
                    final int slotK = k;
                    addDrawableChild(ButtonWidget.builder(Text.literal("Set as ability " + (k == 0 ? "1" : "2")), b -> {
                        JsonObject o = new JsonObject();
                        o.addProperty("slot", slotK);
                        o.addProperty("id", ai.ability);
                        ClientState.send("ability_slot", o);
                    }).dimensions(ax + k * 71, ay, 69, 15).build());
                }
                ay += 17;
            }
            // assign to hotbar slot 1-9
            for (int hb = 0; hb < 9; hb++) {
                final int target = hb;
                ButtonWidget b = ButtonWidget.builder(Text.literal(Integer.toString(hb + 1)), btn -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("from", selected);
                    o.addProperty("to", target);
                    ClientState.send("inv_hotbar", o);
                }).dimensions(ax + hb * 15 + 5, ay + 26, 14, 14).build();
                b.active = selected != hb;
                addDrawableChild(b);
            }
        }
    }

    private long lastSnapshot;

    @Override
    public void tick() {
        // inventory may change from server actions; rebuild periodically
        if (System.currentTimeMillis() - lastSnapshot > 500) {
            lastSnapshot = System.currentTimeMillis();
            String q = search.getText();
            boolean focused = search.isFocused();
            rebuild();
            search.setText(q);
            search.setFocused(focused);
        }
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int x = px(), y = py(), w = pw(), h = ph();
        Ui.panel(c, x, y, w, h);
        for (var child : children()) if (child instanceof net.minecraft.client.gui.Drawable d) d.render(c, mx, my, delta);
        // item list
        int lx = x + 112, lw = 170;
        Ui.inset(c, lx, y + 8, lw, h - 16);
        PlayerInventory inv = client.player.getInventory();
        int visible = (h - 22) / 18;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        if (rows.isEmpty()) c.drawText(textRenderer, Text.literal("Nothing here."), lx + 6, y + 14, Ui.MUTED, false);
        for (int r = 0; r < visible && r + scroll < rows.size(); r++) {
            int slot = rows.get(r + scroll);
            ItemStack s = inv.getStack(slot);
            int ry = y + 11 + r * 18;
            if (slot == selected) c.fill(lx + 1, ry - 1, lx + lw - 1, ry + 17, Ui.SELECT);
            c.drawItem(s, lx + 4, ry);
            String name = textRenderer.trimToWidth(s.getName().getString(), lw - 74);
            c.drawText(textRenderer, Text.literal(name), lx + 24, ry + 4, ModItems.isKeyItem(s) ? Ui.TITLE : Ui.TEXT, false);
            if (s.getCount() > 1) c.drawText(textRenderer, Text.literal("x" + s.getCount()), lx + lw - 24, ry + 4, Ui.MUTED, false);
            String where = slot < 9 ? "[" + (slot + 1) + "]" : slot >= 36 && slot < 40 ? "worn" : slot == 40 ? "off" : "";
            if (!where.isEmpty()) c.drawText(textRenderer, Text.literal(where), lx + lw - 46, ry + 4, Ui.ACCENT, false);
        }
        // details panel
        int dx = x + w - 158, dw = 150;
        Ui.inset(c, dx - 4, y + 8, dw + 4, h - 16);
        if (selected >= 0 && selected < inv.size()) {
            ItemStack s = inv.getStack(selected);
            c.drawItem(s, dx, y + 12);
            Ui.title(c, textRenderer, textRenderer.trimToWidth(s.getName().getString(), dw - 22), dx + 20, y + 16);
            c.drawText(textRenderer, Text.translatable(ItemCategory.of(s).key()), dx, y + 32, Ui.MUTED, false);
            List<Text> tip = Screen.getTooltipFromItem(client, s);
            int ty = y + 44;
            for (int i = 1; i < tip.size() && ty < y + 112; i++) {
                ty += Ui.wrapped(c, textRenderer, tip.get(i).getString(), dx, ty, dw - 4, Ui.TEXT);
            }
            c.drawText(textRenderer, Text.literal("Place on hotbar slot:"), dx, y + 136 + (s.getItem() instanceof ArmorItem || s.getItem() instanceof ModItems.AbilityItem ? 17 : 0) - 2 + 0, Ui.MUTED, false);
        }
        // equipment + coins footer
        int fy = y + h - 26;
        c.drawText(textRenderer, Text.literal("Worn:"), dx, fy - 22, Ui.MUTED, false);
        int ex = dx + 32;
        for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = client.player.getEquippedStack(es);
            c.fill(ex, fy - 26, ex + 18, fy - 8, 0x60000000);
            if (!worn.isEmpty()) c.drawItem(worn, ex + 1, fy - 25);
            ex += 20;
        }
        c.drawText(textRenderer, Text.literal("● " + ClientState.i(ClientState.hud, "coins", 0) + " coins"), dx, fy, 0xFFE8C060, false);
        StringBuilder ab = new StringBuilder("Abilities: ");
        if (ClientState.hud.has("abilities")) for (JsonElement e : ClientState.hud.getAsJsonArray("abilities")) {
            JsonObject a = e.getAsJsonObject();
            int slot = a.get("slot").getAsInt();
            ab.append(a.get("name").getAsString()).append(slot >= 0 ? " [" + (slot + 1) + "] " : " ");
        }
        c.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(ab.toString(), dw)), dx, fy + 10, Ui.MUTED, false);
        // hover tooltip
        for (int r = 0; r < visible && r + scroll < rows.size(); r++) {
            int ry = y + 11 + r * 18;
            if (Ui.inside(mx, my, lx, ry - 1, lw, 18)) c.drawItemTooltip(textRenderer, inv.getStack(rows.get(r + scroll)), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = px() + 112, lw = 170, y = py();
        if (Ui.inside(mx, my, lx, y + 8, lw, ph() - 16)) {
            int r = (int) ((my - (y + 10)) / 18) + scroll;
            if (r >= 0 && r < rows.size()) { selected = rows.get(r); rebuild(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll -= (int) Math.signum(v);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!search.isFocused() && (dev.hollowatlas.client.Keys.SATCHEL.matchesKey(key, scan) || client.options.inventoryKey.matchesKey(key, scan))) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
