package dev.hollowatlas.inventory;

import dev.hollowatlas.HollowAtlas;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hollow Atlas items. Textures reuse vanilla art via item models until the 32x pack is supplied.
 * Weapons and armor never lose durability ({@link DataComponentTypes#UNBREAKABLE}, hidden tooltip).
 */
public final class ModItems {
    public static final Map<String, Item> ALL = new LinkedHashMap<>();

    /** Story-critical item: cannot be dropped, sold, consumed or duplicated by normal play. */
    public static class KeyItem extends Item {
        public KeyItem(Settings s) { super(s.maxCount(1).rarity(Rarity.UNCOMMON)); }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("tooltip.hollowatlas.key_item").formatted(Formatting.GOLD));
            String key = getTranslationKey() + ".desc";
            tooltip.add(Text.translatable(key).formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    }

    /** Hotbar token for an ability: put it in any hotbar slot and use it like an item. */
    public static class AbilityItem extends KeyItem {
        public final String ability;
        public AbilityItem(String ability, Settings s) { super(s); this.ability = ability; }

        @Override
        public net.minecraft.util.TypedActionResult<ItemStack> use(net.minecraft.world.World world, net.minecraft.entity.player.PlayerEntity user, net.minecraft.util.Hand hand) {
            if (!world.isClient && user instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                dev.hollowatlas.ability.AbilityService.use(sp, ability);
            }
            return net.minecraft.util.TypedActionResult.success(user.getStackInHand(hand), world.isClient);
        }
    }

    /** Plain item with a flavour line. */
    public static class Described extends Item {
        public Described(Settings s) { super(s); }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    }

    /** Weapon with authored stats and no durability. */
    public static class Weapon extends SwordItem {
        public final String style;

        public Weapon(String style, int damage, float speed, Settings s) {
            super(ToolMaterials.IRON, s.attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.IRON, damage, speed))
                    .component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)));
            this.style = style;
        }

        @Override
        public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("tooltip.hollowatlas.style." + style).formatted(Formatting.DARK_AQUA));
            tooltip.add(Text.translatable(getTranslationKey() + ".desc").formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    }

    // key items
    public static final Item ROOM_KEY = reg("room_key", new KeyItem(new Item.Settings()));
    public static final Item SEALED_PARCEL = reg("sealed_parcel", new KeyItem(new Item.Settings()));
    public static final Item VALVE_LEATHER = reg("valve_leather", new KeyItem(new Item.Settings()));
    public static final Item PUMP_PIN = reg("pump_pin", new KeyItem(new Item.Settings()));
    public static final Item BLUE_RIBBON = reg("blue_ribbon", new KeyItem(new Item.Settings()));
    public static final Item ROUTE_NOTES = reg("route_notes", new KeyItem(new Item.Settings()));
    public static final Item TRACER_LENS = reg("tracer_lens", new AbilityItem("echo_sight", new Item.Settings().rarity(Rarity.RARE)));
    public static final Item TECHNIQUE_WINDSTEP = reg("technique_windstep", new AbilityItem("windstep", new Item.Settings()));
    public static final Item HERBAL_REMEDY = reg("herbal_remedy", new KeyItem(new Item.Settings()));

    // materials
    public static final Item YARROW = reg("yarrow", new Described(new Item.Settings()));
    public static final Item FEVERFEW = reg("feverfew", new Described(new Item.Settings()));
    public static final Item WILD_HONEY = reg("wild_honey", new Described(new Item.Settings()));

    // consumables
    public static final Item HEARTH_STEW = reg("hearth_stew", new Described(new Item.Settings().maxCount(16).food(
            new FoodComponent.Builder().nutrition(8).saturationModifier(0.8f)
                    .statusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0), 1f).build())));
    public static final Item HONEY_BUN = reg("honey_bun", new Described(new Item.Settings().maxCount(16).food(
            new FoodComponent.Builder().nutrition(5).saturationModifier(0.6f).alwaysEdible().build())));
    public static final Item TRAIL_TONIC = reg("trail_tonic", new Described(new Item.Settings().maxCount(8).food(
            new FoodComponent.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible()
                    .statusEffect(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1, 0), 1f).build())));

    // valuables and charms
    public static final Item SILVER_LOCKET = reg("silver_locket", new Described(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
    public static final Item OLD_COIN = reg("old_coin", new Described(new Item.Settings().maxCount(64)));
    public static final Item COURIER_CHARM = reg("courier_charm", new Described(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    // weapons
    public static final Item TRAVELER_SHORTSWORD = reg("traveler_shortsword", new Weapon("balanced", 3, -2.2f, new Item.Settings()));
    public static final Item ASH_QUARTERSTAFF = reg("ash_quarterstaff", new Weapon("sweeping", 2, -2.6f, new Item.Settings()));
    public static final Item HOLT_HATCHET = reg("holt_hatchet", new Weapon("heavy", 5, -3.0f, new Item.Settings().rarity(Rarity.UNCOMMON)));

    private ModItems() {}

    private static Item reg(String id, Item item) {
        Registry.register(Registries.ITEM, HollowAtlas.id(id), item);
        ALL.put(id, item);
        return item;
    }

    public static void init() { }

    public static boolean isKeyItem(ItemStack stack) {
        return stack.getItem() instanceof KeyItem;
    }
}
