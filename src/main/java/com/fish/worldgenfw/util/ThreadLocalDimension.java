package com.fish.worldgenfw.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class ThreadLocalDimension {
    private static final ThreadLocal<ResourceKey<Level>> CURRENT_DIMENSION = new ThreadLocal<>();

    public static void set(ResourceKey<Level> dim) { CURRENT_DIMENSION.set(dim); }
    public static ResourceKey<Level> get() { return CURRENT_DIMENSION.get(); }
    public static void clear() { CURRENT_DIMENSION.remove(); }
}