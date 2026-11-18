package dev.hollowatlas.investigation;

import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.inventory.CraftingService;
import dev.hollowatlas.inventory.RewardService;
import dev.hollowatlas.logic.Logic;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import dev.hollowatlas.world.LocationIndex;
import dev.hollowatlas.world.WorldChanges;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Designated world interactions bound to location anchors: clue inspection, reading, gathering
 * nodes with timed regrowth, lever puzzles, crafting stations, caches and rest spots.
 * These are the ONLY block interactions players get beyond doors; everything else is protected.
 */
public final class Interactions {
    public static final Map<String, Bundle> BUNDLES = ContentManager.register("interactions", Bundle.class);

    public static final class Bundle implements ContentManager.Validatable {
        public String id;
        public List<Def> interactions = new ArrayList<>();
        public List<Puzzle> puzzles = new ArrayList<>();
        @Override public String id() { return id; }
        @Override public void validate(String file, List<String> problems) {
            for (Def d : interactions) {
                if (d.anchor == null || !LocationIndex.has(d.anchor)) problems.add(file + ": interaction " + d.id + " has unknown anchor " + d.anchor);
                Logic.validateActions(file + "#" + d.id, d.actions, problems);
            }
        }
    }

    public static final class Def {
        public String id;
        public String anchor;
        public int dx, dy, dz;
        public String type = "inspect";     // inspect | read | gather | puzzle | station | cache | rest
        public JsonElement when;             // condition; if false, show locked_text
        public String text;
        public String locked_text;
        public String repeat_text;           // shown on revisits of a once-only interaction
        public boolean once = false;         // per player
        public String evidence;
        public JsonElement actions;
        public List<Loot> loot = new ArrayList<>();
        public int regrow_minutes = 10;      // gather nodes (real minutes)
        public String depleted_state;        // block shown while regrowing
        public String ripe_state;            // block restored when ready
        public String puzzle;                // puzzle id
        public String symbol;                // puzzle input symbol
        public String station;               // crafting station id
        /** Context-dependent responses: the first whose condition holds replaces text/actions/evidence. */
        public List<Variant> variants = new ArrayList<>();
    }

    public static final class Variant {
        public String id;
        public JsonElement when;
        public String text;
        public String evidence;
        public JsonElement actions;
        public boolean once = true;
    }

    public static final class Loot { public String item; public int min = 1, max = 1; public double chance = 1.0; }

    public static final class Puzzle {
        public String id;
        public List<String> sequence = new ArrayList<>();
        public String solved_flag;
        public JsonElement on_solve;
        public String reset_text, solved_text;
    }

    private static final Map<BlockPos, Def> BY_POS = new HashMap<>();
    private static final Map<String, Puzzle> PUZZLES = new HashMap<>();
    private static final Map<UUID, List<String>> PUZZLE_INPUT = new HashMap<>();
    private static int indexedLoad = -1;

    private Interactions() {}

    private static void reindex() {
        if (indexedLoad == ContentManager.loads()) return;
        indexedLoad = ContentManager.loads();
        BY_POS.clear();
        PUZZLES.clear();
        for (Bundle b : BUNDLES.values()) {
            for (Def d : b.interactions) {
                if (d.anchor != null && LocationIndex.has(d.anchor)) BY_POS.put(LocationIndex.pos(d.anchor).add(d.dx, d.dy, d.dz), d);
            }
            for (Puzzle p : b.puzzles) PUZZLES.put(p.id, p);
        }
    }

    public static Map<BlockPos, Def> all() { reindex(); return BY_POS; }

    public static void init() {
        // Registered before ProtectionService so designated interactions win.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity sp)) {
                if (world.isClient && hand == Hand.MAIN_HAND) {
                    return BY_POS.containsKey(hit.getBlockPos()) ? ActionResult.SUCCESS : ActionResult.PASS;
                }
                return ActionResult.PASS;
            }
            reindex();
            Def d = BY_POS.get(hit.getBlockPos());
            if (d == null) return ActionResult.PASS;
            handle(sp, d, hit.getBlockPos());
            return ActionResult.SUCCESS;
        });
    }

    private static void say(ServerPlayerEntity p, String text, Formatting color) {
        if (text != null && !text.isEmpty()) p.sendMessage(Text.literal(text).formatted(color, Formatting.ITALIC), false);
    }

    private static void handle(ServerPlayerEntity player, Def d, BlockPos pos) {
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        Logic.Ctx ctx = Logic.Ctx.NONE;
        if (!Logic.test(player, d.when, ctx)) {
            say(player, d.locked_text, Formatting.GRAY);
            return;
        }
        String doneKey = "int:" + d.id;
        for (Variant v : d.variants) {
            if (!Logic.test(player, v.when, ctx)) continue;
            String vk = "int:" + v.id;
            boolean first = p.flags.add(vk);
            state.markDirty();
            say(player, v.text, Formatting.WHITE);
            if (v.evidence != null) CaseService.addEvidence(player, v.evidence);
            if (first || !v.once) Logic.run(player, v.actions, ctx);
            QuestEngine.event(player, "inspect", v.id);
            ((ServerWorld) player.getWorld()).spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.2);
            return;
        }
        switch (d.type) {
            case "inspect", "read" -> {
                boolean first = p.flags.add(doneKey);
                state.markDirty();
                if (!first && d.once) {
                    say(player, d.repeat_text != null ? d.repeat_text : d.text, Formatting.GRAY);
                    if (d.evidence != null) player.sendMessage(Text.translatable("message.hollowatlas.already_noted").formatted(Formatting.DARK_GRAY), true);
                    return;
                }
                say(player, d.text, Formatting.WHITE);
                if (d.evidence != null) CaseService.addEvidence(player, d.evidence);
                if (first || !d.once) Logic.run(player, d.actions, ctx);
                QuestEngine.event(player, "inspect", d.id);
                ((ServerWorld) player.getWorld()).spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.2);
            }
            case "cache" -> {
                if (!p.flags.add(doneKey)) { say(player, d.repeat_text != null ? d.repeat_text : "Empty now.", Formatting.GRAY); return; }
                state.markDirty();
                say(player, d.text, Formatting.WHITE);
                giveLoot(player, d);
                Logic.run(player, d.actions, ctx);
                QuestEngine.event(player, "inspect", d.id);
                player.playSoundToPlayer(SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.8f, 1f);
            }
            case "gather" -> {
                long now = player.getWorld().getTime();
                Long ready = state.nodeCooldowns.get(d.id);
                if (ready != null && ready > now) {
                    long mins = Math.max(1, (ready - now) / 1200);
                    say(player, "Picked clean. It'll be ready again in about " + mins + " minute" + (mins == 1 ? "" : "s") + ".", Formatting.GRAY);
                    return;
                }
                state.nodeCooldowns.put(d.id, now + d.regrow_minutes * 1200L);
                state.markDirty();
                say(player, d.text, Formatting.WHITE);
                giveLoot(player, d);
                if (d.depleted_state != null) setState(player.getServer(), pos, d.depleted_state);
                player.swingHand(Hand.MAIN_HAND, true);
                player.playSoundToPlayer(SoundEvents.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 0.8f, 1f);
                Logic.run(player, d.actions, ctx);
                QuestEngine.event(player, "gather", d.id);
            }
            case "puzzle" -> puzzle(player, d, pos);
            case "station" -> {
                say(player, d.text, Formatting.GRAY);
                CraftingService.open(player, d.station);
            }
            case "rest" -> rest(player, d);
            default -> HollowAtlas.LOGGER.warn("Unknown interaction type {}", d.type);
        }
    }

    private static void giveLoot(ServerPlayerEntity player, Def d) {
        for (Loot l : d.loot) {
            if (player.getRandom().nextDouble() > l.chance) continue;
            int n = l.min + (l.max > l.min ? player.getRandom().nextInt(l.max - l.min + 1) : 0);
            RewardService.give(player, l.item, n);
        }
    }

    private static void puzzle(ServerPlayerEntity player, Def d, BlockPos pos) {
        Puzzle pz = PUZZLES.get(d.puzzle);
        if (pz == null) return;
        GameState state = GameState.get(player.getServer());
        if (pz.solved_flag != null && state.flag(pz.solved_flag)) {
            say(player, pz.solved_text != null ? pz.solved_text : "The mechanism is already open.", Formatting.GRAY);
            return;
        }
        List<String> input = PUZZLE_INPUT.computeIfAbsent(player.getUuid(), u -> new ArrayList<>());
        input.add(d.symbol);
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockState st = world.getBlockState(pos);
        if (st.contains(Properties.POWERED)) world.setBlockState(pos, st.with(Properties.POWERED, true), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.6f, 1.0f);
        if (d.text != null) say(player, d.text, Formatting.GRAY);
        int i = input.size() - 1;
        if (i >= pz.sequence.size() || !pz.sequence.get(i).equals(d.symbol)) {
            input.clear();
            resetLevers(world, pz);
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.8f, 0.6f);
            say(player, pz.reset_text != null ? pz.reset_text : "Something clunks back into place.", Formatting.GRAY);
            return;
        }
        if (input.size() == pz.sequence.size()) {
            input.clear();
            if (pz.solved_flag != null) state.setFlag(pz.solved_flag, true);
            WorldChanges.apply(player.getServer());
            world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1f, 0.8f);
            say(player, pz.solved_text != null ? pz.solved_text : "Something opens with a heavy click.", Formatting.AQUA);
            Logic.run(player, pz.on_solve, Logic.Ctx.NONE);
            QuestEngine.event(player, "solve", pz.id);
        }
    }

    private static void resetLevers(ServerWorld world, Puzzle pz) {
        for (Map.Entry<BlockPos, Def> e : BY_POS.entrySet()) {
            if (pz.id.equals(e.getValue().puzzle)) {
                BlockState st = world.getBlockState(e.getKey());
                if (st.contains(Properties.POWERED)) world.setBlockState(e.getKey(), st.with(Properties.POWERED, false), Block.NOTIFY_ALL);
            }
        }
    }

    private static void rest(ServerPlayerEntity player, Def d) {
        if (!Logic.test(player, d.when, Logic.Ctx.NONE)) { say(player, d.locked_text, Formatting.GRAY); return; }
        ServerWorld world = (ServerWorld) player.getWorld();
        long t = world.getTimeOfDay() % 24000L;
        if (t > 12000 && player.getServer().getPlayerManager().getCurrentPlayerCount() == 1) {
            world.setTimeOfDay(world.getTimeOfDay() + (24000 - t) + 0);   // to 06:00
            say(player, d.text != null ? d.text : "You sleep until morning.", Formatting.WHITE);
        } else {
            say(player, "You rest a while.", Formatting.WHITE);
        }
        player.setHealth(player.getMaxHealth());
        Logic.run(player, d.actions, Logic.Ctx.NONE);
        QuestEngine.event(player, "inspect", d.id);
    }

    /** Restore gathering nodes whose regrow timer has elapsed. */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 100 != 11) return;
        reindex();
        GameState state = GameState.get(server);
        long now = server.getOverworld().getTime();
        for (Map.Entry<BlockPos, Def> e : BY_POS.entrySet()) {
            Def d = e.getValue();
            if (!"gather".equals(d.type) || d.ripe_state == null) continue;
            Long ready = state.nodeCooldowns.get(d.id);
            if (!server.getOverworld().isChunkLoaded(e.getKey())) continue;
            if (ready != null && ready <= now) {
                setState(server, e.getKey(), d.ripe_state);
                state.nodeCooldowns.remove(d.id);
                state.markDirty();
            } else if (ready == null && server.getOverworld().getBlockState(e.getKey()).isAir()) {
                setState(server, e.getKey(), d.ripe_state);   // first-time placement of a designated node
            }
        }
    }

    private static void setState(MinecraftServer server, BlockPos pos, String spec) {
        try {
            BlockState s = BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), spec, false).blockState();
            server.getOverworld().setBlockState(pos, s, Block.NOTIFY_ALL);
        } catch (CommandSyntaxException e) {
            HollowAtlas.LOGGER.error("Bad interaction block state {}", spec);
        }
    }
}
