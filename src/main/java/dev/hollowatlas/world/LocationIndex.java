package dev.hollowatlas.world;

import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.state.GameState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime lookup of named location anchors across every authored region.
 * Anchor names are global (each region's anchors must be unique); world coordinates are derived from
 * the same {@link RegionModel}s that generated the map, so they cannot drift apart.
 * Regions open progressively: a region's {@code open_flag} (world flag) must be set for players to
 * enter it; the first region is always open.
 */
public final class LocationIndex {
    /** Regions in map order. Their layout files live in data/hollowatlas/region/. */
    public static final List<String> REGIONS = List.of("westreach_brindlecross", "boreal_sableforge", "devtest_grounds");
    public static final String DEFAULT_REGION = REGIONS.get(0);

    private static List<RegionModel> models;
    private static Map<String, RegionModel> owner;
    private static Map<String, int[]> merged;

    private LocationIndex() {}

    private static synchronized void ensure() {
        if (models != null) return;
        long t0 = System.currentTimeMillis();
        List<RegionModel> ms = new ArrayList<>();
        Map<String, RegionModel> own = new LinkedHashMap<>();
        Map<String, int[]> all = new LinkedHashMap<>();
        for (String id : REGIONS) {
            if (LocationIndex.class.getResource("/data/hollowatlas/region/" + id + ".json") == null) {
                HollowAtlas.LOGGER.warn("Region layout {} not present; skipping", id);
                continue;
            }
            RegionModel m = new RegionModel(RegionLayout.loadBuiltin(id));
            ms.add(m);
            for (Map.Entry<String, int[]> e : m.anchors.entrySet()) {
                if (own.containsKey(e.getKey())) m.warnings.add("anchor name used by two regions: " + e.getKey());
                own.put(e.getKey(), m);
                all.put(e.getKey(), e.getValue());
            }
            for (String w : m.warnings) HollowAtlas.LOGGER.warn("Region data ({}): {}", id, w);
        }
        models = Collections.unmodifiableList(ms);
        owner = own;
        merged = all;
        HollowAtlas.LOGGER.info("Location index ready: {} regions, {} anchors in {} ms", ms.size(), all.size(), System.currentTimeMillis() - t0);
    }

    /** The first (starting) region. */
    public static RegionModel model() { ensure(); return models.get(0); }

    public static List<RegionModel> models() { ensure(); return models; }

    /** Regions that belong to this world's mode (story regions in story worlds, the test region in test worlds). */
    public static List<RegionModel> modelsFor(MinecraftServer server) {
        String mode = dev.hollowatlas.state.WorldMode.of(server);
        return models().stream().filter(m -> mode.equals(m.layout.mode == null ? "story" : m.layout.mode)).toList();
    }

    /** First region of this world's mode: where new players arrive. */
    public static RegionModel first(MinecraftServer server) {
        List<RegionModel> l = modelsFor(server);
        return l.isEmpty() ? model() : l.get(0);
    }

    public static boolean isFirstOfMode(RegionModel m) {
        for (RegionModel x : models()) if (x.layout.mode.equals(m.layout.mode)) return x == m;
        return false;
    }

    public static RegionModel region(String id) {
        for (RegionModel m : models()) if (m.layout.id.equals(id)) return m;
        return null;
    }

    public static boolean isOpen(MinecraftServer server, RegionModel m) {
        return m.layout.open_flag == null || m.layout.open_flag.isEmpty() || GameState.get(server).flag(m.layout.open_flag);
    }

    public static boolean has(String anchor) { ensure(); return merged.containsKey(anchor); }

    /** World block position of an anchor (the block the entity's feet occupy). */
    public static BlockPos pos(String anchor) {
        ensure();
        int[] a = merged.get(anchor);
        if (a == null) throw new IllegalArgumentException("Unknown location anchor: " + anchor);
        int[] o = owner.get(anchor).layout.origin;
        return new BlockPos(o[0] + a[0], a[1], o[1] + a[2]);
    }

    public static Vec3d vec(String anchor) { return Vec3d.ofBottomCenter(pos(anchor)); }

    public static Set<String> names() { ensure(); return Collections.unmodifiableSet(merged.keySet()); }

    public static Map<String, int[]> raw() { ensure(); return Collections.unmodifiableMap(merged); }

    /** Region containing a world position, or null. */
    public static RegionModel regionAt(double x, double z) {
        for (RegionModel m : models()) {
            int[] o = m.layout.origin;
            if (Math.abs(x - o[0]) <= m.half && Math.abs(z - o[1]) <= m.half) return m;
        }
        return null;
    }

    /** True if a world position lies inside any authored region. */
    public static boolean inRegion(double x, double z) { return regionAt(x, z) != null; }

    /**
     * Distance from a world position to the outer edge of the currently open play area (negative if outside).
     * Edges shared with another open region don't count as edges.
     */
    public static double distanceToEdge(MinecraftServer server, double x, double z) {
        RegionModel m = regionAt(x, z);
        if (m == null || !isOpen(server, m) || !modelsFor(server).contains(m)) return -1;
        int[] o = m.layout.origin;
        double half = m.half + 0.5;
        double best = Double.MAX_VALUE;
        // west, east, north, south edges
        double[][] edges = {{x - (o[0] - half), -1, 0}, {(o[0] + half) - x, 1, 0}, {z - (o[1] - half), 0, -1}, {(o[1] + half) - z, 0, 1}};
        for (double[] e : edges) {
            double probeX = x + e[1] * (e[0] + 2), probeZ = z + e[2] * (e[0] + 2);
            RegionModel next = regionAt(probeX, probeZ);
            if (next != null && next != m && isOpen(server, next) && modelsFor(server).contains(next)) continue;   // internal seam between open regions
            best = Math.min(best, e[0]);
        }
        return best;
    }

    /** Legacy single-region helper (assumes only the starting region is open). */
    public static double distanceToEdge(double x, double z) {
        RegionModel m = model();
        int[] o = m.layout.origin;
        double half = m.layout.border_size / 2.0;
        return Math.min(half - Math.abs(x - o[0]), half - Math.abs(z - o[1]));
    }

    /** Find the closest anchor name to a world position (used by dev tools). */
    public static String nearest(BlockPos pos) {
        String best = null; double bd = Double.MAX_VALUE;
        for (String n : names()) {
            double d = pos(n).getSquaredDistance(pos);
            if (d < bd) { bd = d; best = n; }
        }
        return best;
    }
}
