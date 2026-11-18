package dev.hollowatlas.inventory;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.TridentItem;

/** Inventory categories for the RPG satchel. Derived from the item itself, so it can never desync. */
public enum ItemCategory {
    WEAPONS, ARMOR, CONSUMABLES, MATERIALS, QUEST, VALUABLES;

    public String key() { return "category.hollowatlas." + name().toLowerCase(java.util.Locale.ROOT); }

    public static ItemCategory of(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ModItems.KeyItem) return QUEST;
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof MaceItem) return WEAPONS;
        if (item instanceof ArmorItem || item instanceof ShieldItem) return ARMOR;
        if (stack.contains(DataComponentTypes.FOOD) || item == Items.POTION) return CONSUMABLES;
        if (item == ModItems.SILVER_LOCKET || item == ModItems.OLD_COIN || item == ModItems.COURIER_CHARM
                || item == Items.EMERALD || item == Items.GOLD_NUGGET || item == Items.AMETHYST_SHARD) return VALUABLES;
        return MATERIALS;
    }
}
