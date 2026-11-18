package dev.hollowatlas.npc;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BiConsumer;

/**
 * Entry point for a player right-clicking a resident. The dialogue system installs its handler here;
 * until then residents answer with a greeting bark.
 */
public final class NpcInteractions {
    private static BiConsumer<ServerPlayerEntity, NpcEntity> handler = (player, npc) -> NpcDirector.bark(npc, "greet");

    private NpcInteractions() {}

    public static void setHandler(BiConsumer<ServerPlayerEntity, NpcEntity> h) { handler = h; }

    public static void onInteract(ServerPlayerEntity player, NpcEntity npc) {
        npc.getLookControl().lookAt(player, 30f, 30f);
        if (npc.npcBrain() != null) npc.npcBrain().pause(60);
        handler.accept(player, npc);
    }
}
