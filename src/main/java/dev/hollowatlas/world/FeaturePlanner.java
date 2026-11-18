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
    }

    private static void milestone(BuildingPlanner.Plan p) {
        p.set(0, 0, 0, "minecraft:mossy_stone_brick_wall");
        p.set(0, 1, 0, "minecraft:stone_brick_wall");
        p.set(-1, 0, 0, "minecraft:oak_sign[rotation=4]|Brindlecross|one mile east|~|Westreach Road");
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
