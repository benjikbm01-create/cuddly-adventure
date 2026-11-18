package dev.hollowatlas.world;

import dev.hollowatlas.HollowAtlas;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders the generated region from the REAL world blocks into a top-down PNG (vanilla map colours
 * with relief shading). Used for QA and as the source of the in-game Atlas overview texture.
 */
public final class MapRenderer {
    private MapRenderer() {}

    public static Path render(ServerWorld world, Path out, int scale) throws Exception {
        RegionModel m = LocationIndex.model();
        int ox = m.layout.origin[0], oz = m.layout.origin[1], h = m.half;
        int size = (2 * h + 1) / scale;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int[] prevRow = new int[size];
        for (int pz = 0; pz < size; pz++) {
            for (int px = 0; px < size; px++) {
                int x = ox - h + px * scale, z = oz - h + pz * scale;
                world.getChunk(x >> 4, z >> 4); // force-load so the heightmap is real
                int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                pos.set(x, top, z);
                BlockState st = world.getBlockState(pos);
                int waterDepth = 0;
                while (st.getFluidState().isStill() && !st.getFluidState().isEmpty() && waterDepth < 10) {
                    waterDepth++;
                    pos.move(0, -1, 0);
                    st = world.getBlockState(pos);
                }
                if (waterDepth > 0) { pos.set(x, top, z); st = world.getBlockState(pos); }
                MapColor mc = st.getMapColor(world, pos);
                double shade;
                if (waterDepth > 0) shade = waterDepth > 4 ? 0.70 : waterDepth > 2 ? 0.86 : 1.0;
                else {
                    int north = pz == 0 ? top : prevRow[px];
                    shade = top > north ? 1.0 : top < north ? 0.71 : 0.86;
                }
                prevRow[px] = top;
                int c = mc.color;
                int r = (int) (((c >> 16) & 255) * shade), g = (int) (((c >> 8) & 255) * shade), b = (int) ((c & 255) * shade);
                img.setRGB(px, pz, (r << 16) | (g << 8) | b);
            }
        }
        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
        HollowAtlas.LOGGER.info("Rendered region map to {}", out);
        return out;
    }
}
