package dev.hollowatlas.state;

import dev.hollowatlas.HollowAtlas;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The single canonical, server-authoritative store for all Hollow Atlas progress.
 * Persisted by vanilla's PersistentStateManager in {@code <world>/data/hollowatlas_state.dat}
 * so it autosaves with the world. Every section is versioned through {@link #VERSION} and
 * migrated in {@link StateMigration}.
 */
public final class GameState extends PersistentState {
    public static final int VERSION = 1;
    public static final String FILE_ID = "hollowatlas_state";

    /** Namespaced world flags, e.g. {@code hollowatlas:region_built}. */
    public final Set<String> worldFlags = new LinkedHashSet<>();
    public final Map<UUID, PlayerProfile> players = new HashMap<>();
    public final Map<String, NpcRecord> npcs = new HashMap<>();
    /** Gathering node id -> world time at which it becomes available again. */
    public final Map<String, Long> nodeCooldowns = new HashMap<>();
    /** Shop id -> item id -> stock. */
    public final Map<String, Map<String, Integer>> shopStock = new HashMap<>();
    public final Map<String, Long> shopRestockDay = new HashMap<>();
    public String difficulty = "normal";
    /** Monotonic counter for deterministic, save-safe ids. */
    public long sequence = 0;

    public static final Type<GameState> TYPE = new Type<>(GameState::new, GameState::fromNbt, null);

    public static GameState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, FILE_ID);
    }

    public boolean flag(String id) { return worldFlags.contains(id); }

    public void setFlag(String id, boolean value) {
        boolean changed = value ? worldFlags.add(id) : worldFlags.remove(id);
        if (changed) markDirty();
    }

    public PlayerProfile profile(UUID uuid) {
        return players.computeIfAbsent(uuid, u -> { markDirty(); return new PlayerProfile(); });
    }

    public NpcRecord npc(String id) {
        return npcs.computeIfAbsent(id, k -> { markDirty(); return new NpcRecord(); });
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("version", VERSION);
        nbt.put("flags", strings(worldFlags));
        NbtCompound ps = new NbtCompound();
        players.forEach((uuid, p) -> ps.put(uuid.toString(), p.toNbt()));
        nbt.put("players", ps);
        NbtCompound ns = new NbtCompound();
        npcs.forEach((id, n) -> ns.put(id, n.toNbt()));
        nbt.put("npcs", ns);
        NbtCompound nodes = new NbtCompound();
        nodeCooldowns.forEach(nodes::putLong);
        nbt.put("nodes", nodes);
        NbtCompound shops = new NbtCompound();
        shopStock.forEach((shop, stock) -> {
            NbtCompound s = new NbtCompound();
            stock.forEach(s::putInt);
            shops.put(shop, s);
        });
        nbt.put("shops", shops);
        NbtCompound restock = new NbtCompound();
        shopRestockDay.forEach(restock::putLong);
        nbt.put("shop_restock", restock);
        nbt.putString("difficulty", difficulty);
        nbt.putLong("sequence", sequence);
        return nbt;
    }

    public static GameState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound data = StateMigration.migrate(nbt);
        GameState s = new GameState();
        readStrings(data.getList("flags", NbtElement.STRING_TYPE), s.worldFlags);
        NbtCompound ps = data.getCompound("players");
        for (String key : ps.getKeys()) {
            try {
                s.players.put(UUID.fromString(key), PlayerProfile.fromNbt(ps.getCompound(key)));
            } catch (IllegalArgumentException e) {
                HollowAtlas.LOGGER.warn("Skipping corrupt player profile key {}", key);
            }
        }
        NbtCompound ns = data.getCompound("npcs");
        for (String key : ns.getKeys()) s.npcs.put(key, NpcRecord.fromNbt(ns.getCompound(key)));
        NbtCompound nodes = data.getCompound("nodes");
        for (String key : nodes.getKeys()) s.nodeCooldowns.put(key, nodes.getLong(key));
        NbtCompound shops = data.getCompound("shops");
        for (String shop : shops.getKeys()) {
            Map<String, Integer> stock = new HashMap<>();
            NbtCompound sc = shops.getCompound(shop);
            for (String item : sc.getKeys()) stock.put(item, Math.max(0, sc.getInt(item)));
            s.shopStock.put(shop, stock);
        }
        NbtCompound restock = data.getCompound("shop_restock");
        for (String key : restock.getKeys()) s.shopRestockDay.put(key, restock.getLong(key));
        if (data.contains("difficulty")) s.difficulty = data.getString("difficulty");
        s.sequence = data.getLong("sequence");
        return s;
    }

    public static NbtList strings(Iterable<String> values) {
        NbtList list = new NbtList();
        for (String v : values) list.add(NbtString.of(v));
        return list;
    }

    public static void readStrings(NbtList list, java.util.Collection<String> into) {
        for (int i = 0; i < list.size(); i++) into.add(list.getString(i));
    }
}
