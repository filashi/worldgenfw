package com.fish.worldgenfw;

import com.fish.worldgenfw.api.ClusterCoordinator;
import com.fish.worldgenfw.api.StructureClusterProfile;
import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.service.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod("worldgenfw")
public class WorldGenFw {
    private static DefaultClusterCoordinator coordinator;
    private static MetaRegistryImpl metaRegistry;
    private static PlacementMap placementMap;

    public WorldGenFw(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, ClusterConfig.SPEC);

        StructureClusterProfile defaultProfile = new ConfigBasedProfile(true, 16, 320, false, 0);
        metaRegistry = new MetaRegistryImpl(defaultProfile);
        metaRegistry.presetProfile("minecraft:village_plains", true, 16, 320, false, 0);
        metaRegistry.presetProfile("minecraft:pillager_outpost", true, 32, 400, true, 1);

        coordinator = new DefaultClusterCoordinator(metaRegistry);
        placementMap = new PlacementMap();
    }

    public static ClusterCoordinator getCoordinator() { return coordinator; }
    public static PlacementMap getPlacementMap() { return placementMap; }
    public static MetaRegistryImpl getMetaRegistry() { return metaRegistry; }
}
