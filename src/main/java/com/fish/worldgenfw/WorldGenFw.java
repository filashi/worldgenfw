package com.fish.worldgenfw;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod("worldgenfw")
public class WorldGenFw {
    public static final String MODID = "worldgenfw";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WorldGenFw() {
        LOGGER.info("WorldGenFW initialized. All structure clustering is data-driven via Lithostitched.");
    }
}