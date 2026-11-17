package dev.hollowatlas;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (server + client) entrypoint for The Hollow Atlas.
 */
public final class HollowAtlas implements ModInitializer {
    public static final String MOD_ID = "hollowatlas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("The Hollow Atlas initializing (common)");
    }
}
