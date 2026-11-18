package dev.hollowatlas.world;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.AbstractCauldronBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoatItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Adventure-map protection. Players cannot break or place blocks, open other people's containers,
 * use workstations, pour fluids or bypass the map with pearls. Designated interactions
 * (gathering nodes, puzzles, clues, stations) are handled before this by {@code Interactions}.
 * Operators can toggle builder mode (creative, no restrictions) with {@code /atlas builder}.
 */
public final class ProtectionService {
    private static final Set<UUID> BUILDERS = new HashSet<>();
    private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

    private ProtectionService() {}

    public static boolean isBuilder(PlayerEntity player) {
        // the scenery showcase world is a free-roam sandbox: fly, build, break
        if (player.getServer() != null && dev.hollowatlas.state.WorldMode.showcase(player.getServer())) return true;
        return BUILDERS.contains(player.getUuid()) && player.hasPermissionLevel(2);
    }

    public static void setBuilder(ServerPlayerEntity player, boolean on) {
        if (on) {
            BUILDERS.add(player.getUuid());
            player.changeGameMode(GameMode.CREATIVE);
        } else {
            BUILDERS.remove(player.getUuid());
            player.changeGameMode(GameMode.ADVENTURE);
        }
    }

    /** Called on join/respawn: everyone who is not an active builder plays in adventure mode. */
    public static void enforceMode(ServerPlayerEntity player) {
        if (dev.hollowatlas.state.WorldMode.showcase(player.getServer())) {
            if (player.interactionManager.getGameMode() == GameMode.SURVIVAL || player.interactionManager.getGameMode() == GameMode.ADVENTURE)
                player.changeGameMode(GameMode.CREATIVE);
            return;
        }
        if (isBuilder(player)) return;
        if (player.interactionManager.getGameMode() != GameMode.ADVENTURE && !player.isSpectator()) {
            player.changeGameMode(GameMode.ADVENTURE);
        }
    }

    private static void deny(PlayerEntity player, String key) {
        if (player.getWorld().isClient) return;
        long now = player.getWorld().getTime();
        Long last = LAST_MESSAGE.get(player.getUuid());
        if (last != null && now - last < 40) return;
        LAST_MESSAGE.put(player.getUuid(), now);
        player.sendMessage(Text.translatable(key).formatted(Formatting.GRAY, Formatting.ITALIC), true);
    }

    public static boolean isRestrictedBlock(BlockState state) {
        if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.TRAPDOORS) || state.isIn(BlockTags.FENCE_GATES)
                || state.isIn(BlockTags.BEDS) || state.isIn(BlockTags.BUTTONS)) {
            return false;
        }
        Block b = state.getBlock();
        if (b instanceof BlockWithEntity) return true;       // chests, barrels, furnaces, signs, lecterns...
        if (b instanceof AbstractCauldronBlock || b instanceof FlowerPotBlock || b instanceof SweetBerryBushBlock) return true;
        return b == Blocks.CRAFTING_TABLE || b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL
                || b == Blocks.GRINDSTONE || b == Blocks.STONECUTTER || b == Blocks.SMITHING_TABLE
                || b == Blocks.CARTOGRAPHY_TABLE || b == Blocks.LOOM || b == Blocks.COMPOSTER || b == Blocks.CAKE
                || b == Blocks.NOTE_BLOCK || b == Blocks.LEVER || b == Blocks.REPEATER || b == Blocks.COMPARATOR
                || b == Blocks.RESPAWN_ANCHOR || b == Blocks.CAVE_VINES || b == Blocks.CAVE_VINES_PLANT
                || b == Blocks.DRAGON_EGG || b == Blocks.REDSTONE_WIRE;
    }

    private static boolean isWorldAlteringItem(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BlockItem || item instanceof BucketItem || item instanceof SpawnEggItem
                || item instanceof FlintAndSteelItem || item instanceof FireChargeItem || item instanceof BoatItem
                || item instanceof EnderPearlItem || item == Items.CHORUS_FRUIT || item == Items.BONE_MEAL
                || item == Items.END_CRYSTAL || item == Items.ARMOR_STAND || item == Items.ITEM_FRAME
                || item == Items.GLOW_ITEM_FRAME || item == Items.PAINTING || item == Items.MINECART
                || item == Items.WIND_CHARGE;
    }

    private static boolean isDecoration(Entity e) {
        return e instanceof ItemFrameEntity || e instanceof ArmorStandEntity || e instanceof PaintingEntity;
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (isBuilder(player)) return true;
            deny(player, "message.hollowatlas.protected.break");
            return false;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
                isBuilder(player) ? ActionResult.PASS : ActionResult.FAIL);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (isBuilder(player) || player.isSpectator()) return ActionResult.PASS;
            BlockState state = world.getBlockState(hit.getBlockPos());
            if (isRestrictedBlock(state)) {
                deny(player, "message.hollowatlas.protected.use");
                return ActionResult.FAIL;
            }
            if (isWorldAlteringItem(player.getStackInHand(hand))) {
                // allow doors/gates/beds even while holding a block, never placement
                if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.FENCE_GATES) || state.isIn(BlockTags.TRAPDOORS) || state.isIn(BlockTags.BEDS)) {
                    return ActionResult.PASS;
                }
                deny(player, "message.hollowatlas.protected.place");
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (isBuilder(player) || !isWorldAlteringItem(stack) || stack.getItem() instanceof BlockItem) {
                return TypedActionResult.pass(stack);
            }
            deny(player, "message.hollowatlas.protected.item");
            return TypedActionResult.fail(stack);
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!isBuilder(player) && isDecoration(entity)) return ActionResult.FAIL;
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!isBuilder(player) && isDecoration(entity)) return ActionResult.FAIL;
            return ActionResult.PASS;
        });
    }
}
