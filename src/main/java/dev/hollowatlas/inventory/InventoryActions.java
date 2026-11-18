package dev.hollowatlas.inventory;

import dev.hollowatlas.net.Net;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/** Validated inventory operations requested by the satchel screen, plus player settings. */
public final class InventoryActions {
    private InventoryActions() {}

    public static void init() {
        Net.on("inv_hotbar", (player, body) -> {
            int from = body.get("from").getAsInt(), to = body.get("to").getAsInt();
            PlayerInventory inv = player.getInventory();
            if (to < 0 || to > 8 || from < 0 || from >= inv.main.size() || from == to) return;
            ItemStack a = inv.main.get(from), b = inv.main.get(to);
            inv.main.set(from, b);
            inv.main.set(to, a);
            inv.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        });
        Net.on("inv_equip", (player, body) -> {
            int slot = body.get("slot").getAsInt();
            PlayerInventory inv = player.getInventory();
            if (slot >= 36 && slot < 40) {
                // unequip worn armor into the first free main slot
                int armorIdx = slot - 36;
                ItemStack worn = inv.armor.get(armorIdx);
                int free = inv.getEmptySlot();
                if (worn.isEmpty() || free < 0) return;
                inv.main.set(free, worn);
                inv.armor.set(armorIdx, ItemStack.EMPTY);
            } else {
                if (slot < 0 || slot >= inv.main.size()) return;
                ItemStack s = inv.main.get(slot);
                if (!(s.getItem() instanceof ArmorItem armor)) return;
                EquipmentSlot es = armor.getSlotType();
                ItemStack old = player.getEquippedStack(es);
                player.equipStack(es, s);
                inv.main.set(slot, old);
            }
            inv.markDirty();
            player.currentScreenHandler.sendContentUpdates();
        });
        Net.on("setting", (player, body) -> {
            String key = body.get("key").getAsString(), value = body.get("value").getAsString();
            GameState gs = GameState.get(player.getServer());
            PlayerProfile p = gs.profile(player.getUuid());
            switch (key) {
                case "guidance" -> {
                    if (!List.of("on", "off").contains(value)) return;
                    p.settings.put("guidance", value);
                }
                case "difficulty" -> {
                    if (!List.of("story", "normal", "hard", "expert").contains(value)) return;
                    // single-player host or operators only
                    if (!player.hasPermissionLevel(2) && !player.getServer().isHost(player.getGameProfile())) return;
                    gs.difficulty = value;
                }
                default -> { return; }
            }
            gs.markDirty();
            QuestEngine.sync(player);
            HudSync.mark(player);
        });
    }

    public static void unused(ServerPlayerEntity p) { }
}
