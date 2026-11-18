package dev.hollowatlas.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Edge of the playable area. Ridges, cliffs and the gorge shape the boundary physically; this service
 * adds a gentle note near the edge and, if a player still slips past it (or into a region the story
 * hasn't opened yet), returns them to their last safe spot. The vanilla world border is kept as a
 * last-resort hard stop around all authored regions.
 */
public final class BoundaryService {
    private static final Map<UUID, Long> LAST_NOTE = new HashMap<>();
    private static final Map<UUID, Vec3d> LAST_SAFE = new HashMap<>();

    private BoundaryService() {}

    /** Size the vanilla border to the bounding square of all authored regions. */
    public static void applyWorldBorder(MinecraftServer server) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (RegionModel m : LocationIndex.models()) {
            int[] o = m.layout.origin;
            minX = Math.min(minX, o[0] - m.half); maxX = Math.max(maxX, o[0] + m.half);
            minZ = Math.min(minZ, o[1] - m.half); maxZ = Math.max(maxZ, o[1] + m.half);
        }
        ServerWorld w = server.getOverworld();
        double size = Math.max(maxX - minX, maxZ - minZ) + 8;
        w.getWorldBorder().setCenter((minX + maxX) / 2.0 + 0.5, (minZ + maxZ) / 2.0 + 0.5);
        w.getWorldBorder().setSize(size);
        w.getWorldBorder().setWarningBlocks(8);
        w.getWorldBorder().setDamagePerBlock(0);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 10 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (ProtectionService.isBuilder(p) || p.isSpectator()) continue;
            double edge = LocationIndex.distanceToEdge(server, p.getX(), p.getZ());
            if (edge < -1.5) {
                Vec3d safe = LAST_SAFE.get(p.getUuid());
                if (safe != null) {
                    p.teleport(p.getServerWorld(), safe.x, safe.y, safe.z, p.getYaw(), p.getPitch());
                    p.sendMessage(Text.translatable("message.hollowatlas.boundary_return").formatted(Formatting.GRAY, Formatting.ITALIC), true);
                }
                continue;
            }
            if (edge > 3 && p.isOnGround()) LAST_SAFE.put(p.getUuid(), p.getPos());
            if (edge > 14) continue;
            long now = server.getTicks();
            Long last = LAST_NOTE.get(p.getUuid());
            if (last != null && now - last < 20 * 20) continue;
            LAST_NOTE.put(p.getUuid(), now);
            p.sendMessage(Text.translatable("message.hollowatlas.boundary").formatted(Formatting.GRAY, Formatting.ITALIC), true);
        }
    }
}
