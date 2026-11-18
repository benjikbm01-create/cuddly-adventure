package dev.hollowatlas.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hollowatlas.client.ui.CraftScreen;
import dev.hollowatlas.client.ui.DialogueScreen;
import dev.hollowatlas.client.ui.JournalScreen;
import dev.hollowatlas.client.ui.ShopScreen;
import dev.hollowatlas.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side mirror of what the server last told us. Screens and HUD read from here; they never
 * compute game state themselves.
 */
public final class ClientState {
    public static JsonObject hud = new JsonObject();
    public static JsonObject journal = new JsonObject();
    public static String hintKey = null;
    public static long hintUntil = 0;
    public static long lastHudAt = 0;

    private ClientState() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Net.S2C.ID, (payload, ctx) -> ctx.client().execute(() -> handle(payload.kind(), payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { hud = new JsonObject(); journal = new JsonObject(); });
    }

    public static void send(String kind, JsonObject body) {
        if (ClientPlayNetworking.canSend(Net.C2S.ID)) ClientPlayNetworking.send(new Net.C2S(kind, body.toString()));
    }

    public static void send(String kind) { send(kind, new JsonObject()); }

    private static void handle(String kind, String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (kind) {
            case "hud" -> { hud = o; lastHudAt = System.currentTimeMillis(); }
            case "journal" -> {
                journal = o;
                if (mc.currentScreen instanceof JournalScreen js) js.refresh();
            }
            case "dialogue" -> {
                if (o.has("close")) {
                    if (mc.currentScreen instanceof DialogueScreen) mc.setScreen(null);
                } else if (mc.currentScreen instanceof DialogueScreen ds) {
                    ds.show(o);
                } else {
                    mc.setScreen(new DialogueScreen(o));
                }
            }
            case "shop" -> {
                if (mc.currentScreen instanceof ShopScreen s) s.update(o); else mc.setScreen(new ShopScreen(o));
            }
            case "craft" -> {
                if (mc.currentScreen instanceof CraftScreen s) s.update(o); else mc.setScreen(new CraftScreen(o));
            }
            case "dev_info" -> {
                dev.hollowatlas.client.ui.DevMenuScreen.update(o);
                if (mc.currentScreen instanceof dev.hollowatlas.client.ui.DevMenuScreen d) d.rebuild();
            }
            case "hint" -> { hintKey = o.get("key").getAsString(); hintUntil = System.currentTimeMillis() + 9000; }
            default -> { }
        }
    }

    public static float f(JsonObject o, String k, float def) { return o != null && o.has(k) ? o.get(k).getAsFloat() : def; }
    public static int i(JsonObject o, String k, int def) { return o != null && o.has(k) ? o.get(k).getAsInt() : def; }
    public static String s(JsonObject o, String k, String def) { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def; }
    public static boolean b(JsonObject o, String k) { return o != null && o.has(k) && o.get(k).getAsBoolean(); }
}
