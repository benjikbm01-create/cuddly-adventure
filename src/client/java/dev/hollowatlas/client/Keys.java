package dev.hollowatlas.client;

import com.google.gson.JsonObject;
import dev.hollowatlas.client.ui.AtlasScreen;
import dev.hollowatlas.client.ui.JournalScreen;
import dev.hollowatlas.client.ui.SatchelScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Action-RPG key bindings. All are remappable in Options > Controls under "The Hollow Atlas".
 * Defaults avoid vanilla keys; if a player maps one onto an existing binding we warn in chat.
 */
public final class Keys {
    public static final String CATEGORY = "key.categories.hollowatlas";
    public static KeyBinding HEAVY, DODGE, GUARD, ABILITY_1, ABILITY_2, JOURNAL, SATCHEL, ATLAS, DEV_MENU;
    private static final List<KeyBinding> OURS = new ArrayList<>();
    private static boolean guardHeld = false;
    private static int conflictCheckIn = 100;

    private Keys() {}

    private static KeyBinding reg(String id, int key) {
        KeyBinding k = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hollowatlas." + id, InputUtil.Type.KEYSYM, key, CATEGORY));
        OURS.add(k);
        return k;
    }

    public static void init() {
        HEAVY = reg("heavy", GLFW.GLFW_KEY_R);
        DODGE = reg("dodge", GLFW.GLFW_KEY_LEFT_ALT);
        GUARD = reg("guard", GLFW.GLFW_KEY_V);
        ABILITY_1 = reg("ability_1", GLFW.GLFW_KEY_Z);
        ABILITY_2 = reg("ability_2", GLFW.GLFW_KEY_X);
        JOURNAL = reg("journal", GLFW.GLFW_KEY_J);
        SATCHEL = reg("satchel", GLFW.GLFW_KEY_I);
        ATLAS = reg("atlas", GLFW.GLFW_KEY_M);
        DEV_MENU = reg("dev_menu", GLFW.GLFW_KEY_F7);
        ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> conflictCheckIn = 100);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        if (conflictCheckIn > 0 && --conflictCheckIn == 0) warnConflicts(mc);
        while (HEAVY.wasPressed()) ClientState.send("heavy");
        while (DODGE.wasPressed()) {
            JsonObject o = new JsonObject();
            o.addProperty("x", -mc.player.input.movementSideways);
            o.addProperty("z", mc.player.input.movementForward);
            ClientState.send("dodge", o);
        }
        boolean g = GUARD.isPressed() && mc.currentScreen == null;
        if (g != guardHeld) {
            guardHeld = g;
            JsonObject o = new JsonObject();
            o.addProperty("on", g);
            ClientState.send("guard", o);
        }
        while (ABILITY_1.wasPressed()) ability(0);
        while (ABILITY_2.wasPressed()) ability(1);
        while (JOURNAL.wasPressed()) { ClientState.send("journal_request"); mc.setScreen(new JournalScreen(0)); }
        while (SATCHEL.wasPressed()) mc.setScreen(new SatchelScreen());
        while (ATLAS.wasPressed()) { ClientState.send("journal_request"); mc.setScreen(new AtlasScreen()); }
        while (DEV_MENU.wasPressed()) {
            // only in developer testing worlds; the server re-checks every action anyway
            if (devMenuAllowed()) mc.setScreen(new dev.hollowatlas.client.ui.DevMenuScreen());
        }
    }

    private static void ability(int slot) {
        JsonObject o = new JsonObject();
        o.addProperty("slot", slot);
        ClientState.send("ability", o);
    }

    /** Warn (once per join) if any Hollow Atlas action shares a key with another binding. */
    public static void warnConflicts(MinecraftClient mc) {
        for (KeyBinding ours : OURS) {
            if (ours.isUnbound()) continue;
            for (KeyBinding other : mc.options.allKeys) {
                if (other == ours || other.isUnbound()) continue;
                if (other.equals(ours)) {
                    mc.player.sendMessage(Text.translatable("message.hollowatlas.key_conflict",
                            Text.translatable(ours.getTranslationKey()), Text.translatable(other.getTranslationKey()),
                            ours.getBoundKeyLocalizedText()).formatted(Formatting.YELLOW), false);
                }
            }
        }
    }

    /** The developer menu only exists in the developer test world (the server reports the mode). */
    public static boolean devMenuAllowed() {
        return ClientState.b(ClientState.hud, "devtest");
    }
}
