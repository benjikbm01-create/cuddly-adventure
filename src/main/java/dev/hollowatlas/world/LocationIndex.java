package dev.hollowatlas.world;

import dev.hollowatlas.HollowAtlas;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Runtime lookup of named location anchors (e.g. {@code inn.work}, {@code orchard_pump}).
 * Every NPC position, quest point and interaction refers to anchors by name; world coordinates are
 * derived from the same {@link RegionModel} that generated the map, so they cannot drift apart.
 */
public final class LocationIndex {
    public static final String DEFAULT_REGION = "westreach_brindlecross";
    private static RegionModel model;

    private LocationIndex() {}

    public static synchronized RegionModel model() {
        if (model == null) {
            long t0 = System.currentTimeMillis();
            model = new RegionModel(RegionLayout.loadBuiltin(DEFAULT_REGION));
            HollowAtlas.LOGGER.info("Location index ready: {} anchors in {} ms", model.anchors.size(), System.currentTimeMillis() - t0);
            for (String w : model.warnings) HollowAtlas.LOGGER.warn("Region data: {}", w);
        }
        return model;
    }

    public static boolean has(String anchor) { return model().anchors.containsKey(anchor); }

    /** World block position of an anchor (the block the entity's feet occupy). */
    public static BlockPos pos(String anchor) {
        int[] a = model().anchor(anchor);
        if (a == null) throw new IllegalArgumentException("Unknown location anchor: " + anchor);
        int[] o = model().layout.origin;
        return new BlockPos(o[0] + a[0], a[1], o[1] + a[2]);
    }

    public static Vec3d vec(String anchor) { return Vec3d.ofBottomCenter(pos(anchor)); }

    public static Set<String> names() { return Collections.unmodifiableSet(model().anchors.keySet()); }

    public static Map<String, int[]> raw() { return Collections.unmodifiableMap(model().anchors); }

    /** True if a world position lies inside the authored region bounds. */
    public static boolean inRegion(double x, double z) {
        RegionModel m = model();
        int[] o = m.layout.origin;
        return Math.abs(x - o[0]) <= m.half && Math.abs(z - o[1]) <= m.half;
    }

    /** Distance from a world position to the nearest region edge (negative if outside). */
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
