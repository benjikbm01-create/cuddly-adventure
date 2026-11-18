package dev.hollowatlas.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.hollowatlas.HollowAtlas;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Loads all authored Hollow Atlas content from data packs ({@code data/hollowatlas/<kind>/*.json}),
 * reports problems per file, and runs cross-reference validation after every (re)load.
 * Content is immutable between reloads; runtime progress lives in {@code GameState}.
 */
public final class ContentManager implements SimpleSynchronousResourceReloadListener {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** A registered content kind: folder name, POJO class and a sink that stores parsed objects. */
    public record Kind<T>(String folder, Class<T> type, Map<String, T> store) {}

    private static final List<Kind<?>> KINDS = new ArrayList<>();
    private static final List<BiConsumer<ContentManager, List<String>>> VALIDATORS = new ArrayList<>();
    private static final List<String> LAST_REPORT = new ArrayList<>();
    private static int loadCount = 0;

    public static <T> Map<String, T> register(String folder, Class<T> type) {
        Map<String, T> store = new LinkedHashMap<>();
        KINDS.add(new Kind<>(folder, type, store));
        return Collections.unmodifiableMap(store);
    }

    public static void validator(BiConsumer<ContentManager, List<String>> v) { VALIDATORS.add(v); }

    public static List<String> report() { return Collections.unmodifiableList(LAST_REPORT); }

    public static int loads() { return loadCount; }

    public static void init() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new ContentManager());
    }

    @Override
    public Identifier getFabricId() { return HollowAtlas.id("content"); }

    @Override
    public void reload(ResourceManager manager) {
        LAST_REPORT.clear();
        int total = 0;
        StringBuilder counts = new StringBuilder();
        for (Kind<?> kind : KINDS) {
            int n = load(manager, kind);
            total += n;
            counts.append(kind.folder()).append('=').append(n).append(' ');
            if (n == 0) LAST_REPORT.add("content kind '" + kind.folder() + "' loaded no files");
        }
        HollowAtlas.LOGGER.info("Content by kind: {}", counts.toString().trim());
        for (BiConsumer<ContentManager, List<String>> v : VALIDATORS) {
            try {
                v.accept(this, LAST_REPORT);
            } catch (Exception e) {
                LAST_REPORT.add("validator crashed: " + e);
            }
        }
        loadCount++;
        HollowAtlas.LOGGER.info("Loaded {} Hollow Atlas content files; {} problem(s)", total, LAST_REPORT.size());
        for (String line : LAST_REPORT) HollowAtlas.LOGGER.warn("[content] {}", line);
    }

    private <T> int load(ResourceManager manager, Kind<T> kind) {
        kind.store().clear();
        int n = 0;
        Map<Identifier, Resource> found = manager.findResources(kind.folder(),
                id -> id.getNamespace().equals(HollowAtlas.MOD_ID) && id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            String file = e.getKey().getPath();
            try (Reader reader = e.getValue().getReader()) {
                T obj = GSON.fromJson(reader, kind.type());
                if (obj == null) { LAST_REPORT.add(file + ": empty file"); continue; }
                String id = idOf(obj, file);
                if (kind.store().containsKey(id)) LAST_REPORT.add(file + ": duplicate id '" + id + "'");
                if (obj instanceof Validatable v) v.validate(file, LAST_REPORT);
                kind.store().put(id, obj);
                n++;
            } catch (JsonParseException | IllegalStateException ex) {
                LAST_REPORT.add(file + ": invalid JSON - " + ex.getMessage());
            } catch (Exception ex) {
                LAST_REPORT.add(file + ": could not be read - " + ex);
            }
        }
        return n;
    }

    private static String idOf(Object obj, String file) {
        if (obj instanceof Validatable v && v.id() != null && !v.id().isBlank()) return v.id();
        String name = file.substring(file.lastIndexOf('/') + 1);
        return name.substring(0, name.length() - 5);
    }

    /** Content objects that know their id and can self-check. */
    public interface Validatable {
        String id();
        void validate(String file, List<String> problems);
    }
}
