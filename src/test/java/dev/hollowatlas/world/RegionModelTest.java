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
    static java.util.List<RegionModel> all() {
        java.util.List<RegionModel> out = new java.util.ArrayList<>();
        for (String id : java.util.List.of("westreach_brindlecross", "devtest_grounds", "boreal_sableforge")) {
            if (RegionLayout.class.getResource("/data/hollowatlas/region/" + id + ".json") != null)
                out.add(new RegionModel(RegionLayout.loadBuiltin(id)));
        }
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
