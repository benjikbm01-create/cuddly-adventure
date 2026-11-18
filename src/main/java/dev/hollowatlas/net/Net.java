package dev.hollowatlas.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hollowatlas.HollowAtlas;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Typed message channel between client screens and the server-authoritative game state.
 * Each direction uses one payload carrying a message kind and a JSON body. Every client message is
 * validated by its server handler (session ids, ownership, distance, costs) before touching state.
 */
public final class Net {
    private static final int MAX = 1 << 18;

    public record S2C(String kind, String json) implements CustomPayload {
        public static final Id<S2C> ID = new Id<>(HollowAtlas.id("s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(
                PacketCodecs.string(64), S2C::kind, PacketCodecs.string(MAX), S2C::json, S2C::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record C2S(String kind, String json) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(HollowAtlas.id("c2s"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(
                PacketCodecs.string(64), C2S::kind, PacketCodecs.string(8192), C2S::json, C2S::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private static final Map<String, BiConsumer<ServerPlayerEntity, JsonObject>> HANDLERS = new HashMap<>();

    private Net() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(S2C.ID, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            BiConsumer<ServerPlayerEntity, JsonObject> h = HANDLERS.get(payload.kind());
            if (h == null) return;
            JsonObject body;
            try {
                body = JsonParser.parseString(payload.json()).getAsJsonObject();
            } catch (Exception e) {
                HollowAtlas.LOGGER.warn("Malformed {} message from {}", payload.kind(), player.getName().getString());
                return;
            }
            ctx.server().execute(() -> {
                try {
                    h.accept(player, body);
                } catch (Exception e) {
                    HollowAtlas.LOGGER.error("Handler {} failed", payload.kind(), e);
                }
            });
        });
    }

    public static void on(String kind, BiConsumer<ServerPlayerEntity, JsonObject> handler) { HANDLERS.put(kind, handler); }

    public static void send(ServerPlayerEntity player, String kind, JsonObject body) {
        if (ServerPlayNetworking.canSend(player, S2C.ID)) ServerPlayNetworking.send(player, new S2C(kind, body.toString()));
    }
}
