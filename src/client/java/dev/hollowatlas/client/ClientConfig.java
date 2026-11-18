package dev.hollowatlas.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.hollowatlas.HollowAtlas;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Per-machine client preferences, saved to {@code config/hollowatlas-client.json}. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hollowatlas-client.json");

    public boolean showHud = true;
    public boolean showMarkers = true;
    public boolean showTracker = true;
    public float dialogueTextScale = 1.0f;
    public int dialogueSpeed = 2;       // 0 = instant, 1 = fast, 2 = normal, 3 = slow
    public boolean reducedEffects = false;

    private static ClientConfig instance;

    public static ClientConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) { instance = GSON.fromJson(r, ClientConfig.class); }
            }
        } catch (Exception e) {
            HollowAtlas.LOGGER.warn("Could not read client config; using defaults", e);
        }
        if (instance == null) instance = new ClientConfig();
        instance.dialogueTextScale = Math.max(0.75f, Math.min(1.5f, instance.dialogueTextScale));
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) { GSON.toJson(get(), w); }
        } catch (Exception e) {
            HollowAtlas.LOGGER.warn("Could not save client config", e);
        }
    }
}
