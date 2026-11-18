package dev.hollowatlas.state;

import net.minecraft.nbt.NbtCompound;

/**
 * Persistent identity + abstract simulation state of one authored resident.
 * The live entity is disposable; this record is the truth used to (re)spawn it.
 */
public final class NpcRecord {
    public double x, y, z;
    public boolean placed = false;
    public boolean alive = true;
    public String task = "";
    public String anchor = "";
    /** Optional routine variant set by world changes (e.g. a reopened stall). */
    public String variant = "";

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putDouble("x", x);
        n.putDouble("y", y);
        n.putDouble("z", z);
        n.putBoolean("placed", placed);
        n.putBoolean("alive", alive);
        n.putString("task", task);
        n.putString("anchor", anchor);
        n.putString("variant", variant);
        return n;
    }

    public static NpcRecord fromNbt(NbtCompound n) {
        NpcRecord r = new NpcRecord();
        r.x = n.getDouble("x");
        r.y = n.getDouble("y");
        r.z = n.getDouble("z");
        r.placed = n.getBoolean("placed");
        r.alive = !n.contains("alive") || n.getBoolean("alive");
        r.task = n.getString("task");
        r.anchor = n.getString("anchor");
        r.variant = n.getString("variant");
        return r;
    }
}
