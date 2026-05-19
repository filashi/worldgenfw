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

    public WorldGenFw(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置
        modContainer.registerConfig(ModConfig.Type.SERVER, ClusterConfig.SPEC);

        // 初始化注册表
        StructureClusterProfile defaultProfile = new ConfigBasedProfile(true, 16, 320, false, 0);
        metaRegistry = new MetaRegistryImpl(defaultProfile);
        // 预设原版结构
        metaRegistry.presetProfile("minecraft:village_plains", true, 16, 320, false, 0);
        metaRegistry.presetProfile("minecraft:pillager_outpost", true, 32, 400, true, 1);

        coordinator = new DefaultClusterCoordinator(metaRegistry);
    }

    public static ClusterCoordinator getCoordinator() {
        return coordinator;
    }
}