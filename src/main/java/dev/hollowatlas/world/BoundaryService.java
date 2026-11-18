package dev.hollowatlas.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Soft edge of the playable region: ridges and the gorge shape the boundary physically; the vanilla
 * world border is the hard stop. As players approach it they get a gentle in-world note instead of
 * an unexplained wall.
 */
public final class BoundaryService {
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private BoundaryService() {}

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (ProtectionService.isBuilder(p)) continue;
            double edge = LocationIndex.distanceToEdge(p.getX(), p.getZ());
            if (edge > 14) continue;
            long now = server.getTicks();
            Long last = LAST.get(p.getUuid());
            if (last != null && now - last < 20 * 20) continue;
            LAST.put(p.getUuid(), now);
            p.sendMessage(Text.translatable("message.hollowatlas.boundary").formatted(Formatting.GRAY, Formatting.ITALIC), true);
        }
    }
}
