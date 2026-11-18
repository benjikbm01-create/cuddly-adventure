package dev.hollowatlas.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.logic.Logic;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.npc.NpcDirector;
import dev.hollowatlas.npc.NpcEntity;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import dev.hollowatlas.world.LocationIndex;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Merchants with real stock. Prices come only from server data; the client sends intents
 * (buy item X / sell slot N) which are validated for proximity, stock, funds and item identity.
 * Stock restocks on new days. Key items can never be sold.
 */
public final class ShopService {
    public static final Map<String, ShopDef> SHOPS = ContentManager.register("shops", ShopDef.class);

    public static final class ShopDef implements ContentManager.Validatable {
        public String id;
        public String title;
        public String npc;              // shopkeeper
        public String anchor;           // fallback location when the keeper is away
        public int restock_days = 1;
        public List<Offer> sells = new ArrayList<>();
        /** item id -> price the merchant pays */
        public Map<String, Integer> buys = new HashMap<>();
        public double buy_back_rate = 0.0;   // generic rate for any non-key item not listed (0 = refuse)
        @Override public String id() { return id; }
        @Override public void validate(String file, List<String> problems) {
            for (Offer o : sells) {
                if (o.price <= 0) problems.add(file + ": bad price for " + o.item);
                if (Registries.ITEM.getId(Logic.item(o.item)).getPath().equals("air")) problems.add(file + ": unknown item " + o.item);
            }
            if (anchor != null && !LocationIndex.has(anchor)) problems.add(file + ": unknown anchor " + anchor);
        }
    }

    public static final class Offer { public String item; public int price; public int stock = 5; public int count = 1; }

    private static final Map<UUID, String> OPEN = new HashMap<>();

    private ShopService() {}

    public static void init() {
        Net.on("shop_buy", (player, body) -> buy(player, body.get("shop").getAsString(), body.get("item").getAsString()));
        Net.on("shop_sell", (player, body) -> sell(player, body.get("shop").getAsString(), body.get("slot").getAsInt()));
        Net.on("shop_close", (player, body) -> OPEN.remove(player.getUuid()));
    }

    private static Map<String, Integer> stock(MinecraftServer server, ShopDef def) {
        GameState gs = GameState.get(server);
        long day = server.getOverworld().getTimeOfDay() / 24000L;
        Map<String, Integer> stock = gs.shopStock.computeIfAbsent(def.id, k -> new HashMap<>());
        long last = gs.shopRestockDay.getOrDefault(def.id, -1L);
        if (last < 0 || day - last >= def.restock_days) {
            for (Offer o : def.sells) stock.put(o.item, o.stock);
            gs.shopRestockDay.put(def.id, day);
            gs.markDirty();
        }
        return stock;
    }

    private static boolean near(ServerPlayerEntity player, ShopDef def) {
        NpcEntity keeper = def.npc == null ? null : NpcDirector.live(def.npc);
        if (keeper != null && keeper.squaredDistanceTo(player) < 8 * 8) return true;
        if (def.anchor != null && LocationIndex.has(def.anchor)) {
            BlockPos a = LocationIndex.pos(def.anchor);
            return player.squaredDistanceTo(a.toCenterPos()) < 8 * 8;
        }
        return false;
    }

    public static void open(ServerPlayerEntity player, String shopId, String npc) {
        ShopDef def = SHOPS.get(shopId);
        if (def == null) return;
        OPEN.put(player.getUuid(), shopId);
        send(player, def);
    }

    private static int sellPrice(ShopDef def, ItemStack stack) {
        if (stack.isEmpty() || ModItems.isKeyItem(stack)) return 0;
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (def.buys.containsKey(id)) return def.buys.get(id);
        for (Offer o : def.sells) if (o.item.equals(id)) return (int) Math.floor(o.price * def.buy_back_rate);
        return 0;
    }

    private static void send(ServerPlayerEntity player, ShopDef def) {
        Map<String, Integer> stock = stock(player.getServer(), def);
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        JsonObject o = new JsonObject();
        o.addProperty("shop", def.id);
        o.addProperty("title", def.title);
        o.addProperty("coins", p.coins);
        JsonArray sells = new JsonArray();
        for (Offer off : def.sells) {
            JsonObject j = new JsonObject();
            j.addProperty("item", off.item);
            j.addProperty("price", off.price);
            j.addProperty("count", off.count);
            j.addProperty("stock", stock.getOrDefault(off.item, 0));
            sells.add(j);
        }
        o.add("sells", sells);
        JsonArray buys = new JsonArray();
        var inv = player.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack s = inv.main.get(i);
            int price = sellPrice(def, s);
            if (price <= 0) continue;
            JsonObject j = new JsonObject();
            j.addProperty("slot", i);
            j.addProperty("item", Registries.ITEM.getId(s.getItem()).toString());
            j.addProperty("count", s.getCount());
            j.addProperty("price", price);
            buys.add(j);
        }
        o.add("buys", buys);
        Net.send(player, "shop", o);
    }

    private static void buy(ServerPlayerEntity player, String shopId, String itemId) {
        ShopDef def = SHOPS.get(shopId);
        if (def == null || !shopId.equals(OPEN.get(player.getUuid())) || !near(player, def)) return;
        Offer off = def.sells.stream().filter(x -> x.item.equals(itemId)).findFirst().orElse(null);
        if (off == null) return;
        Map<String, Integer> stock = stock(player.getServer(), def);
        int have = stock.getOrDefault(itemId, 0);
        GameState gs = GameState.get(player.getServer());
        PlayerProfile p = gs.profile(player.getUuid());
        if (have <= 0) {
            player.sendMessage(Text.translatable("message.hollowatlas.sold_out").formatted(Formatting.GRAY), true);
        } else if (p.coins < off.price) {
            player.sendMessage(Text.translatable("message.hollowatlas.cannot_afford").formatted(Formatting.RED), true);
        } else {
            stock.put(itemId, have - 1);
            p.coins -= off.price;
            gs.markDirty();
            RewardService.give(player, itemId, off.count);
            player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 0.5f, 1.2f);
            HudSync.mark(player);
        }
        send(player, def);
    }

    private static void sell(ServerPlayerEntity player, String shopId, int slot) {
        ShopDef def = SHOPS.get(shopId);
        if (def == null || !shopId.equals(OPEN.get(player.getUuid())) || !near(player, def)) return;
        var inv = player.getInventory();
        if (slot < 0 || slot >= inv.main.size()) return;
        ItemStack s = inv.main.get(slot);
        int price = sellPrice(def, s);
        if (price <= 0) {
            if (ModItems.isKeyItem(s)) player.sendMessage(Text.translatable("message.hollowatlas.cannot_sell_key").formatted(Formatting.RED), true);
            send(player, def);
            return;
        }
        s.decrement(1);
        inv.markDirty();
        GameState gs = GameState.get(player.getServer());
        gs.profile(player.getUuid()).coins += price;
        gs.markDirty();
        player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.4f, 1.4f);
        HudSync.mark(player);
        send(player, def);
    }

    public static Item item(String id) { return Logic.item(id); }
}
