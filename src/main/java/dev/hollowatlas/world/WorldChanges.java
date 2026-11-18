package dev.hollowatlas.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.state.GameState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persistent, save-safe world mutations driven by world flags (a repaired pump, a reopened stall...).
 * Each change declares the block states for "flag set" and optionally "flag unset". Application is
 * idempotent and re-run on every server start, so restored or copied worlds converge on the saved state.
 * Changes only ever touch their listed anchor blocks, never player possessions.
 */
public final class WorldChanges {
    public static final Map<String, Bundle> BUNDLES = ContentManager.register("world_changes", Bundle.class);

    public static final class Bundle implements ContentManager.Validatable {
        public String id;
        public List<Change> changes = new ArrayList<>();
        @Override public String id() { return id; }
        @Override public void validate(String file, List<String> problems) {
            for (Change c : changes) {
                for (BlockSet b : c.blocks) if (!LocationIndex.has(b.anchor)) problems.add(file + ": unknown anchor " + b.anchor);
                for (BlockSet b : c.otherwise) if (!LocationIndex.has(b.anchor)) problems.add(file + ": unknown anchor " + b.anchor);
            }
        }
    }

    public static final class Change {
        public String id;
        public String flag;
        public List<BlockSet> blocks = new ArrayList<>();
        public List<BlockSet> otherwise = new ArrayList<>();
    }

    public static final class BlockSet {
        public String anchor;
        public int dx, dy, dz;
        public String state;
    }

    private WorldChanges() {}

    /** Forces class init so the content kind is registered before the first data reload. */
    public static void init() { }

    public static void apply(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        GameState state = GameState.get(server);
        for (Bundle b : BUNDLES.values()) {
            if (!dev.hollowatlas.state.WorldMode.allowed(server, b)) continue;
            for (Change c : b.changes) {
                List<BlockSet> sets = state.flag(c.flag) ? c.blocks : c.otherwise;
                for (BlockSet s : sets) {
                    if (!LocationIndex.has(s.anchor)) continue;
                    BlockPos pos = LocationIndex.pos(s.anchor).add(s.dx, s.dy, s.dz);
                    BlockState want = parse(s.state);
                    if (want == null) continue;
                    world.getChunk(pos);
                    BlockState have = world.getBlockState(pos);
                    if (!have.equals(want)) world.setBlockState(pos, want, Block.NOTIFY_ALL);
                }
            }
        }
    }

    private static BlockState parse(String s) {
        try {
            return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), s, false).blockState();
        } catch (CommandSyntaxException e) {
            HollowAtlas.LOGGER.error("Bad world-change block state {}", s);
            return null;
        }
    }
}
