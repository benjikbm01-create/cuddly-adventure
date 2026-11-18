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

/** Merchant window. Shows server-provided stock/prices; buttons send intents only. */
public class ShopScreen extends Screen {
    private JsonObject data;

    public ShopScreen(JsonObject data) {
        super(Text.translatable("screen.hollowatlas.shop"));
        this.data = data;
    }

    public void update(JsonObject d) {
        data = d;
        if (client != null) init(client, width, height);
    }

    private int px() { return Math.max(4, width / 2 - 210); }
    private int py() { return Math.max(4, height / 2 - 110); }

    private static ItemStack stack(String id, int n) { return new ItemStack(Registries.ITEM.get(Identifier.of(id)), Math.max(1, n)); }

    @Override
    protected void init() {
        clearChildren();
        int x = px(), y = py();
        int row = 0;
        for (JsonElement e : data.getAsJsonArray("sells")) {
            JsonObject o = e.getAsJsonObject();
            int stock = o.get("stock").getAsInt(), price = o.get("price").getAsInt();
            ButtonWidget b = ButtonWidget.builder(Text.literal("Buy " + price), btn -> {
                JsonObject req = new JsonObject();
                req.addProperty("shop", data.get("shop").getAsString());
                req.addProperty("item", o.get("item").getAsString());
                ClientState.send("shop_buy", req);
            }).dimensions(x + 150, y + 30 + row * 20, 52, 16).build();
            b.active = stock > 0 && ClientState.i(data, "coins", 0) >= price;
            addDrawableChild(b);
            row++;
        }
        row = 0;
        for (JsonElement e : data.getAsJsonArray("buys")) {
            JsonObject o = e.getAsJsonObject();
            if (row > 8) break;
            addDrawableChild(ButtonWidget.builder(Text.literal("Sell " + o.get("price").getAsInt()), btn -> {
                JsonObject req = new JsonObject();
                req.addProperty("shop", data.get("shop").getAsString());
                req.addProperty("slot", o.get("slot").getAsInt());
                ClientState.send("shop_sell", req);
            }).dimensions(x + 360, y + 30 + row * 20, 52, 16).build());
            row++;
        }
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        int x = px(), y = py();
        Ui.panel(c, x, y, 420, 220);
        for (var child : children()) if (child instanceof net.minecraft.client.gui.Drawable d) d.render(c, mx, my, delta);
        Ui.title(c, textRenderer, ClientState.s(data, "title", "Shop"), x + 10, y + 9);
        c.drawText(textRenderer, Text.literal("● " + ClientState.i(data, "coins", 0)), x + 360, y + 9, 0xFFE8C060, false);
        c.drawText(textRenderer, Text.literal("For sale"), x + 10, y + 20, Ui.MUTED, false);
        c.drawText(textRenderer, Text.literal("Your goods"), x + 216, y + 20, Ui.MUTED, false);
        int row = 0;
        for (JsonElement e : data.getAsJsonArray("sells")) {
            JsonObject o = e.getAsJsonObject();
            int ry = y + 30 + row * 20;
            ItemStack s = stack(o.get("item").getAsString(), o.get("count").getAsInt());
            c.drawItem(s, x + 10, ry);
            c.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(s.getName().getString(), 90)), x + 30, ry + 4, Ui.TEXT, false);
            int stock = o.get("stock").getAsInt();
            c.drawText(textRenderer, Text.literal(stock > 0 ? "x" + stock : "sold out"), x + 122, ry + 4, stock > 0 ? Ui.MUTED : Ui.BAD, false);
            if (Ui.inside(mx, my, x + 8, ry, 110, 18)) c.drawItemTooltip(textRenderer, s, mx, my);
            row++;
        }
        row = 0;
        if (data.getAsJsonArray("buys").isEmpty()) c.drawText(textRenderer, Text.literal("Nothing this merchant wants."), x + 216, y + 34, Ui.MUTED, false);
        for (JsonElement e : data.getAsJsonArray("buys")) {
            if (row > 8) break;
            JsonObject o = e.getAsJsonObject();
            int ry = y + 30 + row * 20;
            ItemStack s = stack(o.get("item").getAsString(), o.get("count").getAsInt());
            c.drawItemInSlot(textRenderer, s, x + 216, ry);
            c.drawItem(s, x + 216, ry);
            c.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(s.getName().getString(), 110)), x + 236, ry + 4, Ui.TEXT, false);
            row++;
        }
        c.drawText(textRenderer, Text.literal("Quest items can't be sold. Stock refreshes daily."), x + 10, y + 206, Ui.MUTED, false);
    }

    @Override
    public void close() {
        ClientState.send("shop_close");
        super.close();
    }
}
