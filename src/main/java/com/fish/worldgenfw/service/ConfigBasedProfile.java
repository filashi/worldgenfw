package com.fish.worldgenfw.service;

import com.fish.worldgenfw.api.StructureClusterProfile;
import net.minecraft.resources.ResourceLocation;

public class ConfigBasedProfile implements StructureClusterProfile {
    private final boolean allowsClustering;
    private final int minDistanceToOther;
    private final int maxClusterDistance;
    private final boolean canBeCenter;
    private final int centerPriority;

    public ConfigBasedProfile(boolean allowsClustering, int minDistanceToOther, int maxClusterDistance, boolean canBeCenter, int centerPriority) {
        this.allowsClustering = allowsClustering;
        this.minDistanceToOther = minDistanceToOther;
        this.maxClusterDistance = maxClusterDistance;
        this.canBeCenter = canBeCenter;
        this.centerPriority = centerPriority;
    }

    @Override
    public boolean allowsClustering() { return allowsClustering; }

    @Override
    public int getMinDistanceToOther(ResourceLocation otherStructureId) { return minDistanceToOther; }

    @Override
    public int getMaxClusterDistance() { return maxClusterDistance; }

    @Override
    public boolean canBeClusterCenter() { return canBeCenter; }

    @Override
    public int getCenterPriority() { return centerPriority; }
}