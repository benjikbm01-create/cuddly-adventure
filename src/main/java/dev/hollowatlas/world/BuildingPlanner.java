package dev.hollowatlas.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure (Minecraft-free) planner that turns a {@link RegionLayout.Building} description into a list of
 * block placements plus named anchors, in the building's CANONICAL frame:
 * x in [0,w), z in [0,d), y=0 is the floor layer, the front wall is z=d-1 and faces south (+z).
 * Block states are vanilla block-state strings expressed in that canonical (south-facing) orientation;
 * the world builder rotates them into place.
 */
public final class BuildingPlanner {

    public record Placed(int x, int y, int z, String state) {}

    public static final class Plan {
        public final List<Placed> blocks = new ArrayList<>();
        /** Canonical anchors [x, y, z]; y is the standing block (feet) level. */
        public final Map<String, int[]> anchors = new LinkedHashMap<>();
        private final Map<Long, Integer> index = new java.util.HashMap<>();

        void set(int x, int y, int z, String state) {
            long key = (((long) x & 0xFFFF) << 32) | (((long) y & 0xFFFF) << 16) | ((long) z & 0xFFFF);
            Integer at = index.get(key);
            Placed p = new Placed(x, y, z, state);
            if (at != null) blocks.set(at, p);
            else { index.put(key, blocks.size()); blocks.add(p); }
        }

        void anchor(String name, int x, int y, int z) { anchors.put(name, new int[]{x, y, z}); }
    }

    private BuildingPlanner() {}

    public static Plan plan(RegionLayout.Building b) {
        Plan p = new Plan();
        switch (b.type) {
            case "stall" -> stall(p, b);
            case "well" -> well(p, b);
            default -> house(p, b);
        }
        return p;
    }

    // ------------------------------------------------------------------ shells

    private static void house(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d, floors = Math.max(1, b.floors);
        boolean tower = b.type.equals("tower");
        String frameV = b.frame + "[axis=y]";
        String frameX = b.frame + "[axis=x]";
        String frameZ = b.frame + "[axis=z]";
        int doorX = w / 2;

        // floor layer + foundation ring
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
            p.set(x, 0, z, edge ? b.foundation : b.floor);
            // clear interior air for every storey (removes terrain/grass if any)
            for (int y = 1; y < 5 * floors; y++) if (!edge) p.set(x, y, z, "minecraft:air");
        }

        for (int f = 0; f < floors; f++) {
            int base = 5 * f;
            if (f > 0) {
                for (int x = 1; x < w - 1; x++) for (int z = 1; z < d - 1; z++) p.set(x, base, z, b.floor);
            }
            for (int y = base + (f == 0 ? 1 : 0); y <= base + 4; y++) {
                for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
                    boolean onX = x == 0 || x == w - 1, onZ = z == 0 || z == d - 1;
                    if (!onX && !onZ) continue;
                    boolean corner = onX && onZ;
                    String s;
                    if (corner || (onZ && x % 4 == 0) || (onX && z % 4 == 0)) s = frameV;
                    else if (y == base + 4 || y == base) s = onZ ? frameX : frameZ;
                    else s = b.wall;
                    // windows
                    int along = onZ ? x : z;
                    int len = onZ ? w : d;
                    boolean post = corner || along % 4 == 0;
                    if (!post && (y == base + 2 || y == base + 3) && along > 0 && along < len - 1
                            && along % 4 != 0 && (along % 4 == 2 || len <= 6)) {
                        s = "minecraft:glass_pane";
                    }
                    p.set(x, y, z, s);
                }
            }
        }

        // front door (ground floor)
        if (b.type.equals("barn")) {
            for (int x = doorX - 1; x <= doorX + 1; x++) for (int y = 1; y <= 3; y++) p.set(x, y, d - 1, "minecraft:air");
        } else {
            p.set(doorX, 1, d - 1, doorBlock(b) + "[facing=north,half=lower,hinge=left,open=false]");
            p.set(doorX, 2, d - 1, doorBlock(b) + "[facing=north,half=upper,hinge=left,open=false]");
            p.set(doorX, 3, d - 1, frameX);
            // lantern beside the door
            p.set(doorX + 1, 3, d, "minecraft:lantern[hanging=false]");
            p.set(doorX + 1, 2, d, b.frame.replace("stripped_", "").replace("_log", "_fence"));
            p.set(doorX + 1, 1, d, b.frame.replace("stripped_", "").replace("_log", "_fence"));
            p.anchor("post", doorX + 1, 1, d);
        }
        p.anchor("door_out", doorX, 1, d + 1);
        p.anchor("inside", doorX, 1, d - 3);
        p.anchor("center", w / 2, 1, d / 2);

        if (tower) towerTop(p, b, floors); else roof(p, b, floors);

        switch (b.type) {
            case "inn" -> innInterior(p, b);
            case "store" -> storeInterior(p, b);
            case "smithy" -> smithyInterior(p, b);
            case "hall" -> hallInterior(p, b);
            case "bakery" -> bakeryInterior(p, b);
            case "barn" -> barnInterior(p, b);
            case "mill" -> millInterior(p, b);
            case "relay" -> relayInterior(p, b);
            case "tower" -> towerInterior(p, b, floors);
            default -> homeInterior(p, b);
        }
    }

    private static String doorBlock(RegionLayout.Building b) {
        if (b.frame.contains("dark_oak")) return "minecraft:dark_oak_door";
        if (b.frame.contains("spruce")) return "minecraft:spruce_door";
        if (b.frame.contains("birch")) return "minecraft:birch_door";
        return "minecraft:oak_door";
    }

    private static String stairs(String roof) {
        return switch (roof) {
            case "stone_brick" -> "minecraft:stone_brick_stairs";
            case "brick" -> "minecraft:brick_stairs";
            case "deepslate" -> "minecraft:deepslate_tile_stairs";
            case "mud_brick" -> "minecraft:mud_brick_stairs";
            default -> "minecraft:" + roof + "_stairs";
        };
    }

    private static String slab(String roof) {
        return switch (roof) {
            case "stone_brick" -> "minecraft:stone_brick_slab";
            case "brick" -> "minecraft:brick_slab";
            case "deepslate" -> "minecraft:deepslate_tile_slab";
            case "mud_brick" -> "minecraft:mud_brick_slab";
            default -> "minecraft:" + roof + "_slab";
        };
    }

    private static void roof(Plan p, RegionLayout.Building b, int floors) {
        int w = b.w, d = b.d, y0 = 5 * floors;
        String st = stairs(b.roof), sl = slab(b.roof);
        boolean alongX = w >= d;
        int span = alongX ? d : w;      // dimension the slopes run across
        int len = alongX ? w : d;       // ridge length
        int ridgeTop = y0;
        for (int k = 0; ; k++) {
            int lo = k - 1, hi = span - k;
            int y = y0 + k;
            if (lo > hi) break;
            if (lo == hi) {
                for (int t = -1; t <= len; t++) put2(p, alongX, t, y, lo, sl + "[type=bottom]");
                ridgeTop = y;
                break;
            }
            for (int t = -1; t <= len; t++) {
                put2(p, alongX, t, y, lo, st + "[facing=" + (alongX ? "south" : "east") + ",half=bottom]");
                put2(p, alongX, t, y, hi, st + "[facing=" + (alongX ? "north" : "west") + ",half=bottom]");
            }
            for (int s = Math.max(0, lo + 1); s <= Math.min(span - 1, hi - 1); s++) {
                put2(p, alongX, 0, y, s, b.wall);
                put2(p, alongX, len - 1, y, s, b.wall);
                for (int t = 1; t < len - 1; t++) put2(p, alongX, t, y, s, "minecraft:air");
            }
            ridgeTop = y;
        }
        // chimney for warm buildings
        if (b.type.equals("inn") || b.type.equals("bakery") || b.type.equals("smithy") || b.type.equals("house")
                || b.type.equals("farmhouse")) {
            int cx = w - 2, cz = 1;
            for (int y = 5 * floors; y <= ridgeTop + 2; y++) p.set(cx, y, cz, "minecraft:bricks");
            p.set(cx, ridgeTop + 3, cz, "minecraft:campfire[lit=true,signal_fire=false,facing=north]");
        }
    }

    /** Place in roof space: t runs along the ridge, s across it. */
    private static void put2(Plan p, boolean alongX, int t, int y, int s, String state) {
        if (alongX) p.set(t, y, s, state); else p.set(s, y, t, state);
    }

    private static void towerTop(Plan p, RegionLayout.Building b, int floors) {
        int y = 5 * floors;
        for (int x = 0; x < b.w; x++) for (int z = 0; z < b.d; z++) {
            boolean edge = x == 0 || z == 0 || x == b.w - 1 || z == b.d - 1;
            p.set(x, y, z, edge ? b.foundation : b.floor);
            if (edge) p.set(x, y + 1, z, (x + z) % 2 == 0 ? "minecraft:cobblestone_wall" : "minecraft:spruce_fence");
        }
        p.set(1, y + 1, 1, "minecraft:lantern[hanging=false]");
        p.anchor("lookout", b.w / 2, y + 1, b.d / 2 + 1);
    }

    // ------------------------------------------------------------- interiors

    private static void bed(Plan p, int x, int y, int z, String color, String anchor) {
        // head toward north (z smaller)
        p.set(x, y, z, "minecraft:" + color + "_bed[facing=north,part=head,occupied=false]");
        p.set(x, y, z + 1, "minecraft:" + color + "_bed[facing=north,part=foot,occupied=false]");
        p.anchor(anchor, x, y, z);
    }

    private static void table(Plan p, int x, int y, int z, boolean lantern) {
        p.set(x, y, z, "minecraft:spruce_slab[type=top]");
        if (lantern) p.set(x, y + 1, z, "minecraft:lantern[hanging=false]");
    }

    private static void chair(Plan p, int x, int y, int z, String facingBack) {
        p.set(x, y, z, "minecraft:oak_stairs[facing=" + facingBack + ",half=bottom]");
    }

    private static final String[] BED_COLORS = {"red", "blue", "green", "yellow", "brown", "cyan"};

    private static void homeInterior(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        int beds = Math.max(1, b.beds);
        int colorSeed = Math.abs(b.id.hashCode());
        int[] bedXs = {1, 3, w - 2};
        for (int i = 0; i < beds && i < 3; i++) {
            int bx = bedXs[i];
            if (bx == w / 2 && d < 8) bx = Math.max(1, bx - 1);
            bed(p, bx, 1, 1, BED_COLORS[(colorSeed + i) % BED_COLORS.length], "bed" + i);
        }
        // table and chairs on the right side
        int tx = w - 3, tz = Math.min(3, d - 4);
        if (tx > 3 && tz >= 2) {
            table(p, tx, 1, tz, true);
            chair(p, tx, 1, tz + 1, "south");
            p.anchor("table", tx, 1, tz + 1);
            p.anchor("table_block", tx, 1, tz);
        }
        p.set(w - 2, 1, d - 2 >= 3 ? d - 3 : 2, "minecraft:barrel[facing=up,open=false]");
        p.set(1, 1, d - 2, "minecraft:crafting_table");
        if (d >= 8) p.set(1, 1, d - 3, "minecraft:bookshelf");
        p.set(w / 2, 1, d / 2, "minecraft:" + (colorSeed % 2 == 0 ? "brown" : "red") + "_carpet");
        p.set(w - 2, 2, 1, "minecraft:potted_" + (colorSeed % 3 == 0 ? "poppy" : colorSeed % 3 == 1 ? "blue_orchid" : "dandelion"));
        p.set(w - 2, 1, 1, "minecraft:smoker[facing=south,lit=false]");
        p.anchor("home", w / 2, 1, d / 2);
        p.anchor("work", w / 2, 1, d / 2);
        if (b.type.equals("farmhouse")) p.set(1, 1, d / 2, "minecraft:composter[level=4]");
    }

    private static void innInterior(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        // bar counter along z=3 from x=3..8, keeper behind at z=2
        for (int x = 3; x <= 8; x++) p.set(x, 1, 3, "minecraft:spruce_slab[type=top]");
        p.set(2, 1, 3, "minecraft:barrel[facing=up,open=false]");
        for (int x = 3; x <= 8; x += 1) p.set(x, 1, 1, x % 2 == 0 ? "minecraft:barrel[facing=south,open=false]" : "minecraft:barrel[facing=up,open=false]");
        p.set(5, 2, 1, "minecraft:barrel[facing=up,open=false]");
        p.set(4, 2, 3, "minecraft:lantern[hanging=false]");
        p.set(7, 2, 3, "minecraft:brewing_stand");
        p.anchor("work", 5, 1, 2);
        p.anchor("counter_front", 5, 1, 4);
        // hearth on the east wall (cooking station)
        int hz = d / 2;
        p.set(w - 2, 1, hz - 1, "minecraft:bricks");
        p.set(w - 2, 1, hz + 1, "minecraft:bricks");
        p.set(w - 2, 2, hz - 1, "minecraft:bricks");
        p.set(w - 2, 2, hz + 1, "minecraft:bricks");
        p.set(w - 2, 3, hz, "minecraft:bricks");
        p.set(w - 2, 3, hz - 1, "minecraft:bricks");
        p.set(w - 2, 3, hz + 1, "minecraft:bricks");
        p.set(w - 2, 1, hz, "minecraft:campfire[lit=true,signal_fire=false,facing=west]");
        p.anchor("hearth_block", w - 2, 1, hz);
        p.anchor("hearth", w - 4, 1, hz);
        // tables
        table(p, 10, 1, 7, true);
        chair(p, 9, 1, 7, "west");
        chair(p, 11, 1, 7, "east");
        table(p, 4, 1, 7, true);
        chair(p, 3, 1, 7, "west");
        chair(p, 5, 1, 7, "east");
        p.anchor("table_a", 9, 1, 8);
        p.anchor("table_b", 5, 1, 8);
        p.anchor("common_room", w / 2 + 1, 1, 6);
        // stairs up along the west wall, rising northward (z decreasing)
        for (int i = 0; i <= 4; i++) {
            p.set(1, 1 + i, d - 3 - i, "minecraft:spruce_stairs[facing=north,half=bottom]");
        }
        // headroom: open the upper floor above the last steps
        p.set(1, 5, d - 3 - 2, "minecraft:air");
        p.set(1, 5, d - 3 - 3, "minecraft:air");
        p.set(1, 5, d - 3 - 1, "minecraft:air");
        // upper floor rooms: partition along z=4 for x=3..13, cross walls at x=6, x=10
        int up = 6;
        for (int x = 3; x <= w - 2; x++) for (int y = up; y <= 9; y++) p.set(x, y, 4, "minecraft:spruce_planks");
        for (int z = 1; z <= 3; z++) for (int y = up; y <= 9; y++) {
            p.set(6, y, z, "minecraft:spruce_planks");
            p.set(10, y, z, "minecraft:spruce_planks");
        }
        int[] doorXs = {4, 8, 12};
        String[] rooms = {"room_guest", "room_b", "room_c"};
        for (int i = 0; i < 3; i++) {
            int dx = doorXs[i];
            p.set(dx, up, 4, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]");
            p.set(dx, up + 1, 4, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]");
            bed(p, dx - 1, up, 1, BED_COLORS[i + 1], "bed_" + rooms[i]);
            p.set(dx + 1, up, 1, "minecraft:barrel[facing=up,open=false]");
            p.set(dx + 1, up + 1, 1, "minecraft:lantern[hanging=false]");
            p.anchor(rooms[i], dx, up, 3);
        }
        p.anchor("landing", 2, up, 6);
        table(p, 8, up, 7, true);
        chair(p, 8, up, 8, "south");
        p.set(w - 2, up, d - 2, "minecraft:bookshelf");
    }

    private static void storeInterior(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        for (int x = 2; x <= w - 4; x++) p.set(x, 1, 3, "minecraft:spruce_slab[type=top]");
        p.set(3, 2, 3, "minecraft:lantern[hanging=false]");
        for (int x = 1; x <= w - 2; x++) {
            p.set(x, 1, 1, x % 3 == 0 ? "minecraft:chest[facing=south,type=single]" : "minecraft:barrel[facing=up,open=false]");
            p.set(x, 2, 1, x % 2 == 0 ? "minecraft:bookshelf" : "minecraft:barrel[facing=south,open=false]");
        }
        for (int z = 4; z <= d - 3; z++) {
            p.set(1, 1, z, "minecraft:barrel[facing=east,open=false]");
            p.set(w - 2, 1, z, "minecraft:barrel[facing=west,open=false]");
        }
        p.set(w - 2, 2, 4, "minecraft:potted_azure_bluet");
        p.anchor("work", w / 2 - 1, 1, 2);
        p.anchor("counter_front", w / 2 - 1, 1, 4);
    }

    private static void smithyInterior(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        p.set(1, 1, 1, "minecraft:blast_furnace[facing=south,lit=true]");
        p.set(2, 1, 1, "minecraft:lava_cauldron");
        p.set(3, 1, 1, "minecraft:blast_furnace[facing=south,lit=true]");
        for (int x = 1; x <= 3; x++) p.set(x, 2, 1, "minecraft:bricks");
        p.set(3, 1, 3, "minecraft:anvil[facing=east]");
        p.set(w - 3, 1, 1, "minecraft:grindstone[face=floor,facing=south]");
        p.set(w - 2, 1, 1, "minecraft:smithing_table");
        p.set(w - 2, 1, 3, "minecraft:chest[facing=west,type=single]");
        p.set(w - 2, 1, 4, "minecraft:barrel[facing=up,open=false]");
        p.anchor("work", 4, 1, 3);
        p.anchor("counter_front", w / 2, 1, d - 3);
        p.anchor("forge_block", 2, 1, 1);
    }

    private static void hallInterior(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        p.set(w / 2, 1, 2, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        p.anchor("lectern", w / 2, 1, 3);
        p.anchor("lectern_block", w / 2, 1, 2);
        for (int z = 4; z <= d - 4; z += 2) {
            for (int x = 2; x < w / 2 - 1; x++) p.set(x, 1, z, "minecraft:spruce_stairs[facing=south,half=bottom]");
            for (int x = w / 2 + 2; x <= w - 3; x++) p.set(x, 1, z, "minecraft:spruce_stairs[facing=south,half=bottom]");
        }
        p.set(1, 1, 1, "minecraft:bookshelf");
        p.set(1, 2, 1, "minecraft:bookshelf");
        p.set(w - 2, 1, 1, "minecraft:bookshelf");
        p.set(w - 2, 2, 1, "minecraft:bookshelf");
        p.set(2, 1, 1, "minecraft:lantern[hanging=false]");
        p.set(w - 3, 1, 1, "minecraft:lantern[hanging=false]");
        p.anchor("work", w / 2, 1, 3);
        p.anchor("seats", 3, 1, 5);
    }

    private static void bakeryInterior(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        p.set(1, 1, 1, "minecraft:smoker[facing=south,lit=true]");
        p.set(2, 1, 1, "minecraft:furnace[facing=south,lit=true]");
        p.set(3, 1, 1, "minecraft:smoker[facing=south,lit=true]");
        p.set(w - 2, 1, 1, "minecraft:hay_block[axis=y]");
        p.set(w - 2, 2, 1, "minecraft:hay_block[axis=y]");
        p.set(w - 3, 1, 1, "minecraft:barrel[facing=up,open=false]");
        table(p, w / 2, 1, 3, false);
        p.set(w / 2, 2, 3, "minecraft:cake[bites=0]");
        p.set(1, 1, d - 3, "minecraft:barrel[facing=up,open=false]");
        bed(p, w - 2, 1, d - 4 > 2 ? d - 5 : 2, "white", "bed0");
        p.anchor("work", 2, 1, 2);
        p.anchor("home", w / 2, 1, d / 2);
    }

    private static void barnInterior(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        for (int z = 1; z <= d - 3; z++) {
            p.set(1, 1, z, z % 3 == 0 ? "minecraft:hay_block[axis=z]" : "minecraft:oak_fence");
            p.set(w - 2, 1, z, z % 3 == 1 ? "minecraft:hay_block[axis=y]" : "minecraft:oak_fence");
        }
        p.set(1, 2, 3, "minecraft:hay_block[axis=x]");
        p.set(w - 2, 2, 4, "minecraft:hay_block[axis=x]");
        p.set(w / 2, 1, 1, "minecraft:composter[level=6]");
        p.anchor("work", w / 2, 1, 3);
    }

    private static void millInterior(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        p.set(w / 2, 1, 2, "minecraft:grindstone[face=floor,facing=south]");
        p.set(1, 1, 1, "minecraft:barrel[facing=up,open=false]");
        p.set(2, 1, 1, "minecraft:barrel[facing=up,open=false]");
        p.set(1, 2, 1, "minecraft:barrel[facing=up,open=false]");
        p.set(w - 2, 1, 1, "minecraft:stonecutter[facing=south]");
        p.set(w - 2, 1, d - 3, "minecraft:hay_block[axis=y]");
        bed(p, 1, 1, d - 4, "brown", "bed0");
        p.anchor("work", w / 2, 1, 3);
        p.anchor("home", w / 2, 1, d / 2 + 1);
    }

    private static void relayInterior(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        p.set(2, 1, 1, "minecraft:lectern[facing=south,has_book=false,powered=false]");
        p.set(1, 1, 1, "minecraft:chest[facing=south,type=single]");
        for (int x = w - 4; x <= w - 2; x++) for (int z = 1; z <= 2; z++) p.set(x, 1, z, "minecraft:barrel[facing=up,open=false]");
        p.set(w - 2, 2, 1, "minecraft:barrel[facing=up,open=false]");
        p.set(w - 3, 2, 1, "minecraft:barrel[facing=up,open=false]");
        p.set(1, 1, d - 3, "minecraft:cartography_table");
        bed(p, w - 2, 1, d - 4 > 2 ? d - 5 : 3, "gray", "bed0");
        p.anchor("desk", 2, 1, 2);
        p.anchor("lectern_block", 2, 1, 1);
        p.anchor("crate_block", w - 3, 1, 2);
        p.anchor("strongbox", 1, 1, 1);
        p.anchor("work", 2, 1, 2);
        p.anchor("crates", w - 3, 1, 3);
    }

    private static void towerInterior(Plan p, RegionLayout.Building b, int floors) {
        int x = b.w / 2;
        for (int y = 1; y < 5 * floors + 1; y++) {
            p.set(x, y, 1, "minecraft:ladder[facing=south]");
        }
        for (int f = 1; f <= floors; f++) p.set(x, 5 * f, 1, "minecraft:ladder[facing=south]");
        p.set(1, 1, b.d - 2, "minecraft:barrel[facing=up,open=false]");
        p.set(b.w - 2, 1, 1, "minecraft:lantern[hanging=false]");
        p.anchor("work", x, 1, b.d / 2);
    }

    // ------------------------------------------------------------- set pieces

    private static void stall(Plan p, RegionLayout.Building b) {
        int w = b.w, d = b.d;
        String color = b.wall.startsWith("minecraft:") ? b.wall : "minecraft:" + b.wall;
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
            if (corner) for (int y = 1; y <= 3; y++) p.set(x, y, z, "minecraft:spruce_fence");
            p.set(x, 4, z, color);
        }
        for (int x = 0; x < w; x++) p.set(x, 1, d - 1, x == 0 || x == w - 1 ? "minecraft:spruce_fence" : "minecraft:spruce_slab[type=top]");
        for (int x = 1; x < w - 1; x++) p.set(x, 1, 0, "minecraft:barrel[facing=up,open=false]");
        p.anchor("work", w / 2, 1, d / 2);
        p.anchor("counter_front", w / 2, 1, d + 1);
        p.anchor("door_out", w / 2, 1, d + 1);
    }

    private static void well(Plan p, RegionLayout.Building b) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            boolean mid = x == 1 && z == 1;
            p.set(x, 0, z, mid ? "minecraft:water[level=0]" : "minecraft:cobblestone");
            p.set(x, -1, z, mid ? "minecraft:water[level=0]" : "minecraft:cobblestone");
            p.set(x, 1, z, mid ? "minecraft:air" : "minecraft:cobblestone_wall");
        }
        p.set(0, 2, 0, "minecraft:spruce_fence");
        p.set(2, 2, 2, "minecraft:spruce_fence");
        p.set(0, 2, 2, "minecraft:spruce_fence");
        p.set(2, 2, 0, "minecraft:spruce_fence");
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) p.set(x, 3, z, "minecraft:spruce_slab[type=bottom]");
        p.anchor("door_out", 1, 1, 4);
    }
}
