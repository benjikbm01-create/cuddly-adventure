package dev.hollowatlas.state;

import dev.hollowatlas.HollowAtlas;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Before the state file is loaded, copy it aside if it was written by an older schema version,
 * so a failed migration can always be rolled back by hand.
 */
public final class StateBackup {
    private StateBackup() {}

    public static void backupIfMigrating(MinecraftServer server) {
        Path file = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(GameState.FILE_ID + ".dat");
        if (!Files.exists(file)) return;
        try {
            NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            NbtCompound data = root.getCompound("data");
            int version = data.contains("version") ? data.getInt("version") : 0;
            if (version < GameState.VERSION) {
                Path backup = file.resolveSibling(GameState.FILE_ID + ".v" + version + ".bak");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                HollowAtlas.LOGGER.info("Backed up Hollow Atlas state (schema v{}) to {}", version, backup.getFileName());
            }
        } catch (Exception e) {
            Path backup = file.resolveSibling(GameState.FILE_ID + ".unreadable.bak");
            try { Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING); } catch (Exception ignored) {}
            HollowAtlas.LOGGER.error("Hollow Atlas state file could not be read; a copy was kept at {}", backup.getFileName(), e);
        }
    }
}
