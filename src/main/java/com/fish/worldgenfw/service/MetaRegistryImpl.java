package com.fish.worldgenfw.service;

import com.fish.worldgenfw.api.StructureClusterProfile;
import com.fish.worldgenfw.api.StructureMetaRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class MetaRegistryImpl implements StructureMetaRegistry {
    private final Map<ResourceLocation, StructureClusterProfile> profiles = new HashMap<>();
    private final StructureClusterProfile defaultProfile;

    public MetaRegistryImpl(StructureClusterProfile defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    @Override
    public void registerProfile(ResourceLocation structureId, StructureClusterProfile profile) {
        profiles.put(structureId, profile);
    }

    @Override
    public StructureClusterProfile getProfile(ResourceLocation structureId) {
        return profiles.getOrDefault(structureId, defaultProfile);
    }

    // 便捷方法，用于内部预设原版结构
    public void presetProfile(String structureId, boolean allowsClustering, int minDist, int maxDist, boolean canBeCenter, int priority) {
        registerProfile(ResourceLocation.parse(structureId), new ConfigBasedProfile(allowsClustering, minDist, maxDist, canBeCenter, priority));
    }
}