package dev.hollowatlas.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Named places that the Atlas charts once the player has physically been there. The Atlas never
 * shows undiscovered places.
 */
public final class SiteService {
    public static final Map<String, Bundle> BUNDLES = ContentManager.register("sites", Bundle.class);

    public static final class Bundle implements ContentManager.Validatable {
        public String id;
        public List<Site> sites = new ArrayList<>();
        @Override public String id() { return id; }
        @Override public void validate(String file, List<String> problems) {
            for (Site s : sites) if (!LocationIndex.has(s.anchor)) problems.add(file + ": site " + s.id + " unknown anchor " + s.anchor);
        }
    }

    public static final class Site { public String id, name, anchor, kind = "place"; public double radius = 12; }

    private SiteService() {}

    /** Forces class init so the content kind is registered before the first data reload. */
    public static void init() { }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 17) return;
        GameState gs = GameState.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) continue;
            PlayerProfile p = gs.profile(player.getUuid());
            for (Bundle b : BUNDLES.values()) for (Site s : b.sites) {
                if (p.discovered.contains(s.id)) continue;
                BlockPos a = LocationIndex.pos(s.anchor);
                if (player.squaredDistanceTo(a.toCenterPos()) > s.radius * s.radius) continue;
                p.discovered.add(s.id);
                gs.markDirty();
                player.sendMessage(Text.translatable("message.hollowatlas.discovered", s.name).formatted(Formatting.AQUA), false);
                player.playSoundToPlayer(SoundEvents.UI_TOAST_IN, SoundCategory.PLAYERS, 0.9f, 1.1f);
                QuestEngine.event(player, "discover", s.id);
                QuestEngine.sync(player);
            }
        }
    }

    public static JsonArray journal(ServerPlayerEntity player) {
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        JsonArray arr = new JsonArray();
        for (Bundle b : BUNDLES.values()) for (Site s : b.sites) {
            if (!p.discovered.contains(s.id)) continue;
            BlockPos a = LocationIndex.pos(s.anchor);
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id);
            o.addProperty("name", s.name);
            o.addProperty("kind", s.kind);
            o.addProperty("x", a.getX());
            o.addProperty("z", a.getZ());
            arr.add(o);
        }
        return arr;
    }
}
