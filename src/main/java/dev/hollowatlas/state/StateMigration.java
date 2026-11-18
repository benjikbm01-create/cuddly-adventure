package dev.hollowatlas.state;

import dev.hollowatlas.HollowAtlas;
import net.minecraft.nbt.NbtCompound;

/**
 * Upgrades older saved {@link GameState} NBT to the current schema, one version step at a time.
 * The raw file is backed up by {@link StateBackup} before any migration runs.
 */
public final class StateMigration {
    private StateMigration() {}

    public static NbtCompound migrate(NbtCompound nbt) {
        int version = nbt.contains("version") ? nbt.getInt("version") : 0;
        if (version > GameState.VERSION) {
            HollowAtlas.LOGGER.error("Hollow Atlas save data version {} is newer than this build ({}). "
                    + "Loading what is understood; unknown fields are preserved only until next save.", version, GameState.VERSION);
            return nbt;
        }
        NbtCompound data = nbt.copy();
        if (version < 1) {
            // v0 -> v1: initial schema; nothing to transform, but stamp the version.
            data.putInt("version", 1);
        }
        return data;
    }
}
