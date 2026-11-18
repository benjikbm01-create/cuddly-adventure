package dev.hollowatlas.world;

/**
 * Pure planner for small authored set-pieces. Coordinates are relative to the feature center.
 * Unless the feature has an absolute {@code y}, plan Y is GROUND-RELATIVE: y=0 is the first block above
 * the local terrain surface at that column, y=-1 replaces the surface block.
 * Sign text is carried in the state string as {@code |line1|line2|...} after the block state.
 */
public final class FeaturePlanner {
    private FeaturePlanner() {}

    public static BuildingPlanner.Plan plan(RegionLayout.Feature f) {
        BuildingPlanner.Plan p = new BuildingPlanner.Plan();
        switch (f.type) {
            case "overlook" -> overlook(p);
            case "shrine" -> shrine(p);
            case "wagon" -> wagon(p);
            case "lone_oak" -> loneOak(p);
            case "notice" -> notice(p);
            case "pump" -> pump(p);
            case "bridge_out" -> bridgeOut(p);
            case "milestone" -> milestone(p);
            case "camp" -> camp(p);
            case "cache" -> cache(p);
            case "jetty" -> jetty(p);
            case "lever_test" -> leverTest(p);
            case "arena" -> arena(p);
            case "rock_arch" -> rockArch(p, f.size > 0 ? f.size : 24);
            case "summit_cairn" -> summitCairn(p);
            case "sky_island" -> skyIsland(p, f.size > 0 ? f.size : 14, f.x * 31L + f.z);
            case "ruin" -> ruin(p, f.size > 0 ? f.size : 9, f.x * 17L + f.z);
            case "statue" -> statue(p);
            default -> { /* reeds and others are procedural in the builder */ }
        }
        return p;
    }

    private static void overlook(BuildingPlanner.Plan p) {
        p.set(0, 0, 0, "minecraft:mossy_cobblestone");
        p.set(0, 1, 0, "minecraft:cobblestone");
        p.set(0, 2, 0, "minecraft:mossy_cobblestone_wall");
        p.set(1, 0, 0, "minecraft:cobblestone");
        p.set(-1, 0, 0, "minecraft:mossy_cobblestone");
        p.set(-1, 1, 0, "minecraft:cobblestone_slab[type=bottom]");
        p.set(0, 0, -1, "minecraft:cobblestone");
        p.set(0, 0, 1, "minecraft:oak_sign[rotation=0]|Lark Hill|Brindlecross lies|southwest, past|the orchards.");
        p.set(-2, 0, 3, "minecraft:spruce_stairs[facing=north,half=bottom]");
        p.set(-1, 0, 3, "minecraft:spruce_stairs[facing=north,half=bottom]");
        p.set(-3, 0, 3, "minecraft:spruce_fence");
        p.set(-3, 1, 3, "minecraft:lantern[hanging=false]");
        p.anchor("view", -1, 0, 4);
        p.anchor("sign", 0, 0, 1);
    }

    private static void shrine(BuildingPlanner.Plan p) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            p.set(x, -1, z, (x * 7 + z * 3) % 4 == 0 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks");
        }
        for (int x = -3; x <= 3; x++) {
            p.set(x, 0, -3, "minecraft:mossy_stone_brick_wall");
        }
        // three pedestals; order is hinted by the sign, not by position
        String[][] peds = {{"-2", "minecraft:light_blue_glazed_terracotta", "river"},
                {"0", "minecraft:chiseled_stone_bricks", "stone"},
                {"2", "minecraft:glowstone", "sun"}};
        for (String[] ped : peds) {
            int x = Integer.parseInt(ped[0]);
            p.set(x, 0, -2, "minecraft:polished_andesite");
            p.set(x, 1, -2, "minecraft:polished_andesite");
            p.set(x, 2, -2, ped[1]);
            p.set(x, 1, -1, "minecraft:lever[face=wall,facing=south,powered=false]");
            p.anchor("lever_" + ped[2], x, 1, -1);
        }
        // hidden hatch over a small cache
        p.set(0, -1, 1, "minecraft:iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]");
        p.set(0, -2, 1, "minecraft:air");
        p.set(0, -3, 1, "minecraft:barrel[facing=up,open=false]");
        p.anchor("hatch", 0, -1, 1);
        p.anchor("cache", 0, -3, 1);
        p.set(0, 0, 3, "minecraft:oak_sign[rotation=0]|Greet the morning,|follow the water,|then rest|upon stone.");
        p.anchor("plinth", 0, 0, 2);
    }

    private static void wagon(BuildingPlanner.Plan p) {
        for (int x = -2; x <= 1; x++) {
            p.set(x, 0, 0, x == -2 ? "minecraft:spruce_slab[type=bottom]" : "minecraft:spruce_planks");
            p.set(x, 0, 1, x == 1 ? "minecraft:spruce_slab[type=bottom]" : "minecraft:spruce_slab[type=top]");
        }
        p.set(-1, 1, 0, "minecraft:spruce_fence");
        p.set(1, 1, 1, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
        p.set(2, 0, -1, "minecraft:dark_oak_trapdoor[facing=west,half=bottom,open=true]");
        p.set(-3, 0, 2, "minecraft:barrel[facing=east,open=false]");
        p.set(0, 0, 3, "minecraft:barrel[facing=up,open=false]");
        p.set(3, 0, 2, "minecraft:hay_block[axis=x]");
        p.set(-1, 0, 2, "minecraft:brown_carpet");
        p.anchor("clue", 0, 0, 3);
        p.anchor("ambush", 5, 0, -4);
    }

    private static void loneOak(BuildingPlanner.Plan p) {
        // trunk is grown by the builder (vanilla fancy oak); marker stones form a pointing line
        p.set(5, 0, 0, "minecraft:mossy_cobblestone");
        p.set(4, 0, 1, "minecraft:cobblestone_slab[type=bottom]");
        p.set(3, 0, 2, "minecraft:mossy_cobblestone");
        p.set(2, -1, 3, "minecraft:coarse_dirt");
        p.set(2, -2, 3, "minecraft:barrel[facing=up,open=false]");
        p.anchor("loose_earth", 2, -1, 3);
        p.anchor("stand", 2, 0, 4);
    }

    private static void notice(BuildingPlanner.Plan p) {
        p.set(-1, 0, 0, "minecraft:spruce_fence");
        p.set(-1, 1, 0, "minecraft:spruce_fence");
        p.set(1, 0, 0, "minecraft:spruce_fence");
        p.set(1, 1, 0, "minecraft:spruce_fence");
        for (int x = -1; x <= 1; x++) {
            p.set(x, 2, 0, "minecraft:spruce_planks");
            p.set(x, 3, 0, "minecraft:spruce_slab[type=bottom]");
        }
        p.set(0, 2, 1, "minecraft:oak_wall_sign[facing=south]|~ NOTICES ~|Read the board|for work and|local news.");
        p.anchor("read", 0, 0, 2);
        p.anchor("sign", 0, 2, 1);
    }

    private static void pump(BuildingPlanner.Plan p) {
        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 1; z++) p.set(x, -1, z, "minecraft:cobblestone");
        p.set(0, 0, 0, "minecraft:smooth_stone");
        p.set(0, 1, 0, "minecraft:stone_brick_wall");
        p.set(1, 0, 0, "minecraft:cobblestone_wall");
        p.set(2, 0, 0, "minecraft:cauldron");
        p.set(-1, 0, 1, "minecraft:barrel[facing=up,open=false]");
        p.anchor("body", 0, 0, 0);
        p.anchor("trough", 2, 0, 0);
        p.anchor("stand", 0, 0, 2);
    }

    private static void bridgeOut(BuildingPlanner.Plan p) {
        for (int z = -14; z <= 14; z++) {
            p.set(0, 0, z, Math.abs(z) % 5 == 0 ? "minecraft:spruce_log[axis=y]" : "minecraft:spruce_fence");
            if (Math.abs(z) % 5 == 0) p.set(0, 1, z, "minecraft:lantern[hanging=false]");
        }
        p.set(-1, 0, -4, "minecraft:oak_sign[rotation=4]|BRIDGE OUT|Eastfall crossing|closed until|repairs.");
        p.set(-1, 0, 4, "minecraft:oak_sign[rotation=4]|Turn back.|Ask at the|Bridgewarden's|hut.");
        p.anchor("barrier", -2, 0, 0);
        p.anchor("sign", -1, 0, -4);
    }

    private static void milestone(BuildingPlanner.Plan p) {
        p.set(0, 0, 0, "minecraft:mossy_stone_brick_wall");
        p.set(0, 1, 0, "minecraft:stone_brick_wall");
        p.set(-1, 0, 0, "minecraft:oak_sign[rotation=4]|Brindlecross|one mile east|~|Westreach Road");
        p.anchor("sign", -1, 0, 0);
    }

    private static void camp(BuildingPlanner.Plan p) {
        p.set(0, 0, 0, "minecraft:campfire[lit=true,signal_fire=false,facing=north]");
        p.set(2, 0, 1, "minecraft:white_carpet");
        p.set(2, 0, 2, "minecraft:white_carpet");
        p.set(-2, 0, 1, "minecraft:brown_carpet");
        p.set(-2, 0, 2, "minecraft:brown_carpet");
        p.set(3, 0, -2, "minecraft:barrel[facing=up,open=false]");
        p.set(3, 1, -2, "minecraft:barrel[facing=up,open=false]");
        p.set(-3, 0, -2, "minecraft:chest[facing=south,type=single]");
        p.set(-3, 0, 0, "minecraft:lantern[hanging=false]");
        p.set(1, 0, -3, "minecraft:spruce_slab[type=bottom]");
        p.anchor("fire", 0, 0, 0);
        p.anchor("chest", -3, 0, -2);
    }

    private static void cache(BuildingPlanner.Plan p) {
        p.set(0, 0, 0, "minecraft:barrel[facing=up,open=false]");
        p.set(1, 0, 0, "minecraft:bone_block[axis=x]");
        p.set(-1, 0, 1, "minecraft:lantern[hanging=false]");
        p.set(0, 0, -1, "minecraft:cobweb");
        p.anchor("box", 0, 0, 0);
    }

    /** Noncanonical test puzzle: three coloured pedestals with levers, order printed on the sign. */
    private static void leverTest(BuildingPlanner.Plan p) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) p.set(x, -1, z, "minecraft:smooth_stone");
        String[][] peds = {{"-2", "minecraft:red_concrete", "red"}, {"0", "minecraft:blue_concrete", "blue"}, {"2", "minecraft:lime_concrete", "green"}};
        for (String[] ped : peds) {
            int x = Integer.parseInt(ped[0]);
            p.set(x, 0, -2, "minecraft:stone_bricks");
            p.set(x, 1, -2, "minecraft:stone_bricks");
            p.set(x, 2, -2, ped[1]);
            p.set(x, 1, -1, "minecraft:lever[face=wall,facing=south,powered=false]");
            p.anchor("lever_" + ped[2], x, 1, -1);
        }
        p.set(0, -1, 1, "minecraft:iron_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]");
        p.set(0, -2, 1, "minecraft:air");
        p.set(0, -3, 1, "minecraft:barrel[facing=up,open=false]");
        p.anchor("hatch", 0, -1, 1);
        p.anchor("cache", 0, -3, 1);
        p.set(0, 0, 3, "minecraft:oak_sign[rotation=0]|TEST PUZZLE|Pull levers:|red, green,|then blue.");
        p.anchor("stand", 0, 0, 4);
    }

    /** Fenced ring for combat tests. */
    /**
     * A natural stone arch spanning {@code span} blocks along X. Use an absolute feature y (the base of the legs);
     * the legs reach 14 blocks further down so they meet uneven ground.
     */
    private static void rockArch(BuildingPlanner.Plan p, int span) {
        double a = span / 2.0, b = span * 0.55, thick = Math.max(3, span / 7.0);
        String[] rock = {"minecraft:stone", "minecraft:andesite", "minecraft:stone", "minecraft:tuff", "minecraft:stone", "minecraft:andesite",
                "minecraft:stone", "minecraft:calcite", "minecraft:cobblestone", "minecraft:stone"};
        for (int x = (int) -a - 2; x <= (int) a + 2; x++) for (int y = -14; y <= (int) b + 2; y++) for (int z = -3; z <= 3; z++) {
            double yy = Math.max(0, y);
            double r = Math.hypot(x / a, yy / b);                    // 1.0 on the outer curve
            double inner = Math.hypot(x / (a - thick), yy / (b - thick));
            double rough = ((x * 73856093 ^ y * 19349663 ^ z * 83492791) & 7) / 28.0;
            double zw = 2.2 + (y > b * 0.6 ? -0.6 : 0.6) + rough;
            if (Math.abs(z) > zw) continue;
            boolean solid = r <= 1.0 + rough * 0.08 && inner >= 1.0 - rough * 0.1;
            if (y < 0) solid = Math.abs(x) >= a - thick - rough && Math.abs(x) <= a + 0.5 + rough * 4;
            if (!solid) continue;
            int warp = (int) Math.round(Math.sin(x * 0.35 + z * 0.2) * 1.5);
            int band = Math.floorMod(Math.floorDiv(y + warp + 14, 3), rock.length);
            if (((x * 7349 ^ y * 1931 ^ z * 3307) & 15) == 0) band = 8;   // scattered rough patches
            p.set(x, y, z, rock[band]);
        }
        p.anchor("under", 0, 0, 0);
        p.anchor("top", 0, (int) b + 1, 0);
    }

    /**
     * A floating rock island (use an absolute y: the grass top). Grass and dirt over a jagged stone underside that
     * tapers to a point, a small tree and hanging roots.
     */
    private static void skyIsland(BuildingPlanner.Plan p, int R, long seed) {
        java.util.Random r = new java.util.Random(seed);
        double[] lobes = {r.nextDouble() * 6.28, r.nextDouble() * 6.28, r.nextDouble() * 6.28};
        for (int x = -R - 3; x <= R + 3; x++) for (int z = -R - 3; z <= R + 3; z++) {
            double ang = Math.atan2(z, x);
            double edge = R * (0.82 + 0.1 * Math.sin(ang * 3 + lobes[0]) + 0.08 * Math.sin(ang * 5 + lobes[1]));
            double d = Math.hypot(x, z);
            if (d > edge) continue;
            double t = d / edge;
            int depth = (int) Math.round(R * 1.35 * Math.pow(1 - t, 0.75) + 1 + 2 * Math.sin(x * 0.7 + lobes[2]) * Math.cos(z * 0.6));
            p.set(x, 0, z, "minecraft:grass_block");
            p.set(x, -1, z, "minecraft:dirt");
            if (depth > 2) p.set(x, -2, z, "minecraft:dirt");
            for (int y = -3; y > -depth; y--) {
                int k = Math.floorMod(y + (int) (x * 0.3), 7);
                p.set(x, y, z, k == 0 ? "minecraft:andesite" : k == 3 ? "minecraft:tuff" : k == 5 ? "minecraft:mossy_cobblestone" : "minecraft:stone");
            }
            if (t > 0.6 && r.nextDouble() < 0.06) for (int y = -2; y > -2 - r.nextInt(5); y--) p.set(x, y - depth + 2, z, "minecraft:hanging_roots");
            else if (t < 0.8 && r.nextDouble() < 0.10) p.set(x, 1, z, r.nextDouble() < 0.5 ? "minecraft:short_grass" : "minecraft:poppy");
        }
        // a small persistent tree
        int tx = (int) Math.round(R * 0.2), tz = (int) Math.round(-R * 0.15);
        for (int y = 1; y <= 5; y++) p.set(tx, y, tz, "minecraft:oak_log[axis=y]");
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 4; y <= 6; y++) {
            if (Math.abs(x) + Math.abs(z) + (y - 4) > 4 || (x == 0 && z == 0 && y < 6)) continue;
            p.set(tx + x, y, tz + z, "minecraft:oak_leaves[persistent=true]");
        }
        p.anchor("top", 0, 1, 0);
    }

    /** Broken stone walls of an old building, overgrown. */
    private static void ruin(BuildingPlanner.Plan p, int s, long seed) {
        java.util.Random r = new java.util.Random(seed);
        for (int x = -s; x <= s; x++) for (int z = -s; z <= s; z++) {
            boolean wall = Math.abs(x) == s || Math.abs(z) == s;
            if (!wall) { if (r.nextDouble() < 0.3) p.set(x, -1, z, r.nextBoolean() ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone"); continue; }
            int hgt = (int) (Math.max(0, 5 - Math.abs(x + z) * 0.25) + r.nextInt(3));
            for (int y = 0; y < hgt; y++) {
                double q = r.nextDouble();
                p.set(x, y, z, q < 0.4 ? "minecraft:mossy_stone_bricks" : q < 0.7 ? "minecraft:cracked_stone_bricks" : "minecraft:stone_bricks");
            }
            if (hgt > 0 && r.nextDouble() < 0.25) p.set(x, hgt, z, "minecraft:mossy_cobblestone_wall");
        }
        p.set(0, 0, 0, "minecraft:chiseled_stone_bricks");
        p.anchor("center", 0, 0, 0);
    }

    /** A weathered stone figure on a plinth. */
    private static void statue(BuildingPlanner.Plan p) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) p.set(x, 0, z, "minecraft:stone_bricks");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) p.set(x, 1, z, "minecraft:polished_andesite");
        p.set(0, 2, 0, "minecraft:polished_andesite"); p.set(0, 3, 0, "minecraft:polished_andesite"); p.set(0, 4, 0, "minecraft:polished_andesite");
        p.set(-1, 4, 0, "minecraft:andesite_wall"); p.set(1, 4, 0, "minecraft:andesite_wall"); p.set(1, 5, 0, "minecraft:andesite_wall");
        p.set(0, 5, 0, "minecraft:chiseled_stone_bricks");
        p.set(0, 6, 0, "minecraft:mossy_cobblestone_wall");
        p.anchor("front", 0, 0, 3);
    }

    private static void summitCairn(BuildingPlanner.Plan p) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) p.set(x, 0, z, "minecraft:cobblestone");
        p.set(0, 1, 0, "minecraft:mossy_cobblestone");
        p.set(0, 2, 0, "minecraft:cobblestone_wall");
        p.set(0, 3, 0, "minecraft:lantern[hanging=false]");
        p.set(1, 1, 0, "minecraft:cobblestone_slab[type=bottom]");
        p.set(0, 0, 2, "minecraft:spruce_sign[rotation=0]|TEST SUMMIT|Highest point|of the test|mountain");
        p.anchor("stand", 0, 0, 3);
    }

    private static void arena(BuildingPlanner.Plan p) {
        int r = 12;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d <= r) p.set(x, -1, z, (x + z) % 3 == 0 ? "minecraft:coarse_dirt" : "minecraft:dirt_path");
            if (d > r - 0.7 && d <= r + 0.3) {
                boolean gate = z > r - 2 && Math.abs(x) <= 1;
                if (!gate) p.set(x, 0, z, Math.abs(x) % 4 == 0 && Math.abs(z) % 4 == 0 ? "minecraft:spruce_log[axis=y]" : "minecraft:spruce_fence");
            }
        }
        p.set(2, 0, r + 1, "minecraft:oak_sign[rotation=0]|TEST ARENA|Combat testing|area");
        p.anchor("center", 0, 0, 0);
        p.anchor("gate", 0, 0, r + 2);
    }

    private static void jetty(BuildingPlanner.Plan p) {
        for (int x = 0; x >= -6; x--) {
            p.set(x, 0, 0, "minecraft:spruce_planks");
            p.set(x, 0, 1, "minecraft:spruce_planks");
            if (x % 3 == 0) {
                for (int y = -4; y <= -1; y++) {
                    p.set(x, y, -1, "minecraft:spruce_fence");
                    p.set(x, y, 2, "minecraft:spruce_fence");
                }
                p.set(x, 1, -1, "minecraft:spruce_fence");
                p.set(x, 1, 2, "minecraft:spruce_fence");
            }
        }
        p.set(-6, 2, -1, "minecraft:lantern[hanging=false]");
        p.set(1, 0, 0, "minecraft:barrel[facing=up,open=false]");
        p.anchor("end", -5, 1, 1);
    }
}
