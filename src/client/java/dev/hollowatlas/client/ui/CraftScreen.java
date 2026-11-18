package dev.hollowatlas.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.client.ClientState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Station crafting window (e.g. the inn hearth). */
public class CraftScreen extends Screen {
    private JsonObject data;

    public CraftScreen(JsonObject data) {
        super(Text.translatable("screen.hollowatlas.craft"));
        this.data = data;
    }

    public void update(JsonObject d) {
        data = d;
        if (client != null) init(client, width, height);
    }

    private int px() { return Math.max(4, width / 2 - 170); }
    private int py() { return Math.max(4, height / 2 - 100); }

    private static ItemStack stack(String id, int n) { return new ItemStack(Registries.ITEM.get(Identifier.of(id)), Math.max(1, n)); }

    @Override
    protected void init() {
        clearChildren();
        int row = 0;
        for (JsonElement e : data.getAsJsonArray("recipes")) {
            JsonObject r = e.getAsJsonObject();
            ButtonWidget b = ButtonWidget.builder(Text.literal("Make"), btn -> {
                JsonObject req = new JsonObject();
                req.addProperty("station", data.get("station").getAsString());
                req.addProperty("recipe", r.get("id").getAsString());
                ClientState.send("craft", req);
            }).dimensions(px() + 280, py() + 28 + row * 34, 46, 16).build();
            b.active = r.get("can").getAsBoolean();
            addDrawableChild(b);
            row++;
        }
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int x = px(), y = py();
        Ui.panel(c, x, y, 340, 200);
        for (var child : children()) if (child instanceof net.minecraft.client.gui.Drawable d) d.render(c, mx, my, delta);
        String station = ClientState.s(data, "station", "");
        Ui.title(c, textRenderer, station.equals("hearth") ? "Cooking at the hearth" : "Workbench", x + 10, y + 9);
        int row = 0;
        if (data.getAsJsonArray("recipes").isEmpty()) c.drawText(textRenderer, Text.literal("You don't know any recipes for this yet."), x + 10, y + 30, Ui.MUTED, false);
        for (JsonElement e : data.getAsJsonArray("recipes")) {
            JsonObject r = e.getAsJsonObject();
            int ry = y + 26 + row * 34;
            ItemStack out = stack(r.get("output").getAsString(), r.get("output_count").getAsInt());
            c.drawItem(out, x + 10, ry);
            c.drawItemInSlot(textRenderer, out, x + 10, ry);
            c.drawText(textRenderer, Text.literal(ClientState.s(r, "name", "")), x + 30, ry + 4, Ui.TITLE, false);
            int ix = x + 30;
            for (JsonElement ie : r.getAsJsonArray("inputs")) {
                JsonObject in = ie.getAsJsonObject();
                ItemStack s = stack(in.get("item").getAsString(), 1);
                int have = in.get("have").getAsInt(), need = in.get("count").getAsInt();
                c.drawItem(s, ix, ry + 14);
                c.drawText(textRenderer, Text.literal(have + "/" + need), ix + 17, ry + 19, have >= need ? Ui.GOOD : Ui.BAD, false);
                if (Ui.inside(mx, my, ix, ry + 14, 16, 16)) c.drawItemTooltip(textRenderer, s, mx, my);
                ix += 46;
            }
            if (Ui.inside(mx, my, x + 10, ry, 16, 16)) c.drawItemTooltip(textRenderer, out, mx, my);
            row++;
        }
    }
}
