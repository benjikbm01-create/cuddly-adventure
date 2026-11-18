package dev.hollowatlas.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, Minecraft-free model of a region computed from its {@link RegionLayout}:
 * final terrain height grid, water and road masks, building placements and every named anchor.
 * The world builder and the runtime {@code LocationIndex} both derive from this, so anchors always
 * agree with the blocks that were actually generated.
 */
public final class RegionModel {
    public static final byte SURF_GRASS = 0, SURF_ROAD = 1, SURF_LANE = 2, SURF_BANK = 3, SURF_BED = 4,
            SURF_ROCK = 5, SURF_BRIDGE = 6, SURF_COBBLE = 7, SURF_FIELD = 8, SURF_PAD = 9, SURF_WALL = 10;
    public static final byte ZONE_NONE = -1;
    public static final byte FLUID_WATER = 0, FLUID_LAVA = 1;
    public static final byte BRIDGE_AUTO = 0, BRIDGE_STONE = 1, BRIDGE_WOOD = 2, BRIDGE_ROPE = 3;

    public final RegionLayout layout;
    public final int half;
    public final int size;
    /** Final surface block Y per column. */
    public final int[] height;
    /** Water surface Y per column, or Integer.MIN_VALUE if dry. */
    public final int[] water;
    public final byte[] surface;
    /** Bridge deck Y per column (0 if none). */
    public final int[] bridge;
    /** Index into layout.zones per column, or ZONE_NONE. */
    public final byte[] zone;
    /** Fluid per wet column (FLUID_*). */
    public final byte[] fluid;
    /** Bridge style per bridge column (BRIDGE_*). */
    public final byte[] bridgeStyle;
    public final boolean[] occupied;     // building footprints (+ small margin)
    public final boolean[] roadMask;     // any road/lane cell including bridge cells
    public final List<PlacedBuilding> buildings = new ArrayList<>();
    public final List<PlacedFeature> features = new ArrayList<>();
    public final Map<String, int[]> anchors = new LinkedHashMap<>();
    public final List<String> warnings = new ArrayList<>();

    public static final class PlacedBuilding {
        public final RegionLayout.Building def;
        public final BuildingPlanner.Plan plan;
        public final int padY;
        public final int rotation; // 0 none, 1 cw90, 2 180, 3 ccw90

        PlacedBuilding(RegionLayout.Building def, BuildingPlanner.Plan plan, int padY) {
            this.def = def;
            this.plan = plan;
            this.padY = padY;
            this.rotation = switch (def.facing) {
                case "west" -> 1;
                case "north" -> 2;
                case "east" -> 3;
                default -> 0;
            };
        }

        /** Canonical (x,z) -> local region (x,z). */
        public int[] toLocal(int cx, int cz) {
            int x = cx - def.w / 2, z = cz - def.d / 2;
            int rx, rz;
            switch (rotation) {
                case 1 -> { rx = -z; rz = x; }
                case 2 -> { rx = -x; rz = -z; }
                case 3 -> { rx = z; rz = -x; }
                default -> { rx = x; rz = z; }
            }
            return new int[]{rx + def.x, rz + def.z};
        }

        /** Canonical anchor/position -> local region [x, y, z]. */
        public int[] toLocal(int[] canonical) {
            int[] xz = toLocal(canonical[0], canonical[2]);
            return new int[]{xz[0], padY + canonical[1], xz[1]};
        }
    }

    public static final class PlacedFeature {
        public final RegionLayout.Feature def;
        public final BuildingPlanner.Plan plan;
        PlacedFeature(RegionLayout.Feature def) { this.def = def; this.plan = FeaturePlanner.plan(def); }
    }

    /** Resolve a feature-relative position to local region [x, y, z]. */
    public int[] featurePos(PlacedFeature f, int dx, int dy, int dz) {
        int x = f.def.x + dx, z = f.def.z + dz;
        int y = f.def.y != 0 ? f.def.y + dy : heightAt(x, z) + 1 + dy;
        return new int[]{x, y, z};
    }

    public RegionModel(RegionLayout layout) {
        this.layout = layout;
        this.half = layout.half_size;
        this.size = half * 2 + 1;
        int n = size * size;
        height = new int[n];
        water = new int[n];
        surface = new byte[n];
        bridge = new int[n];
        bridgeStyle = new byte[n];
        zone = new byte[n];
        fluid = new byte[n];
        occupied = new boolean[n];
        roadMask = new boolean[n];
        java.util.Arrays.fill(water, Integer.MIN_VALUE);
        compute();
    }

    public int idx(int x, int z) { return (z + half) * size + (x + half); }
    public boolean inside(int x, int z) { return x >= -half && x <= half && z >= -half && z <= half; }
    public int heightAt(int x, int z) {
        x = Math.max(-half, Math.min(half, x));
        z = Math.max(-half, Math.min(half, z));
        return height[idx(x, z)];
    }

    // ------------------------------------------------------------------ noise

    private double hash(int x, int z) {
        long h = layout.seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + z * 0x85157AF5L;
        h ^= (h >>> 31); h *= 0xBF58476D1CE4E5B9L; h ^= (h >>> 27);
        return ((h >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    private double valueNoise(double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
        double a = hash(x0, z0), b = hash(x0 + 1, z0), c = hash(x0, z0 + 1), d = hash(x0 + 1, z0 + 1);
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz; // 0..1
    }

    public double noise(double x, double z) { return valueNoise(x, z) * 2 - 1; }

    /** Ridged noise 0..1: sharp creases where plain noise crosses zero (crags, ridgelines). */
    private double ridged(double x, double z) {
        double a = 1 - Math.abs(noise(x, z));
        double b = 1 - Math.abs(noise(x * 2.1 + 17, z * 2.1 - 9));
        return a * a * 0.7 + b * b * 0.3;
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    // ---------------------------------------------------------------- geometry

    /** Distance from point to polyline, and parametric info. */
    /** Distance to a polyline plus the interpolated height along it, for graded roads: {distance, y}. */
    static double[] gradedAt(List<int[]> pts, double[] vy, double px, double pz) {
        double best = Double.MAX_VALUE, y = vy[0];
        for (int i = 0; i + 1 < pts.size(); i++) {
            int[] a = pts.get(i), b = pts.get(i + 1);
            double dx = b[0] - a[0], dz = b[1] - a[1];
            double len2 = dx * dx + dz * dz;
            double t = len2 == 0 ? 0 : ((px - a[0]) * dx + (pz - a[1]) * dz) / len2;
            t = Math.max(0, Math.min(1, t));
            double d = Math.hypot(px - (a[0] + t * dx), pz - (a[1] + t * dz));
            if (d < best) { best = d; y = vy[i] + (vy[i + 1] - vy[i]) * t; }
        }
        return new double[]{best, y};
    }

    static double distToPolyline(List<int[]> pts, double px, double pz) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < pts.size(); i++) {
            int[] a = pts.get(i), b = pts.get(i + 1);
            double dx = b[0] - a[0], dz = b[1] - a[1];
            double len2 = dx * dx + dz * dz;
            double t = len2 == 0 ? 0 : ((px - a[0]) * dx + (pz - a[1]) * dz) / len2;
            t = Math.max(0, Math.min(1, t));
            double cx = a[0] + t * dx, cz = a[1] + t * dz;
            double d = Math.hypot(px - cx, pz - cz);
            if (d < best) best = d;
        }
        return best;
    }

    // ----------------------------------------------------------------- compute

    private double rawHeight(int x, int z) {
        double h = layout.base_y
                + 3.0 * noise(x / 64.0, z / 64.0)
                + 1.4 * noise(x / 23.0 + 50, z / 23.0 - 20)
                + 0.6 * noise(x / 9.0 - 7, z / 9.0 + 3);
        if (layout.relief > 1) {
            // broad hills and dales; only ever raises the ground so lakes and rivers keep their levels
            double big = noise(x / 230.0 + 300, z / 230.0 - 300) * 0.65 + noise(x / 110.0 - 90, z / 110.0 + 40) * 0.35;
            h += (layout.relief - 1) * 4.0 * (big + 1);
        }
        for (RegionLayout.Hill hill : layout.hills) {
            double d = Math.hypot(x - hill.x, z - hill.z);
            if (d < hill.r) {
                double t = 1 - d / hill.r;
                double shape = smooth(t) * (0.85 + 0.15 * noise(x / 17.0 + hill.x, z / 17.0 + hill.z));
                h += hill.h * shape;
            }
        }
        for (RegionLayout.Peak pk : layout.peaks) {
            double dx = x - pk.x, dz = z - pk.z;
            double d = Math.hypot(dx, dz);
            double ang = Math.atan2(dz, dx);
            // ragged outline so the mountain isn't a perfect circle
            double rr = pk.r * (0.82 + 0.18 * noise(Math.cos(ang) * 1.7 + pk.x * 0.01, Math.sin(ang) * 1.7 + pk.z * 0.01));
            if (d < rr) {
                double t = 1 - d / rr;
                // concave flanks steepening to a pointed summit
                double cone = 0.55 * t * t * (3 - 2 * t) + 0.45 * Math.pow(t, 2.4);
                // spurs and gullies radiating from the summit (ridged noise in angle, stretched along the radius)
                double wa = ang + 0.5 * noise(d / 45.0 + pk.x, pk.z * 0.01);   // spurs curve as they run downhill
                double spur = 0.75 * ridged(Math.cos(wa) * 1.15 + pk.x * 0.01 + d / 160.0, Math.sin(wa) * 1.15 + pk.z * 0.01)
                        + 0.25 * ridged(Math.cos(wa) * 2.9 + 40 + d / 90.0, Math.sin(wa) * 2.9 - 40);
                double shape = 1 + pk.crag * (spur - 0.55) * Math.min(1, (1 - t) * 2.2);
                double rough = noise(x / 7.0 + pk.x, z / 7.0 - pk.z) * 3.5 * pk.crag * t;
                double add = pk.h * cone * shape + rough;
                if (pk.crater > 0 && d < pk.crater * 1.25) {
                    double c = 1 - d / (pk.crater * 1.25);
                    add -= pk.crater_depth * smooth(c * 1.4) ;            // the bowl inside the rim
                }
                h += add;
            }
        }
        for (RegionLayout.Range rg : layout.ranges) {
            double d = distToPolyline(rg.points, x, z);
            if (d < rg.width) {
                double t = smooth(1 - d / rg.width);
                h += rg.h * t * (0.55 + 0.6 * ridged(x / rg.scale + 5, z / rg.scale - 5));
            }
        }
        for (RegionLayout.Spire sp : layout.spires) {
            double d = Math.hypot(x - sp.x, z - sp.z) + noise(x / 3.0 + sp.x, z / 3.0 - sp.z) * 1.2
                    + (sp.r > 12 ? noise(x / 11.0 - sp.x, z / 11.0 + sp.z) * sp.r * 0.22 : 0);
            if (d < sp.r) {
                double t = d / sp.r;
                h += sp.h * (1 - t * t * t * t) * (0.9 + 0.1 * noise(x / 4.0, z / 4.0));
            }
        }
        // natural edge ridge, with gaps for roads
        RegionLayout.EdgeRidge er = layout.edge_ridge;
        double e = Math.min(Math.min(half - x, half + x), Math.min(half - z, half + z));
        if (e < er.width) {
            double ridge = er.height * smooth((er.width - e) / er.width)
                    * (0.75 + 0.25 * noise(x / 31.0 + 9, z / 31.0 - 4));
            for (RegionLayout.EdgeRidge.Gap g : er.gaps) {
                double along, distEdge;
                switch (g.edge) {
                    case "west" -> { along = z; distEdge = half + x; }
                    case "east" -> { along = z; distEdge = half - x; }
                    case "north" -> { along = x; distEdge = half + z; }
                    default -> { along = x; distEdge = half - z; }
                }
                if (distEdge <= er.width + 5) {
                    double off = Math.abs(along - g.at);
                    double open = 1 - smooth((off - g.half) / 18.0);
                    ridge *= (1 - open);
                }
            }
            h += ridge;
        }
        return h;
    }

    private void compute() {
        int n = size * size;
        double[] h = new double[n];
        for (int z = -half; z <= half; z++) for (int x = -half; x <= half; x++) h[idx(x, z)] = rawHeight(x, z);

        // zones
        java.util.Arrays.fill(zone, ZONE_NONE);
        for (int z = -half; z <= half; z++) for (int x = -half; x <= half; x++) {
            for (int k = 0; k < layout.zones.size() && k < 127; k++) {
                RegionLayout.Zone zn = layout.zones.get(k);
                double d = Math.hypot(x - zn.x, z - zn.z) + noise(x / 23.0 + k * 31, z / 23.0 - k * 17) * zn.r * 0.12;
                if (d < zn.r) { zone[idx(x, z)] = (byte) k; break; }
            }
        }

        // flats
        double[] pre = h.clone();
        for (RegionLayout.Flat f : layout.flats) {
            double target = f.y != 0 ? f.y : Math.round(pre[idx(Math.max(-half, Math.min(half, f.x)), Math.max(-half, Math.min(half, f.z)))]);
            int R = flatReach(f) + f.blend;
            for (int z = f.z - R; z <= f.z + R; z++) for (int x = f.x - R; x <= f.x + R; x++) {
                if (!inside(x, z)) continue;
                double d = flatDist(f, x, z);
                if (d > f.blend) continue;
                double w = d <= 0 ? 1 : 1 - smooth(d / Math.max(1, f.blend));
                int i = idx(x, z);
                h[i] = h[i] * (1 - w) + target * w;
            }
        }

        // building pads
        for (RegionLayout.Building b : layout.buildings) {
            int padY = (int) Math.round(h[idx(b.x, b.z)]);
            BuildingPlanner.Plan plan = BuildingPlanner.plan(b);
            PlacedBuilding pb = new PlacedBuilding(b, plan, padY);
            buildings.add(pb);
            int margin = b.type.equals("stall") || b.type.equals("well") ? 1 : 2;
            int blend = 5;
            for (int cz = -margin - blend; cz < b.d + margin + blend; cz++) {
                for (int cx = -margin - blend; cx < b.w + margin + blend; cx++) {
                    int[] l = pb.toLocal(cx, cz);
                    if (!inside(l[0], l[1])) continue;
                    int ox = Math.max(0, Math.max(-margin - cx, cx - (b.w - 1 + margin)));
                    int oz = Math.max(0, Math.max(-margin - cz, cz - (b.d - 1 + margin)));
                    double out = Math.max(ox, oz);
                    double w = out == 0 ? 1 : 1 - smooth(out / blend);
                    int i = idx(l[0], l[1]);
                    // the building floor layer (padY) replaces the surface block
                    h[i] = h[i] * (1 - w) + padY * w;
                    if (out == 0) occupied[i] = true;
                    if (cx >= 0 && cz >= 0 && cx < b.w && cz < b.d) surface[i] = SURF_PAD;
                }
            }
        }

        // rivers and ponds (carve)
        for (RegionLayout.River r : layout.rivers) {
            int wy = r.water_y != 0 ? r.water_y : layout.water_y;
            double hw = r.width / 2.0;
            double bank = r.gorge ? 0.6 : 5;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int[] p : r.points) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]); }
            int pad = (int) Math.ceil(hw + bank + 2);
            for (int z = Math.max(-half, minZ - pad); z <= Math.min(half, maxZ + pad); z++) {
                for (int x = Math.max(-half, minX - pad); x <= Math.min(half, maxX + pad); x++) {
                    double wob = noise(x / 13.0 + 3, z / 13.0) * 0.8;
                    double d = distToPolyline(r.points, x, z) + wob;
                    int i = idx(x, z);
                    if (d < hw) {
                        double bed = wy - r.depth * (1 - (d / hw) * (d / hw)) - 0.6;
                        h[i] = Math.min(h[i], bed);
                        water[i] = wy;
                        surface[i] = SURF_BED;
                    } else if (d < hw + bank) {
                        double t = smooth((d - hw) / bank);
                        double target = wy + 1 + t * Math.max(0, h[i] - (wy + 1));
                        if (h[i] > target) h[i] = target;
                        if (d < hw + 2.2 && !r.gorge) surface[i] = SURF_BANK;
                    }
                }
            }
        }
        for (RegionLayout.Pond p : layout.ponds) {
            int wy = p.y != 0 ? p.y : (int) Math.round(h[idx(p.x, p.z)]) - 1;
            byte fl = "lava".equals(p.fluid) ? FLUID_LAVA : FLUID_WATER;
            int reach = p.r + 4 + (p.r > 30 ? (int) (p.r * 0.3) : 0);
            for (int z = p.z - reach; z <= p.z + reach; z++) for (int x = p.x - reach; x <= p.x + reach; x++) {
                if (!inside(x, z)) continue;
                double d = Math.hypot(x - p.x, z - p.z) + noise(x / 5.0, z / 5.0) * 1.3
                        + (p.r > 30 ? noise(x / 45.0 + p.x, z / 45.0 - p.z) * p.r * 0.22 + noise(x / 14.0, z / 14.0 + 9) * p.r * 0.05 : 0);
                int i = idx(x, z);
                if (d < p.r) {
                    h[i] = Math.min(h[i], wy - p.depth * (1 - (d / p.r) * (d / p.r)) - 0.6);
                    water[i] = wy;
                    fluid[i] = fl;
                    surface[i] = SURF_BED;
                } else if (d < p.r + 3 && (p.y == 0 || h[i] < wy + 6)) {   // absolute-level lakes keep their cliffs
                    h[i] = Math.min(h[i], wy + 0.4 + (d - p.r) * 0.5);
                    surface[i] = SURF_BANK;
                }
            }
        }

        // auto lanes: connect each building door to the nearest road
        List<RegionLayout.Road> allRoads = new ArrayList<>(layout.roads);
        for (PlacedBuilding pb : buildings) {
            int[] door = pb.plan.anchors.get("door_out");
            if (door == null || pb.def.type.equals("well")) continue;
            int[] dl = pb.toLocal(door[0], door[2]);
            double best = Double.MAX_VALUE; int bx = 0, bz = 0;
            RegionLayout.Road bestRoad = null;
            for (RegionLayout.Road r : layout.roads) {
                for (int s = 0; s + 1 < r.points.size(); s++) {
                    int[] a = r.points.get(s), b2 = r.points.get(s + 1);
                    int steps = (int) Math.max(1, Math.hypot(b2[0] - a[0], b2[1] - a[1]));
                    for (int k = 0; k <= steps; k++) {
                        double t = k / (double) steps;
                        int px = (int) Math.round(a[0] + (b2[0] - a[0]) * t), pz = (int) Math.round(a[1] + (b2[1] - a[1]) * t);
                        double dd = Math.hypot(px - dl[0], pz - dl[1]);
                        if (dd < best) { best = dd; bx = px; bz = pz; bestRoad = r; }
                    }
                }
            }
            if (best < 60 && best > 1) {
                RegionLayout.Road lane = new RegionLayout.Road();
                lane.id = pb.def.id + "_lane";
                lane.width = 2;
                lane.surface = "lane";
                // a short straight run out of the door, then toward the road
                int[] step = pb.toLocal(door[0], door[2] + 2);
                if (layout.road_steps) {
                    // graded from the doorstep down/up to the road's own height at the junction
                    lane.graded = true;
                    int junctionY = (int) Math.round(h[idx(bx, bz)]);
                    if (bestRoad != null && bestRoad.graded) {
                        double[] vy = new double[bestRoad.points.size()];
                        for (int k = 0; k < vy.length; k++) {
                            int[] p = bestRoad.points.get(k);
                            vy[k] = p.length >= 3 ? p[2] : Math.round(h[idx(Math.max(-half, Math.min(half, p[0])), Math.max(-half, Math.min(half, p[1])))]);
                        }
                        junctionY = (int) Math.round(gradedAt(bestRoad.points, vy, bx, bz)[1]);
                    }
                    lane.points.add(new int[]{dl[0], dl[1], pb.padY});
                    lane.points.add(new int[]{step[0], step[1], pb.padY});
                    lane.points.add(new int[]{bx, bz, junctionY});
                } else {
                    lane.points.add(new int[]{dl[0], dl[1]});
                    lane.points.add(new int[]{step[0], step[1]});
                    lane.points.add(new int[]{bx, bz});
                }
                allRoads.add(lane);
            }
        }

        // roads: smooth along corridor, mark surface, detect bridges
        double[] carved = h.clone();
        for (RegionLayout.Road road : allRoads) {
            double hw = road.width / 2.0;
            byte surf = switch (road.surface) {
                case "lane" -> SURF_LANE;
                case "cobble" -> SURF_COBBLE;
                default -> SURF_ROAD;
            };
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int[] p : road.points) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]); }
            int pad = (int) Math.ceil(hw + 4);
            double[] vy = null;
            if (road.graded) {
                vy = new double[road.points.size()];
                for (int k = 0; k < vy.length; k++) {
                    int[] p = road.points.get(k);
                    vy[k] = p.length >= 3 ? p[2] : Math.round(carved[idx(Math.max(-half, Math.min(half, p[0])), Math.max(-half, Math.min(half, p[1])))]);
                }
            }
            for (int z = Math.max(-half, minZ - pad); z <= Math.min(half, maxZ + pad); z++) {
                for (int x = Math.max(-half, minX - pad); x <= Math.min(half, maxX + pad); x++) {
                    double gradedY = 0;
                    double d;
                    if (vy != null) { double[] g = gradedAt(road.points, vy, x, z); d = g[0]; gradedY = g[1]; }
                    else d = distToPolyline(road.points, x, z);
                    if (d > hw + 3) continue;
                    int i = idx(x, z);
                    if (surface[i] == SURF_PAD) continue;
                    // box-blur the carved terrain to soften the road bed
                    double sum = 0; int cnt = 0;
                    for (int dz = -4; dz <= 4; dz++) for (int dx = -4; dx <= 4; dx++) {
                        int xx = x + dx, zz = z + dz;
                        if (!inside(xx, zz)) continue;
                        int j = idx(xx, zz);
                        double v = water[j] != Integer.MIN_VALUE ? Math.max(carved[j], water[j] + 1) : carved[j];
                        sum += v; cnt++;
                    }
                    double smoothH = vy != null ? gradedY : sum / cnt;
                    double w = d <= hw ? 1 : 1 - smooth((d - hw) / 3);
                    if (d <= hw + 0.5) roadMask[i] = true;
                    if (water[i] != Integer.MIN_VALUE) {
                        if (d <= hw + 0.5) {
                            int deck = water[i] + 2;
                            bridgeStyle[i] = switch (road.bridge) {
                                case "stone" -> BRIDGE_STONE;
                                case "wood" -> BRIDGE_WOOD;
                                case "rope" -> BRIDGE_ROPE;
                                default -> BRIDGE_AUTO;
                            };
                            if (road.broken_bridge) {
                                // only the first stretch of deck survives near the western bank
                                bridge[i] = -deck; // negative = broken bridge cell
                            } else {
                                bridge[i] = deck;
                            }
                        }
                        continue;
                    }
                    h[i] = h[i] * (1 - w) + smoothH * w;
                    if (d <= hw && surface[i] != SURF_BANK) surface[i] = surf;
                    else if (d <= hw && surface[i] == SURF_BANK) surface[i] = surf;
                }
            }
        }

        // fields
        for (RegionLayout.Field f : layout.fields) {
            for (int z = Math.min(f.z1, f.z2); z <= Math.max(f.z1, f.z2); z++)
                for (int x = Math.min(f.x1, f.x2); x <= Math.max(f.x1, f.x2); x++) {
                    if (!inside(x, z)) continue;
                    int i = idx(x, z);
                    if (surface[i] == SURF_GRASS) surface[i] = SURF_FIELD;
                }
        }

        int cap = layout.max_height;
        for (int i = 0; i < n; i++) height[i] = (int) Math.round(Math.min(cap, h[i]));
        // water must sit above bed
        for (int i = 0; i < n; i++) if (water[i] != Integer.MIN_VALUE && height[i] >= water[i]) height[i] = water[i] - 1;
        if (layout.road_steps) limitRoadGrade();   // opt-in: existing worlds keep the exact heights they were built with

        // bridge decks: each connected bridge span sits at the height of the road on its banks
        boolean[] seen = new boolean[n];
        for (int start = 0; start < n; start++) {
            if (bridge[start] == 0 || seen[start]) continue;
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            List<Integer> span = new ArrayList<>();
            queue.add(start); seen[start] = true;
            double sum = 0; int cnt = 0; int maxWater = Integer.MIN_VALUE;
            while (!queue.isEmpty()) {
                int c = queue.poll();
                span.add(c);
                maxWater = Math.max(maxWater, water[c]);
                int cx = c % size - half, cz = c / size - half;
                for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                    int xx = cx + dx, zz = cz + dz;
                    if (!inside(xx, zz)) continue;
                    int j = idx(xx, zz);
                    if (bridge[j] != 0) {
                        if (!seen[j] && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) { seen[j] = true; queue.add(j); }
                    } else if (roadMask[j] && water[j] == Integer.MIN_VALUE) {
                        sum += height[j]; cnt++;
                    }
                }
            }
            int deck = Math.max(maxWater + 2, cnt > 0 ? (int) Math.round(sum / cnt) : maxWater + 2);
            for (int c : span) bridge[c] = bridge[c] < 0 ? -deck : deck;
        }

        // retaining walls along walled terraces
        for (RegionLayout.Flat f : layout.flats) {
            if (!f.wall) continue;
            int R = flatReach(f) + 3;
            for (int z = f.z - R; z <= f.z + R; z++) for (int x = f.x - R; x <= f.x + R; x++) {
                if (x <= -half || z <= -half || x >= half || z >= half) continue;
                double d = flatDist(f, x, z);
                if (d < -2 || d > 3) continue;
                int i = idx(x, z);
                if (roadMask[i] || water[i] != Integer.MIN_VALUE || surface[i] == SURF_PAD || occupied[i]) continue;
                int c = height[i];
                int lowest = Math.min(Math.min(height[idx(x + 1, z)], height[idx(x - 1, z)]), Math.min(height[idx(x, z + 1)], height[idx(x, z - 1)]));
                // on the terrace edge: a built retaining wall; outside it (dug into the slope): a natural rock cut
                if (c - lowest >= 2) surface[i] = d <= 0.5 ? SURF_WALL : SURF_ROCK;
            }
        }

        // steep rock
        for (int z = -half + 1; z < half; z++) for (int x = -half + 1; x < half; x++) {
            int i = idx(x, z);
            if (surface[i] != SURF_GRASS) continue;
            int c = height[i];
            int m = Math.max(Math.max(Math.abs(c - height[idx(x + 1, z)]), Math.abs(c - height[idx(x - 1, z)])),
                    Math.max(Math.abs(c - height[idx(x, z + 1)]), Math.abs(c - height[idx(x, z - 1)])));
            if (m >= 3) surface[i] = SURF_ROCK;
        }

        // anchors: layout ground anchors
        for (Map.Entry<String, int[]> e : layout.anchors.entrySet()) {
            int[] a = e.getValue();
            if (!inside(a[0], a[2])) { warnings.add("anchor outside region: " + e.getKey()); continue; }
            if (a.length >= 4 && a[3] == 1) { // absolute Y (caves, interiors)
                anchors.put(e.getKey(), new int[]{a[0], a[1], a[2]});
                continue;
            }
            int i = idx(a[0], a[2]);
            int y = bridge[i] > 0 ? bridge[i] + 1 : Math.max(height[i], water[i] == Integer.MIN_VALUE ? height[i] : water[i]) + 1;
            anchors.put(e.getKey(), new int[]{a[0], y + a[1], a[2]});
        }
        // feature anchors: <featureId>.<anchor>
        for (RegionLayout.Feature fd : layout.features) {
            PlacedFeature pf = new PlacedFeature(fd);
            features.add(pf);
            if (fd.y == 0) {
                for (BuildingPlanner.Placed b : pf.plan.blocks) {
                    for (int mz = -1; mz <= 1; mz++) for (int mx = -1; mx <= 1; mx++) {
                        int x = fd.x + b.x() + mx, z = fd.z + b.z() + mz;
                        if (inside(x, z)) occupied[idx(x, z)] = true;
                    }
                }
                if (fd.type.equals("lone_oak")) {
                    for (int mz = -3; mz <= 3; mz++) for (int mx = -3; mx <= 3; mx++)
                        if (inside(fd.x + mx, fd.z + mz)) occupied[idx(fd.x + mx, fd.z + mz)] = true;
                }
            }
            for (Map.Entry<String, int[]> e : pf.plan.anchors.entrySet()) {
                int[] a = e.getValue();
                anchors.put(fd.id + "." + e.getKey(), featurePos(pf, a[0], a[1], a[2]));
            }
        }
        // building anchors: <buildingId>.<anchor>
        for (PlacedBuilding pb : buildings) {
            for (Map.Entry<String, int[]> e : pb.plan.anchors.entrySet()) {
                anchors.put(pb.def.id + "." + e.getKey(), pb.toLocal(e.getValue()));
            }
        }
    }

    /**
     * Roads never rise more than one block between neighbouring road cells: where the terrain is steeper,
     * the road bed is cut down into the slope (repeated until stable). Bridge and water cells are left alone.
     */
    private void limitRoadGrade() {
        // only road cells matter: collect them once (the showcase map has 4 million columns)
        int[] cells = new int[1024];
        boolean[] fixed = new boolean[1024];
        int count = 0;
        for (int i = 0; i < roadMask.length; i++) {
            if (!roadMask[i] || bridge[i] != 0 || water[i] != Integer.MIN_VALUE || surface[i] == SURF_PAD) continue;
            if (count == cells.length) { cells = java.util.Arrays.copyOf(cells, count * 2); fixed = java.util.Arrays.copyOf(fixed, count * 2); }
            int x = i % size - half, z = i / size - half;
            boolean onTerrace = false;
            for (RegionLayout.Flat f : layout.flats) if (f.y != 0 && flatDist(f, x, z) <= -1) { onTerrace = true; break; }
            fixed[count] = onTerrace;           // streets on terraces keep the terrace height
            cells[count++] = i;
        }
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean changed = true;
        for (int pass = 0; pass < 1500 && changed; pass++) {
            changed = false;
            for (int c = 0; c < count; c++) {
                if (fixed[c]) continue;
                int i = cells[c];
                int x = i % size - half, z = i / size - half;
                int lo = Integer.MIN_VALUE, hi = Integer.MAX_VALUE;
                for (int[] d : nb) {
                    int xx = x + d[0], zz = z + d[1];
                    if (!inside(xx, zz)) continue;
                    int j = idx(xx, zz);
                    if (!roadMask[j] || bridge[j] != 0 || water[j] != Integer.MIN_VALUE) continue;
                    lo = Math.max(lo, height[j] - 1);
                    hi = Math.min(hi, height[j] + 1);
                }
                if (lo == Integer.MIN_VALUE) continue;
                // the road bed is raised (embankment) or cut down until no step is more than one block
                int want = lo > hi ? (lo + hi + 1) / 2 : Math.max(lo, Math.min(hi, height[i]));
                if (want != height[i]) { height[i] = want; changed = true; }
            }
        }
    }

    /** Signed distance from a flat's edge: negative inside, 0 on the edge. */
    public static double flatDist(RegionLayout.Flat f, double x, double z) {
        if ("rect".equals(f.shape)) return Math.max(Math.abs(x - f.x) - f.w / 2.0, Math.abs(z - f.z) - f.d / 2.0);
        return Math.hypot(x - f.x, z - f.z) - f.r;
    }

    static int flatReach(RegionLayout.Flat f) {
        return "rect".equals(f.shape) ? (int) Math.ceil(Math.max(f.w, f.d) / 2.0) + 1 : f.r;
    }

    public String paletteAt(int x, int z) {
        if (!inside(x, z)) return "alpine";
        byte k = zone[idx(x, z)];
        return k == ZONE_NONE ? "" : layout.zones.get(k).palette;
    }

    /** Largest height step between neighbouring dry road cells (for tests): 1 means fully walkable. */
    public int maxRoadStep() {
        int worst = 0;
        for (int z = -half; z < half; z++) for (int x = -half; x < half; x++) {
            int i = idx(x, z);
            if (!roadMask[i] || bridge[i] != 0 || water[i] != Integer.MIN_VALUE || surface[i] == SURF_PAD) continue;
            for (int j : new int[]{idx(x + 1, z), idx(x, z + 1)}) {
                if (!roadMask[j] || bridge[j] != 0 || water[j] != Integer.MIN_VALUE || surface[j] == SURF_PAD) continue;
                worst = Math.max(worst, Math.abs(height[i] - height[j]));
            }
        }
        return worst;
    }

    /** True if a column is free for vegetation. */
    public boolean isOpenGround(int x, int z) {
        if (!inside(x, z)) return false;
        int i = idx(x, z);
        return !occupied[i] && water[i] == Integer.MIN_VALUE && bridge[i] == 0
                && (surface[i] == SURF_GRASS || surface[i] == SURF_ROCK);
    }

    public int[] anchor(String name) { return anchors.get(name); }
}
