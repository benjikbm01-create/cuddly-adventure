package dev.hollowatlas.world;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegionModelTest {

    /** Every region file shipped with the mod (story and developer test). */
    private static java.util.List<RegionModel> ALL;

    static synchronized java.util.List<RegionModel> all() {
        if (ALL != null) return ALL;
        java.util.List<RegionModel> out = new java.util.ArrayList<>();
        for (String id : java.util.List.of("westreach_brindlecross", "devtest_grounds", "devtest_heights", "boreal_sableforge", "showcase_wilds")) {
            if (RegionLayout.class.getResource("/data/hollowatlas/region/" + id + ".json") != null) {
                long t0 = System.currentTimeMillis();
                out.add(new RegionModel(RegionLayout.loadBuiltin(id)));
                System.out.println("model " + id + " in " + (System.currentTimeMillis() - t0) + " ms");
            }
        }
        ALL = out;
        return out;
    }

    static RegionModel model() {
        return new RegionModel(RegionLayout.loadBuiltin("westreach_brindlecross"));
    }

    @Test
    void layoutHasNoWarnings() {
        for (RegionModel m : all()) layoutHasNoWarnings(m);
    }

    private void layoutHasNoWarnings(RegionModel m) {
        assertTrue(m.warnings.isEmpty(), m.layout.id + " warnings: " + m.warnings);
    }

    @Test
    void buildingsDoNotOverlapAndStayDry() {
        for (RegionModel m : all()) buildingsDoNotOverlapAndStayDry(m);
    }

    private void buildingsDoNotOverlapAndStayDry(RegionModel m) {
        List<String> problems = new ArrayList<>();
        int[] owner = new int[m.size * m.size];
        for (int bi = 0; bi < m.buildings.size(); bi++) {
            RegionModel.PlacedBuilding pb = m.buildings.get(bi);
            for (int cz = 0; cz < pb.def.d; cz++) for (int cx = 0; cx < pb.def.w; cx++) {
                int[] l = pb.toLocal(cx, cz);
                assertTrue(m.inside(l[0], l[1]), pb.def.id + " outside region");
                int i = m.idx(l[0], l[1]);
                if (owner[i] != 0 && owner[i] != bi + 1) {
                    problems.add(pb.def.id + " overlaps " + m.buildings.get(owner[i] - 1).def.id);
                }
                owner[i] = bi + 1;
                if (m.water[i] != Integer.MIN_VALUE) problems.add(pb.def.id + " footprint in water at " + l[0] + "," + l[1]);
            }
        }
        assertTrue(problems.isEmpty(), m.layout.id + ": " + String.join("\n", problems.stream().distinct().toList()));
    }

    @Test
    void doorsOpenOntoFreeGround() {
        for (RegionModel m : all()) doorsOpenOntoFreeGround(m);
    }

    private void doorsOpenOntoFreeGround(RegionModel m) {
        List<String> problems = new ArrayList<>();
        for (RegionModel.PlacedBuilding pb : m.buildings) {
            int[] door = m.anchor(pb.def.id + ".door_out");
            if (door == null) { problems.add(pb.def.id + " has no door_out"); continue; }
            int i = m.idx(door[0], door[2]);
            if (m.water[i] != Integer.MIN_VALUE) problems.add(pb.def.id + " door opens into water");
            for (RegionModel.PlacedBuilding other : m.buildings) {
                if (other == pb) continue;
                for (int cz = 0; cz < other.def.d; cz++) for (int cx = 0; cx < other.def.w; cx++) {
                    int[] l = other.toLocal(cx, cz);
                    if (l[0] == door[0] && l[1] == door[2]) problems.add(pb.def.id + " door blocked by " + other.def.id);
                }
            }
            int ground = m.heightAt(door[0], door[2]);
            if (Math.abs(ground + 1 - door[1]) > 1) problems.add(pb.def.id + " door step mismatch ground=" + ground + " door=" + door[1]);
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void groundAnchorsAreOnLand() {
        for (RegionModel m : all()) groundAnchorsAreOnLand(m);
    }

    private void groundAnchorsAreOnLand(RegionModel m) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, int[]> e : m.layout.anchors.entrySet()) {
            int[] a = e.getValue();
            if (a.length >= 4) continue;
            if (m.water[m.idx(a[0], a[2])] != Integer.MIN_VALUE && m.bridge[m.idx(a[0], a[2])] == 0) problems.add(e.getKey() + " is in water");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void steppedRoadsAreWalkable() {
        for (RegionModel m : all()) {
            if (!m.layout.road_steps) continue;
            assertTrue(m.maxRoadStep() <= 1, m.layout.id + ": a road rises more than one block between neighbouring cells (" + m.maxRoadStep() + ")");
        }
    }

    /** Shaded relief previews of every region: build/region_preview_<id>.png (for authoring; not shipped). */
    @Test
    void renderReliefPreviews() throws Exception {
        for (RegionModel m : all()) {
            BufferedImage img = new BufferedImage(m.size, m.size, BufferedImage.TYPE_INT_RGB);
            for (int z = -m.half; z <= m.half; z++) for (int x = -m.half; x <= m.half; x++) {
                int i = m.idx(x, z);
                int h = m.height[i];
                int west = m.heightAt(x - 1, z), north = m.heightAt(x, z - 1);
                double shade = Math.max(0.45, Math.min(1.35, 1 + ((h - west) + (h - north)) * 0.12));
                int base;
                if (m.bridge[i] != 0) base = m.bridgeStyle[i] == RegionModel.BRIDGE_ROPE ? 0xd09050 : 0x8a6a3a;
                else if (m.water[i] != Integer.MIN_VALUE) base = 0x3a6fd8;
                else if (m.occupied[i]) base = 0xb04030;
                else base = switch (m.surface[i]) {
                    case RegionModel.SURF_ROAD, RegionModel.SURF_LANE -> 0xd8c090;
                    case RegionModel.SURF_COBBLE -> 0xa0a0a0;
                    case RegionModel.SURF_WALL -> 0x5a5a6a;
                    case RegionModel.SURF_ROCK -> m.layout.snow_line > 0 && h >= m.layout.snow_line ? 0xe8eef4 : 0x8a8478;
                    case RegionModel.SURF_BANK -> 0xd8cf9a;
                    case RegionModel.SURF_FIELD -> 0x9a8a30;
                    default -> m.layout.snow_line > 0 && h >= m.layout.snow_line ? 0xf4f8fc
                            : h > 100 ? 0x6a8a50 : 0x5a9a40;
                };
                int r = (int) Math.min(255, ((base >> 16) & 255) * shade), g = (int) Math.min(255, ((base >> 8) & 255) * shade), b = (int) Math.min(255, (base & 255) * shade);
                img.setRGB(x + m.half, z + m.half, r << 16 | g << 8 | b);
            }
            for (Map.Entry<String, int[]> e : m.anchors.entrySet()) {
                int[] a = e.getValue();
                if (m.inside(a[0], a[2])) img.setRGB(a[0] + m.half, a[2] + m.half, 0xffff00);
            }
            File out = new File(System.getProperty("hollowatlas.previewDir", "build"), "region_preview_" + m.layout.id + ".png");
            out.getParentFile().mkdirs();
            ImageIO.write(img, "png", out);
            assertTrue(out.exists());
        }
    }

    @Test
    void renderPreview() throws Exception {
        RegionModel m = model();
        BufferedImage img = new BufferedImage(m.size, m.size, BufferedImage.TYPE_INT_RGB);
        for (int z = -m.half; z <= m.half; z++) for (int x = -m.half; x <= m.half; x++) {
            int i = m.idx(x, z);
            int h = m.height[i];
            int rgb;
            if (m.bridge[i] > 0) rgb = 0x8a6a3a;
            else if (m.bridge[i] < 0) rgb = 0x5a3a2a;
            else if (m.water[i] != Integer.MIN_VALUE) rgb = 0x3a6fd8;
            else if (m.occupied[i]) rgb = m.surface[i] == RegionModel.SURF_PAD ? 0xb04030 : 0x806050;
            else rgb = switch (m.surface[i]) {
                case RegionModel.SURF_ROAD -> 0xc8b080;
                case RegionModel.SURF_LANE -> 0xb8a070;
                case RegionModel.SURF_COBBLE -> 0x909090;
                case RegionModel.SURF_BANK -> 0xd8cf9a;
                case RegionModel.SURF_ROCK -> 0x8a8a8a;
                case RegionModel.SURF_FIELD -> 0x9a8a30;
                default -> {
                    int g = Math.max(0, Math.min(255, 90 + (h - 60) * 4));
                    yield (g / 3) << 16 | g << 8 | (g / 4);
                }
            };
            img.setRGB(x + m.half, z + m.half, rgb);
        }
        for (Map.Entry<String, int[]> e : m.anchors.entrySet()) {
            int[] a = e.getValue();
            if (m.inside(a[0], a[2])) img.setRGB(a[0] + m.half, a[2] + m.half, 0xffff00);
        }
        File out = new File(System.getProperty("hollowatlas.previewDir", "build"), "region_preview.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        assertTrue(out.exists());
    }
}
