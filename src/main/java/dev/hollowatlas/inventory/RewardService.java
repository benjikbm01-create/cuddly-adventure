package dev.hollowatlas.inventory;

import dev.hollowatlas.logic.Logic;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Iterator;
import java.util.Map;

/**
 * The only path by which the game hands out items and currency. Items that do not fit are held in
 * the player's profile and delivered automatically as soon as there is room (never dropped or lost).
 */
public final class RewardService {
    private RewardService() {}

    public static ItemStack create(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        if (item instanceof ArmorItem || item instanceof ToolItem || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof ShieldItem) {
            stack.set(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)); // no durability, ever
        }
        return stack;
    }

    public static void give(ServerPlayerEntity player, String itemId, int count) {
        Item item = Logic.item(itemId);
        if (item == Items.AIR || count <= 0) return;
        if (item instanceof ModItems.KeyItem && count(player, item) > 0) return; // key items are unique: never duplicate
        int left = insert(player, item, count);
        int given = count - left;
        if (given > 0) {
            player.sendMessage(Text.translatable("message.hollowatlas.received", given, create(item, 1).getName())
                    .formatted(Formatting.GREEN), false);
            player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
        }
        if (left > 0) {
            GameState state = GameState.get(player.getServer());
            state.profile(player.getUuid()).pendingRewards.merge(itemId, left, Integer::sum);
            state.markDirty();
            player.sendMessage(Text.translatable("message.hollowatlas.pack_full").formatted(Formatting.YELLOW), false);
        }
        QuestEngine.poll(player);
    }

    private static int insert(ServerPlayerEntity player, Item item, int count) {
        int left = count;
        int max = item.getMaxCount();
        while (left > 0) {
            ItemStack stack = create(item, Math.min(max, left));
            int before = stack.getCount();
            player.getInventory().insertStack(stack);
            int placed = before - stack.getCount();
            if (placed <= 0) break;
            left -= placed;
        }
        return left;
    }

    public static int count(ServerPlayerEntity player, Item item) {
        if (item == null || item == Items.AIR) return 0;
        PlayerInventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) n += s.getCount();
        }
        return n;
    }

    public static boolean take(ServerPlayerEntity player, Item item, int count) {
        if (count(player, item) < count) return false;
        PlayerInventory inv = player.getInventory();
        int left = count;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) {
                int t = Math.min(left, s.getCount());
                s.decrement(t);
                left -= t;
            }
        }
        inv.markDirty();
        return true;
    }

    public static void coins(ServerPlayerEntity player, int delta) {
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        p.coins = Math.max(0, p.coins + delta);
        state.markDirty();
        if (delta != 0) {
            player.sendMessage(Text.translatable(delta > 0 ? "message.hollowatlas.coins_gain" : "message.hollowatlas.coins_spend",
                    Math.abs(delta)).formatted(delta > 0 ? Formatting.GOLD : Formatting.GRAY), true);
        }
        HudSync.mark(player);
    }

    /** Deliver queued rewards when space frees up. */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 3) return;
        GameState state = GameState.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerProfile p = state.profile(player.getUuid());
            if (p.pendingRewards.isEmpty() || player.getInventory().getEmptySlot() < 0) continue;
            Iterator<Map.Entry<String, Integer>> it = p.pendingRewards.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> e = it.next();
                int left = insert(player, Logic.item(e.getKey()), e.getValue());
                if (left <= 0) it.remove(); else e.setValue(left);
                state.markDirty();
            }
            player.sendMessage(Text.translatable("message.hollowatlas.pending_delivered").formatted(Formatting.GREEN), true);
        }
    }
}
