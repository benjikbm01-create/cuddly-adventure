package dev.hollowatlas.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.state.GameState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.TreeConfiguredFeatures;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a {@link RegionModel} into a (superflat) world in time-budgeted steps on the server thread.
 * Re-running is idempotent for terrain/buildings: every block is computed from the model.
 */
public final class RegionBuilder {
    public static final String BUILT_FLAG = "hollowatlas:region_built";
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    private static final long BUDGET_NANOS = 45_000_000L;

    private static RegionBuilder active;

    private final ServerWorld world;
    private final RegionModel model;
    private final int ox, oz;
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final Map<String, BlockState> stateCache = new HashMap<>();
    private final int totalTasks;
    private int done;
    private long started;
    private final Runnable onComplete;

    public static final String CONNECT_FIX_FLAG = "hollowatlas:fix_connections_v1";

    private RegionBuilder(ServerWorld world, RegionModel model, Runnable onComplete) {
        this(world, model, onComplete, false);
    }

    private RegionBuilder(ServerWorld world, RegionModel model, Runnable onComplete, boolean connectOnly) {
        this.world = world;
        this.model = model;
        this.ox = model.layout.origin[0];
        this.oz = model.layout.origin[1];
        this.onComplete = onComplete;
        if (connectOnly) planConnectPass(); else plan();
        totalTasks = tasks.size();
    }

    /** Repairs already-generated worlds: connects panes/fences/walls/stairs that were placed without neighbour updates. */
    public static boolean startConnectFix(ServerWorld world, RegionModel model) {
        if (active != null) return false;
        active = new RegionBuilder(world, model, () -> {
            dev.hollowatlas.state.GameState.get(world.getServer()).setFlag(CONNECT_FIX_FLAG, true);
            HollowAtlas.LOGGER.info("Connected panes, fences, walls and stairs in the existing world.");
        }, true);
        active.started = System.currentTimeMillis();
        HollowAtlas.LOGGER.info("World repair started: connecting panes/fences/walls ({} steps)", active.totalTasks);
        return true;
    }

    private void planConnectPass() {
        int h = model.half;
        int c0x = Math.floorDiv(ox - h, 16), c1x = Math.floorDiv(ox + h, 16);
        int c0z = Math.floorDiv(oz - h, 16), c1z = Math.floorDiv(oz + h, 16);
        for (int cz = c0z; cz <= c1z; cz++) for (int cx = c0x; cx <= c1x; cx++) {
            final int fcx = cx, fcz = cz;
            tasks.add(() -> connectChunk(fcx, fcz));
        }
    }

    private static boolean connectable(BlockState st) {
        net.minecraft.block.Block b = st.getBlock();
        return b instanceof net.minecraft.block.PaneBlock || b instanceof net.minecraft.block.FenceBlock
                || b instanceof net.minecraft.block.WallBlock || b instanceof net.minecraft.block.StairsBlock
                || b instanceof net.minecraft.block.FenceGateBlock;
    }

    /** Recompute shape/connection properties for connectable blocks in one chunk column of the region. */
    private void connectChunk(int cx, int cz) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int wz = cz * 16; wz < cz * 16 + 16; wz++) for (int wx = cx * 16; wx < cx * 16 + 16; wx++) {
            int lx = wx - ox, lz = wz - oz;
            if (!model.inside(lx, lz)) continue;
            int base = model.height[model.idx(lx, lz)];
            for (int y = Math.max(world.getBottomY(), base - 6); y <= base + 26; y++) {
                m.set(wx, y, wz);
                BlockState st = world.getBlockState(m);
                if (!connectable(st)) continue;
                BlockState fixed = Block.postProcessState(st, world, m);
                if (fixed != st) world.setBlockState(m, fixed, FLAGS);
            }
        }
    }

    public static boolean isRunning() { return active != null; }

    public static int percent() {
        RegionBuilder a = active;
        return a == null || a.totalTasks == 0 ? 100 : (int) (100L * a.done / a.totalTasks);
    }

    public static String status() {
        if (active == null) return "idle";
        return String.format("building: %d/%d steps", active.done, active.totalTasks);
    }

    public static boolean start(ServerWorld world, RegionModel model, Runnable onComplete) {
        if (active != null) return false;
        active = new RegionBuilder(world, model, onComplete);
        active.started = System.currentTimeMillis();
        HollowAtlas.LOGGER.info("Region build started: {} ({} steps)", LocationIndex.logLabel(model), active.totalTasks);
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (active == null) return;
        long t0 = System.nanoTime();
        while (!active.tasks.isEmpty() && System.nanoTime() - t0 < BUDGET_NANOS) {
            Runnable r = active.tasks.poll();
            try {
                r.run();
            } catch (Exception e) {
                HollowAtlas.LOGGER.error("Region build step failed", e);
            }
            active.done++;
            if (active.done % 200 == 0) HollowAtlas.LOGGER.info("Region build {}", status());
        }
        if (active.tasks.isEmpty()) {
            RegionBuilder finished = active;
            active = null;
            HollowAtlas.LOGGER.info("REGION BUILD COMPLETE in {}s", (System.currentTimeMillis() - finished.started) / 1000);
            if (finished.onComplete != null) finished.onComplete.run();
        }
    }

    // ------------------------------------------------------------------ plan

    private void plan() {
        int h = model.half;
        int c0x = Math.floorDiv(ox - h, 16), c1x = Math.floorDiv(ox + h, 16);
        int c0z = Math.floorDiv(oz - h, 16), c1z = Math.floorDiv(oz + h, 16);
        for (int cz = c0z; cz <= c1z; cz++) for (int cx = c0x; cx <= c1x; cx++) {
            final int fcx = cx, fcz = cz;
            tasks.add(() -> terrainChunk(fcx, fcz));
        }
        tasks.add(this::bridges);
        tasks.add(this::falls);
        tasks.add(this::caves);
        tasks.add(this::fields);
        for (RegionModel.PlacedBuilding b : model.buildings) tasks.add(() -> building(b));
        for (RegionModel.PlacedFeature f : model.features) tasks.add(() -> feature(f));
        tasks.add(this::lampPosts);
        tasks.add(this::orchard);
        tasks.add(this::pasture);
        // decoration, one chunk per step: each step only ever touches (and reloads) a single chunk
        for (int cz = c0z; cz <= c1z; cz++) for (int cx = c0x; cx <= c1x; cx++) {
            final int lx0 = cx * 16 - ox, lz0 = cz * 16 - oz, wz0 = cz * 16;
            tasks.add(() -> {
                roadSteps(lx0, lx0 + 15, lz0, lz0 + 15);
                pondReeds(lx0, lx0 + 15, lz0, lz0 + 15);
                trees(wz0, wz0 + 15, lx0, lx0 + 15);
                flora(wz0, wz0 + 15, lx0, lx0 + 15);
            });
        }
        planConnectPass();
        tasks.add(this::animals);
        tasks.add(this::finish);
    }

    // --------------------------------------------------------------- helpers

    private BlockState state(String s) {
        return stateCache.computeIfAbsent(s, k -> {
            try {
                return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), k, false).blockState();
            } catch (CommandSyntaxException e) {
                HollowAtlas.LOGGER.error("Bad block state in region data: {}", k);
                return Blocks.AIR.getDefaultState();
            }
        });
    }

    private BlockPos wpos(int lx, int y, int lz) { return new BlockPos(ox + lx, y, oz + lz); }

    private void set(int lx, int y, int lz, BlockState s) { world.setBlockState(wpos(lx, y, lz), s, FLAGS); }

    private static double rnd(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= (h >>> 29); h *= 0xBF58476D1CE4E5B9L; h ^= (h >>> 32);
        return ((h >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    private double woodsDensity(int x, int z) {
        double best = 0;
        for (RegionLayout.Woods w : model.layout.woods) {
            double d = Math.hypot(x - w.x, z - w.z);
            if (d < w.r) {
                double t = 1 - d / w.r;
                best = Math.max(best, w.density * Math.min(1, t * 1.8) * (0.7 + 0.3 * model.noise(x / 19.0, z / 19.0)));
            }
        }
        return best;
    }

    // --------------------------------------------------------------- terrain

    private void terrainChunk(int cx, int cz) {
        BlockState stone = Blocks.STONE.getDefaultState(), dirt = Blocks.DIRT.getDefaultState(),
                air = Blocks.AIR.getDefaultState(), waterState = Blocks.WATER.getDefaultState(), lava = Blocks.LAVA.getDefaultState();
        for (int wz = cz * 16; wz < cz * 16 + 16; wz++) for (int wx = cx * 16; wx < cx * 16 + 16; wx++) {
            int lx = wx - ox, lz = wz - oz;
            if (!model.inside(lx, lz)) continue;
            int i = model.idx(lx, lz);
            int H = model.height[i];
            int W = model.water[i];
            BlockState water = model.fluid[i] == RegionModel.FLUID_LAVA ? lava : waterState;
            byte surf = model.surface[i];
            String pal = model.paletteAt(lx, lz);
            BlockState top = surfaceBlock(lx, lz, surf, H, W);
            BlockState sub = subBlock(surf, pal);
            int yTop = Math.max(Math.max(64, H), W == Integer.MIN_VALUE ? 0 : W) + 1;
            int yLow = Math.min(H - 4, 60);
            BlockPos.Mutable m = new BlockPos.Mutable();
            boolean wall = surf == RegionModel.SURF_WALL;
            boolean bands = model.layout.rock_bands;
            for (int y = yLow; y <= yTop; y++) {
                BlockState want;
                if (wall && y < H && y > H - 14) want = wallBlock(lx, y, lz);
                else if (y <= H - 4) want = bands || !pal.isEmpty() ? band(lx, y, lz) : stone;
                else if (y < H) want = y >= H - 1 ? (bands && sub == stone ? band(lx, y, lz) : sub) : (sub == stone ? (bands ? band(lx, y, lz) : stone) : subDeep(pal, dirt));
                else if (y == H) want = top;
                else if (W != Integer.MIN_VALUE && y <= W) want = water;
                else want = air;
                m.set(wx, y, wz);
                if (world.getBlockState(m) != want) world.setBlockState(m, want, FLAGS);
            }
        }
    }

    /** Coloured rock strata, gently warped so the layers aren't perfectly flat. */
    private static final BlockState[] BADLANDS = {Blocks.TERRACOTTA.getDefaultState(), Blocks.ORANGE_TERRACOTTA.getDefaultState(),
            Blocks.TERRACOTTA.getDefaultState(), Blocks.YELLOW_TERRACOTTA.getDefaultState(), Blocks.RED_TERRACOTTA.getDefaultState(),
            Blocks.RED_TERRACOTTA.getDefaultState(), Blocks.WHITE_TERRACOTTA.getDefaultState(), Blocks.BROWN_TERRACOTTA.getDefaultState(),
            Blocks.TERRACOTTA.getDefaultState(), Blocks.LIGHT_GRAY_TERRACOTTA.getDefaultState(), Blocks.ORANGE_TERRACOTTA.getDefaultState(),
            Blocks.RED_SANDSTONE.getDefaultState()};
    private static final BlockState[] VOLCANIC = {Blocks.BLACKSTONE.getDefaultState(), Blocks.BASALT.getDefaultState(),
            Blocks.SMOOTH_BASALT.getDefaultState(), Blocks.BLACKSTONE.getDefaultState(), Blocks.TUFF.getDefaultState(),
            Blocks.DEEPSLATE.getDefaultState(), Blocks.BASALT.getDefaultState(), Blocks.MAGMA_BLOCK.getDefaultState(), Blocks.BLACKSTONE.getDefaultState()};

    private BlockState band(int x, int y, int z) {
        double warp = model.noise(x / 37.0 + 11, z / 37.0 - 7) * 3.5 + model.noise(x / 9.0, z / 9.0) * 0.8;
        String pal = model.paletteAt(x, z);
        if (pal.equals("badlands")) return BADLANDS[Math.floorMod((int) Math.floor((y + warp * 0.5) / 2.0), BADLANDS.length)];
        if (pal.equals("volcanic")) {
            BlockState b = VOLCANIC[Math.floorMod((int) Math.floor((y + warp) / 3.0), VOLCANIC.length)];
            return b.isOf(Blocks.MAGMA_BLOCK) && rnd(x * 7 + y, z, 55) > 0.35 ? Blocks.BLACKSTONE.getDefaultState() : b;
        }
        int layer = Math.floorMod((int) Math.floor((y + warp) / 3.0), 11);
        return switch (layer) {
            case 1, 6 -> Blocks.ANDESITE.getDefaultState();
            case 3 -> Blocks.TUFF.getDefaultState();
            case 4 -> Blocks.CALCITE.getDefaultState();
            case 8 -> Blocks.DEEPSLATE.getDefaultState();
            case 9 -> Blocks.GRANITE.getDefaultState();
            default -> Blocks.STONE.getDefaultState();
        };
    }

    private BlockState wallBlock(int x, int y, int z) {
        double r = rnd(x * 31 + y, z, 77);
        return r < 0.62 ? Blocks.STONE_BRICKS.getDefaultState() : r < 0.8 ? Blocks.MOSSY_STONE_BRICKS.getDefaultState()
                : r < 0.92 ? Blocks.CRACKED_STONE_BRICKS.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
    }

    private BlockState surfaceBlock(int x, int z, byte surf, int H, int W) {
        double r = rnd(x, z, 7);
        boolean snowy = model.layout.snow_line > 0 && H >= model.layout.snow_line;
        return switch (surf) {
            case RegionModel.SURF_WALL -> wallBlock(x, H, z);
            case RegionModel.SURF_ROAD -> r < 0.05 ? Blocks.COARSE_DIRT.getDefaultState() : r < 0.07 ? Blocks.GRAVEL.getDefaultState() : Blocks.DIRT_PATH.getDefaultState();
            case RegionModel.SURF_LANE -> r < 0.08 ? Blocks.COARSE_DIRT.getDefaultState() : r < 0.20 ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.DIRT_PATH.getDefaultState();
            case RegionModel.SURF_COBBLE -> r < 0.45 ? Blocks.STONE_BRICKS.getDefaultState() : r < 0.65 ? Blocks.COBBLESTONE.getDefaultState()
                    : r < 0.8 ? Blocks.ANDESITE.getDefaultState() : r < 0.9 ? Blocks.MOSSY_STONE_BRICKS.getDefaultState() : Blocks.CRACKED_STONE_BRICKS.getDefaultState();
            case RegionModel.SURF_BANK -> model.paletteAt(x, z).equals("volcanic") ? (r < 0.5 ? Blocks.BASALT.getDefaultState() : Blocks.MAGMA_BLOCK.getDefaultState())
                    : model.paletteAt(x, z).equals("badlands") ? Blocks.RED_SAND.getDefaultState()
                    : r < 0.5 ? Blocks.SAND.getDefaultState() : r < 0.78 ? Blocks.GRAVEL.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState();
            case RegionModel.SURF_BED -> model.paletteAt(x, z).equals("volcanic") ? (r < 0.6 ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.BASALT.getDefaultState())
                    : r < 0.45 ? Blocks.GRAVEL.getDefaultState() : r < 0.78 ? Blocks.SAND.getDefaultState() : Blocks.CLAY.getDefaultState();
            case RegionModel.SURF_ROCK -> snowy && r < 0.55 ? Blocks.SNOW_BLOCK.getDefaultState()
                    : model.layout.rock_bands && r < 0.7 ? band(x, H, z)
                    : r < 0.55 ? Blocks.STONE.getDefaultState() : r < 0.8 ? Blocks.ANDESITE.getDefaultState()
                    : r < 0.92 ? Blocks.COBBLESTONE.getDefaultState() : Blocks.MOSSY_COBBLESTONE.getDefaultState();
            default -> {
                if (model.layout.snow_line > 0 && H >= model.layout.snow_line) yield Blocks.SNOW_BLOCK.getDefaultState();
                String pal = model.paletteAt(x, z);
                if (pal.equals("badlands")) yield r < 0.7 ? Blocks.RED_SAND.getDefaultState() : r < 0.85 ? Blocks.COARSE_DIRT.getDefaultState()
                        : r < 0.93 ? Blocks.TERRACOTTA.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
                if (pal.equals("volcanic")) yield r < 0.35 ? Blocks.BLACKSTONE.getDefaultState() : r < 0.6 ? Blocks.BASALT.getDefaultState()
                        : r < 0.8 ? Blocks.COARSE_DIRT.getDefaultState() : r < 0.83 ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.TUFF.getDefaultState();
                if (pal.equals("taiga") && r < 0.35) yield Blocks.PODZOL.getDefaultState();
                double wd = woodsDensity(x, z);
                if (wd > 0.25 && r < 0.10) yield Blocks.PODZOL.getDefaultState();
                if (wd > 0.25 && r < 0.14) yield Blocks.COARSE_DIRT.getDefaultState();
                if (wd > 0.4 && r < 0.17) yield Blocks.MOSS_BLOCK.getDefaultState();
                yield Blocks.GRASS_BLOCK.getDefaultState();
            }
        };
    }

    private BlockState subDeep(String pal, BlockState dirt) {
        return pal.equals("badlands") ? Blocks.TERRACOTTA.getDefaultState() : pal.equals("volcanic") ? Blocks.BLACKSTONE.getDefaultState() : dirt;
    }

    private BlockState subBlock(byte surf, String pal) {
        if (surf == RegionModel.SURF_GRASS || surf == RegionModel.SURF_FIELD) {
            if (pal.equals("badlands")) return Blocks.RED_SAND.getDefaultState();
            if (pal.equals("volcanic")) return Blocks.BLACKSTONE.getDefaultState();
        }
        return switch (surf) {
            case RegionModel.SURF_BANK, RegionModel.SURF_BED -> Blocks.SAND.getDefaultState();
            case RegionModel.SURF_ROCK, RegionModel.SURF_COBBLE -> Blocks.STONE.getDefaultState();
            default -> Blocks.DIRT.getDefaultState();
        };
    }

    // --------------------------------------------------------------- bridges

    private boolean nearBrook(int x, int z) {
        for (RegionLayout.River r : model.layout.rivers) {
            if (r.width <= 4 && RegionModel.distToPolyline(r.points, x, z) < 4) return true;
        }
        return false;
    }

    private void bridges() {
        int h = model.half;
        for (int z = -h; z <= h; z++) for (int x = -h; x <= h; x++) {
            int i = model.idx(x, z);
            int b = model.bridge[i];
            if (b == 0) continue;
            int deck = Math.abs(b);
            byte style = model.bridgeStyle[i];
            if (style == RegionModel.BRIDGE_ROPE) { ropeBridgeCell(x, z, deck); continue; }
            boolean wood = style == RegionModel.BRIDGE_WOOD || (style == RegionModel.BRIDGE_AUTO && (nearBrook(x, z) || b < 0));
            boolean broken = b < 0;
            if (broken && x >= 345 && x <= 360) {
                // the collapsed span: rubble in the water
                double r = rnd(x, z, 31);
                int bed = model.height[i];
                if (r < 0.35) set(x, bed + 1, z, r < 0.2 ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
                if (r < 0.08) set(x, bed + 2, z, Blocks.SPRUCE_PLANKS.getDefaultState());
                continue;
            }
            set(x, deck, z, wood ? Blocks.SPRUCE_PLANKS.getDefaultState() : (rnd(x, z, 3) < 0.2 ? Blocks.CRACKED_STONE_BRICKS.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState()));
            for (int y = deck + 1; y <= deck + 3; y++) set(x, y, z, Blocks.AIR.getDefaultState());
            boolean edge = false;
            int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : nb) {
                int xx = x + d[0], zz = z + d[1];
                if (!model.inside(xx, zz)) continue;
                int j = model.idx(xx, zz);
                if (model.bridge[j] == 0 && model.water[j] != Integer.MIN_VALUE && !model.roadMask[j]) edge = true;
            }
            if (edge) {
                set(x, deck + 1, z, wood ? Blocks.SPRUCE_FENCE.getDefaultState() : Blocks.STONE_BRICK_WALL.getDefaultState());
                if ((x + z) % 4 == 0) {
                    for (int y = model.height[i] + 1; y < deck; y++) set(x, y, z, wood ? Blocks.SPRUCE_LOG.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState());
                    if (!wood) set(x, deck + 2, z, Blocks.LANTERN.getDefaultState());
                }
            }
            if (broken && x == 344) {
                // the surviving west stub: a pillar and an escape ladder down to the water
                for (int y = model.height[i] + 1; y < deck; y++) {
                    set(x, y, z, Blocks.STONE_BRICKS.getDefaultState());
                    set(x + 1, y, z, state("minecraft:ladder[facing=east]"));
                }
                set(x + 1, deck, z, state("minecraft:ladder[facing=east]"));
            }
        }
    }

    /** Plank deck, rope-and-post rails, lanterns on the posts; no piers (it spans a chasm). */
    private void ropeBridgeCell(int x, int z, int deck) {
        set(x, deck, z, rnd(x, z, 41) < 0.12 ? Blocks.STRIPPED_SPRUCE_WOOD.getDefaultState() : Blocks.SPRUCE_PLANKS.getDefaultState());
        for (int y = deck + 1; y <= deck + 3; y++) set(x, y, z, Blocks.AIR.getDefaultState());
        boolean edge = false;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int xx = x + d[0], zz = z + d[1];
            if (!model.inside(xx, zz)) continue;
            int j = model.idx(xx, zz);
            if (model.bridge[j] == 0 && !model.roadMask[j]) edge = true;
        }
        if (!edge) return;
        set(x, deck + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
        if (Math.floorMod(x + z, 6) == 0) {
            set(x, deck + 2, z, Blocks.SPRUCE_FENCE.getDefaultState());
            set(x, deck + 3, z, Blocks.LANTERN.getDefaultState());
        }
        // a few hanging ropes under the deck
        if (Math.floorMod(x * 3 + z, 5) == 0) for (int y = deck - 1; y >= deck - 3; y--) set(x, y, z, Blocks.CHAIN.getDefaultState());
    }

    // ------------------------------------------------------------- road steps

    /** Half-slab steps where a road rises by one block, so climbing roads need no jumping. */
    private void roadSteps(int x0, int x1, int z0, int z1) {
        if (!model.layout.road_steps) return;
        int h = model.half;
        for (int z = Math.max(-h + 1, z0); z <= Math.min(h - 1, z1); z++) for (int x = Math.max(-h + 1, x0); x <= Math.min(h - 1, x1); x++) {
            int i = model.idx(x, z);
            if (!model.roadMask[i] || model.bridge[i] != 0 || model.water[i] != Integer.MIN_VALUE || model.surface[i] == RegionModel.SURF_PAD) continue;
            int H = model.height[i];
            boolean up = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int j = model.idx(x + d[0], z + d[1]);
                if ((model.roadMask[j] || model.surface[j] == RegionModel.SURF_PAD) && model.bridge[j] == 0 && model.height[j] == H + 1) up = true;
            }
            // railings where a raised road stands well above the ground beside it
            boolean drop = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int j = model.idx(x + d[0], z + d[1]);
                if (!model.roadMask[j] && model.surface[j] != RegionModel.SURF_PAD && model.bridge[j] == 0 && model.height[j] <= H - 3) drop = true;
            }
            if (drop && !up) {
                BlockPos rail = wpos(x, H + 1, z);
                if (world.getBlockState(rail).isAir())
                    world.setBlockState(rail, model.surface[i] == RegionModel.SURF_COBBLE ? Blocks.STONE_BRICK_WALL.getDefaultState() : Blocks.SPRUCE_FENCE.getDefaultState(), FLAGS);
                continue;
            }
            if (!up) continue;
            BlockPos above = wpos(x, H + 1, z);
            if (!world.getBlockState(above).isAir()) continue;
            String slab = model.surface[i] == RegionModel.SURF_COBBLE ? "minecraft:stone_brick_slab[type=bottom]" : "minecraft:spruce_slab[type=bottom]";
            world.setBlockState(above, state(slab), FLAGS);
        }
    }

    // ---------------------------------------------------------------- waterfalls

    private void falls() {
        for (RegionLayout.Fall f : model.layout.falls) {
            for (int k = 0; k < f.w; k++) {
                int x = f.axis.equals("x") ? f.x + k : f.x, z = f.axis.equals("x") ? f.z : f.z + k;
                if (!model.inside(x, z)) continue;
                int top = f.top;
                if (top == 0) for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) top = Math.max(top, model.heightAt(x + dx, z + dz));
                int ground = model.water[model.idx(x, z)] != Integer.MIN_VALUE ? model.water[model.idx(x, z)] : model.height[model.idx(x, z)];
                // the source sits level with the cliff top; the curtain below is already-falling water, so it is stable
                set(x, top, z, Blocks.WATER.getDefaultState());
                for (int y = top - 1; y > ground; y--) set(x, y, z, state("minecraft:water[level=8]"));
            }
        }
    }

    // ------------------------------------------------------------------ caves

    private void caves() {
        for (RegionLayout.Cave cave : model.layout.caves) {
            List<int[]> nodes = cave.nodes;
            for (int n = 0; n < nodes.size(); n++) {
                int[] a = nodes.get(n);
                carve(a[0], a[1], a[2], a[3], a.length > 4 && a[4] == 1);
                if (n + 1 < nodes.size()) {
                    int[] b = nodes.get(n + 1);
                    int steps = (int) Math.ceil(Math.hypot(b[0] - a[0], b[2] - a[2]));
                    for (int s = 1; s < steps; s++) {
                        double t = s / (double) steps;
                        carve((int) Math.round(a[0] + (b[0] - a[0]) * t), (int) Math.round(a[1] + (b[1] - a[1]) * t),
                                (int) Math.round(a[2] + (b[2] - a[2]) * t), (int) Math.round(Math.min(a[3], b[3])), false);
                    }
                }
                if (a[3] >= 4) set(a[0] + a[3] - 2, a[1], a[2], Blocks.LANTERN.getDefaultState());
            }
        }
    }

    private void carve(int cx, int fy, int cz, int r, boolean open) {
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            double hd = (dx * dx + dz * dz) / (double) (r * r);
            if (hd > 1) continue;
            int x = cx + dx, z = cz + dz;
            int ceiling = fy + (int) Math.round(Math.sqrt(Math.max(0, 1 - hd)) * (r + 1.5));
            if (open) ceiling = Math.max(ceiling, model.heightAt(x, z) + 2);
            for (int y = fy; y <= ceiling; y++) set(x, y, z, Blocks.AIR.getDefaultState());
            double rr = rnd(x, z, 11);
            set(x, fy - 1, z, rr < 0.45 ? Blocks.COARSE_DIRT.getDefaultState() : rr < 0.75 ? Blocks.GRAVEL.getDefaultState() : Blocks.STONE.getDefaultState());
        }
    }

    // ----------------------------------------------------------------- fields

    private void fields() {
        for (RegionLayout.Field f : model.layout.fields) {
            if (f.crop.equals("orchard")) continue;
            BlockState crop = switch (f.crop) {
                case "carrots" -> Blocks.CARROTS.getDefaultState();
                case "potatoes" -> Blocks.POTATOES.getDefaultState();
                case "beetroots" -> Blocks.BEETROOTS.getDefaultState();
                default -> Blocks.WHEAT.getDefaultState();
            };
            int z0 = Math.min(f.z1, f.z2), z1 = Math.max(f.z1, f.z2);
            int x0 = Math.min(f.x1, f.x2), x1 = Math.max(f.x1, f.x2);
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
                if (!model.inside(x, z)) continue;
                int i = model.idx(x, z);
                if (model.surface[i] != RegionModel.SURF_FIELD) continue;
                int H = model.height[i];
                if ((z - z0) % 6 == 3) {
                    set(x, H, z, Blocks.WATER.getDefaultState());
                    continue;
                }
                set(x, H, z, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
                int age = 3 + (int) (rnd(x, z, 5) * 5);
                if (crop.getBlock() instanceof CropBlock cb) {
                    set(x, H + 1, z, cb.withAge(Math.min(age, cb.getMaxAge())));
                }
            }
        }
    }

    private void orchard() {
        for (RegionLayout.Field f : model.layout.fields) {
            if (!f.crop.equals("orchard")) continue;
            int z0 = Math.min(f.z1, f.z2), z1 = Math.max(f.z1, f.z2);
            int x0 = Math.min(f.x1, f.x2), x1 = Math.max(f.x1, f.x2);
            for (int z = z0 + 2; z <= z1 - 2; z += 6) for (int x = x0 + 2; x <= x1 - 2; x += 6) {
                if (!model.isOpenGround(x, z) && model.surface[model.idx(x, z)] != RegionModel.SURF_FIELD) continue;
                if (model.roadMask[model.idx(x, z)]) continue;
                fruitTree(x, model.heightAt(x, z) + 1, z);
            }
        }
    }

    private void fruitTree(int x, int y, int z) {
        BlockState log = state("minecraft:oak_log[axis=y]");
        BlockState leaves = state("minecraft:oak_leaves[persistent=true,distance=1]");
        BlockState bloom = state("minecraft:flowering_azalea_leaves[persistent=true,distance=1]");
        for (int t = 0; t < 3; t++) set(x, y + t, z, log);
        for (int dy = 2; dy <= 4; dy++) for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            int rr = dx * dx + dz * dz + (dy - 3) * (dy - 3) * 2;
            if (rr > 6 || (dx == 0 && dz == 0 && dy < 3)) continue;
            set(x + dx, y + dy, z + dz, rnd(x + dx, z + dz, dy) < 0.3 ? bloom : leaves);
        }
    }

    // -------------------------------------------------------------- buildings

    private static BlockRotation rotation(int r) {
        return switch (r) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    private void building(RegionModel.PlacedBuilding b) {
        BlockRotation rot = rotation(b.rotation);
        // clear the volume first so terrain/plants never poke through floors
        int top = 5 * Math.max(1, b.def.floors) + Math.max(b.def.w, b.def.d) / 2 + 4;
        for (int cz = -1; cz <= b.def.d; cz++) for (int cx = -1; cx <= b.def.w; cx++) {
            int[] l = b.toLocal(cx, cz);
            for (int y = b.padY + 1; y <= b.padY + top; y++) set(l[0], y, l[1], Blocks.AIR.getDefaultState());
            // foundation fill down to solid ground
            for (int y = b.padY - 1; y >= b.padY - 4; y--) {
                if (world.getBlockState(wpos(l[0], y, l[1])).isAir()) set(l[0], y, l[1], state(b.def.foundation));
            }
        }
        // two passes: solid blocks first, then attachables (doors, beds, lanterns, ladders, signs)
        for (int pass = 0; pass < 2; pass++) {
            for (BuildingPlanner.Placed p : b.plan.blocks) {
                boolean attach = isAttachable(p.state());
                if ((pass == 0) == attach) continue;
                int[] l = b.toLocal(p.x(), p.z());
                placeState(wpos(l[0], b.padY + p.y(), l[1]), p.state(), rot);
            }
        }
    }

    private static boolean isAttachable(String s) {
        return s.contains("_door") || s.contains("_bed") || s.contains("lantern") || s.contains("ladder")
                || s.contains("sign") || s.contains("lever") || s.contains("carpet") || s.contains("potted")
                || s.contains("cake") || s.contains("trapdoor") || s.contains("brewing");
    }

    private void placeState(BlockPos pos, String spec, BlockRotation rot) {
        String[] parts = spec.split("\\|");
        BlockState st = state(parts[0]).rotate(rot);
        world.setBlockState(pos, st, FLAGS);
        if (parts.length > 1) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SignBlockEntity sign) {
                SignText text = new SignText();
                for (int line = 0; line < 4 && line + 1 < parts.length; line++) text = text.withMessage(line, Text.literal(parts[line + 1]));
                sign.setText(text, true);
                sign.setWaxed(true);
                sign.markDirty();
            }
        }
    }

    // --------------------------------------------------------------- features

    private void feature(RegionModel.PlacedFeature f) {
        for (int pass = 0; pass < 2; pass++) {
            for (BuildingPlanner.Placed p : f.plan.blocks) {
                boolean attach = isAttachable(p.state());
                if ((pass == 0) == attach) continue;
                int[] l = model.featurePos(f, p.x(), p.y(), p.z());
                placeState(wpos(l[0], l[1], l[2]), p.state(), BlockRotation.NONE);
            }
        }
        if (f.def.type.equals("lone_oak")) {
            int y = model.heightAt(f.def.x, f.def.z) + 1;
            growTree(TreeConfiguredFeatures.FANCY_OAK, f.def.x, y, f.def.z, 4242);
        }
    }

    private void lampPosts() {
        for (RegionLayout.Road road : model.layout.roads) {
            if (!road.surface.equals("path") || road.width < 4) continue;
            List<int[]> pts = road.points;
            double carry = 0;
            for (int s = 0; s + 1 < pts.size(); s++) {
                int[] a = pts.get(s), b = pts.get(s + 1);
                double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
                double nx = -(b[1] - a[1]) / len, nz = (b[0] - a[0]) / len;
                for (double t = 14 - carry; t < len; t += 14) {
                    double px = a[0] + (b[0] - a[0]) * t / len, pz = a[1] + (b[1] - a[1]) * t / len;
                    if (!inTown(px, pz)) continue;
                    int side = ((int) t / 14) % 2 == 0 ? 1 : -1;
                    int x = (int) Math.round(px + nx * (road.width / 2 + 1.5) * side);
                    int z = (int) Math.round(pz + nz * (road.width / 2 + 1.5) * side);
                    if (!model.inside(x, z)) continue;
                    int i = model.idx(x, z);
                    if (model.occupied[i] || model.roadMask[i] || model.water[i] != Integer.MIN_VALUE) continue;
                    int y = model.height[i] + 1;
                    set(x, y, z, Blocks.SPRUCE_FENCE.getDefaultState());
                    set(x, y + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
                    set(x, y + 2, z, Blocks.LANTERN.getDefaultState());
                    model.occupied[i] = true;
                }
                carry = (len + carry) % 14;
            }
        }
    }

    private boolean inTown(double x, double z) {
        int[] vc = model.layout.village_center;
        if (Math.hypot(x - vc[0], z - vc[1]) <= model.layout.village_radius) return true;
        for (RegionLayout.Flat f : model.layout.flats) if (f.town && RegionModel.flatDist(f, x, z) <= 2) return true;
        return false;
    }

    private void pondReeds(int x0, int x1, int z0, int z1) {
        for (RegionLayout.Pond p : model.layout.ponds) {
            int reach = p.r + 3 + (p.r > 30 ? (int) (p.r * 0.3) : 0);
            for (int z = Math.max(z0, p.z - reach); z <= Math.min(z1, p.z + reach); z++) for (int x = Math.max(x0, p.x - reach); x <= Math.min(x1, p.x + reach); x++) {
                if (!model.inside(x, z)) continue;
                int i = model.idx(x, z);
                double r = rnd(x, z, 77);
                if ("lava".equals(p.fluid)) continue;
                if (model.water[i] != Integer.MIN_VALUE) {
                    if (r < 0.12) set(x, model.water[i] + 1, z, Blocks.LILY_PAD.getDefaultState());
                    continue;
                }
                boolean shore = false;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (model.inside(x + d[0], z + d[1]) && model.water[model.idx(x + d[0], z + d[1])] != Integer.MIN_VALUE) shore = true;
                }
                if (shore && r < 0.45) {
                    int H = model.height[i];
                    set(x, H, z, Blocks.GRASS_BLOCK.getDefaultState());
                    int tall = 1 + (int) (r * 6);
                    for (int t = 1; t <= tall; t++) set(x, H + t, z, Blocks.SUGAR_CANE.getDefaultState());
                }
            }
        }
    }

    private void pasture() {
        if (!"westreach_brindlecross".equals(model.layout.id)) return;   // authored for the starting region only
        int x0 = 70, x1 = 86, z0 = -124, z1 = -104;
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (!edge || !model.inside(x, z)) continue;
            int i = model.idx(x, z);
            if (model.water[i] != Integer.MIN_VALUE) continue;
            int y = model.height[i] + 1;
            boolean gate = x == x0 && z == (z0 + z1) / 2;
            set(x, y, z, gate ? state("minecraft:oak_fence_gate[facing=east,open=false,in_wall=false,powered=false]") : Blocks.OAK_FENCE.getDefaultState());
            model.occupied[i] = true;
        }
    }

    // ------------------------------------------------------------------ trees

    private void growTree(RegistryKey<ConfiguredFeature<?, ?>> key, int x, int y, int z, long seed) {
        ConfiguredFeature<?, ?> cf = world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).get(key);
        if (cf == null) return;
        Random random = Random.create(model.layout.seed * 31 + seed);
        cf.generate(world, world.getChunkManager().getChunkGenerator(), random, wpos(x, y, z));
    }

    private RegistryKey<ConfiguredFeature<?, ?>> treeKey(String name) {
        return switch (name) {
            case "birch" -> TreeConfiguredFeatures.BIRCH;
            case "fancy_oak" -> TreeConfiguredFeatures.FANCY_OAK;
            case "dark_oak" -> TreeConfiguredFeatures.DARK_OAK;
            case "spruce" -> TreeConfiguredFeatures.SPRUCE;
            case "pine" -> TreeConfiguredFeatures.PINE;
            case "mega_spruce" -> TreeConfiguredFeatures.MEGA_SPRUCE;
            case "mega_pine" -> TreeConfiguredFeatures.MEGA_PINE;
            case "cherry" -> TreeConfiguredFeatures.CHERRY;
            case "acacia" -> TreeConfiguredFeatures.ACACIA;
            case "jungle" -> TreeConfiguredFeatures.JUNGLE_TREE_NO_VINE;
            case "mega_jungle" -> TreeConfiguredFeatures.MEGA_JUNGLE_TREE;
            case "azalea" -> TreeConfiguredFeatures.AZALEA_TREE;
            case "swamp_oak" -> TreeConfiguredFeatures.SWAMP_OAK;
            case "super_birch" -> TreeConfiguredFeatures.SUPER_BIRCH_BEES;
            default -> TreeConfiguredFeatures.OAK;
        };
    }

    private boolean clearForTree(int x, int z, int radius) {
        for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
            int xx = x + dx, zz = z + dz;
            if (!model.inside(xx, zz)) return false;
            int i = model.idx(xx, zz);
            if (model.occupied[i] || model.roadMask[i] || model.water[i] != Integer.MIN_VALUE || model.bridge[i] != 0) return false;
            byte s = model.surface[i];
            if (s == RegionModel.SURF_FIELD || s == RegionModel.SURF_COBBLE || s == RegionModel.SURF_BANK) return false;
        }
        return true;
    }

    private void trees(int wz0, int wz1, int lx0, int lx1) {
        int cell = 6;
        for (int wz = wz0 + Math.floorMod(-wz0, cell); wz <= wz1; wz += cell) {
            for (int lx = lx0 + Math.floorMod(-(lx0 + model.half), cell); lx <= lx1; lx += cell) {
                int lz0 = wz - oz;
                double r = rnd(lx, lz0, 101);
                int jx = lx + (int) (rnd(lx, lz0, 102) * cell);
                int jz = lz0 + (int) (rnd(lx, lz0, 103) * cell);
                if (!model.inside(jx, jz)) continue;
                double density = woodsDensity(jx, jz);
                int[] vc = model.layout.village_center;
                double villageDist = Math.hypot(jx - vc[0], jz - vc[1]);
                double meadow = villageDist < model.layout.village_radius ? 0.0 : 0.035;
                double p = Math.max(density, meadow);
                if (r >= p) continue;
                if (!clearForTree(jx, jz, density > 0.2 ? 2 : 3)) continue;
                int i = model.idx(jx, jz);
                if (model.surface[i] == RegionModel.SURF_ROCK) continue;
                String tpal = model.paletteAt(jx, jz);
                if (tpal.equals("volcanic") || (tpal.equals("badlands") && rnd(jx, jz, 106) > 0.08)) continue;
                if (model.layout.snow_line > 0 && model.height[i] > model.layout.snow_line + 8) continue;
                String kind = "oak";
                for (RegionLayout.Woods w : model.layout.woods) {
                    if (Math.hypot(jx - w.x, jz - w.z) < w.r && !w.mix.isEmpty()) {
                        kind = w.mix.get((int) (rnd(jx, jz, 104) * w.mix.size()));
                        break;
                    }
                }
                if (density < 0.05 && rnd(jx, jz, 105) < 0.4) kind = "fancy_oak";
                if (tpal.equals("cherry")) kind = "cherry";
                else if (tpal.equals("badlands")) kind = "acacia";
                else if (tpal.equals("taiga") && density < 0.05) kind = rnd(jx, jz, 107) < 0.5 ? "spruce" : "pine";
                int y = model.height[i] + 1;
                if (kind.equals("dark_oak") || kind.startsWith("mega_")) {
                    if (!clearForTree(jx + 1, jz + 1, 2)) kind = kind.equals("dark_oak") ? "oak" : "spruce";
                    else for (int dz = 0; dz <= 1; dz++) for (int dx = 0; dx <= 1; dx++)
                        if (dx + dz > 0) world.setBlockState(wpos(jx + dx, model.height[model.idx(jx + dx, jz + dz)], jz + dz), Blocks.PODZOL.getDefaultState(), FLAGS);
                }
                growTree(treeKey(kind), jx, y, jz, (long) jx * 7919 + jz);
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
                    if (model.inside(jx + dx, jz + dz)) model.occupied[model.idx(jx + dx, jz + dz)] = true;
            }
        }
    }

    // ------------------------------------------------------------------ flora

    private static final String[] MEADOW_FLOWERS = {"minecraft:dandelion", "minecraft:poppy", "minecraft:oxeye_daisy",
            "minecraft:cornflower", "minecraft:azure_bluet", "minecraft:allium"};

    private void flora(int wz0, int wz1, int lx0, int lx1) {
        BlockState air = Blocks.AIR.getDefaultState();
        for (int wz = wz0; wz <= wz1; wz++) {
            int lz = wz - oz;
            for (int lx = Math.max(-model.half, lx0); lx <= Math.min(model.half, lx1); lx++) {
                if (!model.inside(lx, lz)) continue;
                int i = model.idx(lx, lz);
                if (model.occupied[i] || model.roadMask[i] || model.water[i] != Integer.MIN_VALUE || model.bridge[i] != 0) continue;
                if (model.surface[i] != RegionModel.SURF_GRASS) continue;
                int H = model.height[i];
                BlockPos above = wpos(lx, H + 1, lz);
                if (world.getBlockState(above) != air) continue;
                double r = rnd(lx, lz, 201);
                String pal = model.paletteAt(lx, lz);
                if (pal.equals("badlands")) {
                    if (r < 0.025 && world.getBlockState(above.down()).isOf(Blocks.RED_SAND)) world.setBlockState(above, Blocks.DEAD_BUSH.getDefaultState(), FLAGS);
                    continue;
                }
                if (pal.equals("volcanic")) continue;
                if (!world.getBlockState(above.down()).isOf(Blocks.GRASS_BLOCK)) continue;
                if (pal.equals("cherry") && r < 0.22) {
                    world.setBlockState(above, state("minecraft:pink_petals[facing=" + (r < 0.1 ? "north" : "east") + ",flower_amount=" + (1 + (int) (r * 18) % 4) + "]"), FLAGS);
                    continue;
                }
                if (pal.equals("taiga") && r < 0.2) {
                    world.setBlockState(above, r < 0.12 ? Blocks.FERN.getDefaultState() : Blocks.SWEET_BERRY_BUSH.getDefaultState(), FLAGS);
                    continue;
                }
                double wd = woodsDensity(lx, lz);
                double village = Math.hypot(lx, lz) < 60 ? 0.4 : 1.0;
                boolean mountain = "mountain".equals(model.layout.surface_style);
                if (model.layout.snow_line > 0 && H >= model.layout.snow_line - 3) {
                    if (world.getBlockState(above.down()).isOf(Blocks.GRASS_BLOCK) && r < 0.6)
                        world.setBlockState(above, Blocks.SNOW.getDefaultState(), FLAGS);
                    continue;
                }
                if (mountain) village *= 0.6;
                double patch = model.noise(lx / 11.0 + 40, lz / 11.0 - 40);
                if (patch > 0.55 && r < 0.35 * village && wd < 0.2) {
                    int kind = (int) ((model.noise(lx / 40.0, lz / 40.0) + 1) * 3) % MEADOW_FLOWERS.length;
                    world.setBlockState(above, state(MEADOW_FLOWERS[Math.abs(kind)]), FLAGS);
                } else if (wd > 0.2 && r < 0.12) {
                    world.setBlockState(above, Blocks.FERN.getDefaultState(), FLAGS);
                } else if (wd > 0.3 && r < 0.135) {
                    world.setBlockState(above, rnd(lx, lz, 9) < 0.5 ? Blocks.BROWN_MUSHROOM.getDefaultState() : Blocks.LILY_OF_THE_VALLEY.getDefaultState(), FLAGS);
                } else if (r < 0.28 * village + wd * 0.2) {
                    world.setBlockState(above, Blocks.SHORT_GRASS.getDefaultState(), FLAGS);
                } else if (r < 0.30 * village + wd * 0.25 && world.getBlockState(above.up()).isAir()) {
                    world.setBlockState(above, state("minecraft:tall_grass[half=lower]"), FLAGS);
                    world.setBlockState(above.up(), state("minecraft:tall_grass[half=upper]"), FLAGS);
                }
            }
        }
    }

    // ---------------------------------------------------------------- animals

    private void spawnAnimals(EntityType<? extends MobEntity> type, String anchor, int count, int spread) {
        int[] a = model.anchor(anchor);
        if (a == null) return;
        for (int n = 0; n < count; n++) {
            int x = a[0] + (int) ((rnd(n, a[2], 300) - 0.5) * spread * 2);
            int z = a[2] + (int) ((rnd(a[0], n, 301) - 0.5) * spread * 2);
            if (!model.inside(x, z) || model.water[model.idx(x, z)] != Integer.MIN_VALUE) continue;
            MobEntity mob = type.create(world);
            if (mob == null) continue;
            BlockPos p = wpos(x, model.heightAt(x, z) + 1, z);
            mob.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, (float) (rnd(x, z, 302) * 360), 0);
            mob.initialize(world, world.getLocalDifficulty(p), SpawnReason.STRUCTURE, null);
            mob.setPersistent();
            world.spawnEntity(mob);
        }
    }

    private void animals() {
        spawnAnimals(EntityType.COW, "pasture", 3, 5);
        spawnAnimals(EntityType.SHEEP, "pasture", 4, 5);
        spawnAnimals(EntityType.CHICKEN, "farmhouse.door_out", 5, 4);
        spawnAnimals(EntityType.FOX, "fox_den", 2, 3);
        spawnAnimals(EntityType.RABBIT, "meadow_west", 3, 10);
        spawnAnimals(EntityType.HORSE, "relay_yard", 2, 3);
        spawnAnimals(EntityType.CAT, "inn.door_out", 1, 2);
    }

    // ----------------------------------------------------------------- finish

    /** World flag recording that a region's terrain has been generated. */
    public static String builtFlag(RegionModel m) {
        return LocationIndex.isFirstOfMode(m) ? BUILT_FLAG : BUILT_FLAG + ":" + m.layout.id;
    }

    private void finish() {
        MinecraftServer server = world.getServer();
        BoundaryService.applyWorldBorder(server);
        if (LocationIndex.isFirstOfMode(model)) {
            int[] arrival = model.anchor(model.layout.arrival_anchor);
            world.setSpawnPos(wpos(arrival[0], arrival[1], arrival[2]), -90f);
            GameState.get(server).setFlag(CONNECT_FIX_FLAG, true);
        }
        WorldRules.apply(server);
        GameState.get(server).setFlag(builtFlag(model), true);
        server.saveAll(false, true, true);
    }

    /** Build every region that hasn't been generated yet, one after another, then run {@code after}. */
    private static final java.util.Set<String> ATTEMPTED = new java.util.HashSet<>();

    public static void buildMissing(MinecraftServer server, Runnable after) {
        for (RegionModel m : LocationIndex.modelsFor(server)) {
            if (!GameState.get(server).flag(builtFlag(m))) {
                if (!ATTEMPTED.add(m.layout.id)) {
                    HollowAtlas.LOGGER.error("Region {} failed to build; not retrying this session.", LocationIndex.logLabel(m));
                    continue;
                }
                start(server.getOverworld(), m, () -> buildMissing(server, after));
                return;
            }
        }
        if (after != null) after.run();
    }
}
