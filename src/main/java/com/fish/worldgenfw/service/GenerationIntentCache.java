package com.fish.worldgenfw.service;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GenerationIntentCache {
    private static final ConcurrentHashMap<Integer, PlannedStructure> CACHE = new ConcurrentHashMap<>();

    public static void put(int instanceId, PlannedStructure structure) {
        CACHE.put(instanceId, structure);
    }

    public static PlannedStructure get(int instanceId) {
        return CACHE.get(instanceId);
    }

    public static List<PlannedStructure> getAllPlanned() {
        List<PlannedStructure> result = new ArrayList<>();
        for (PlannedStructure s : CACHE.values()) {
            if (s.status() == Status.PLANNED) result.add(s);
        }
        return result;
    }

    public static void remove(int instanceId) {
        CACHE.remove(instanceId);
    }

    public static void clear() {
        CACHE.clear();
    }

    /** 检查给定的包围盒是否与缓存中任何计划结构（排除自身）有重叠 */
    public static boolean hasPlannedConflict(AABB box, int excludeInstanceId) {
        for (PlannedStructure planned : getAllPlanned()) {
            if (planned.instanceId() == excludeInstanceId) continue;
            if (box.intersects(planned.boundingBox())) return true;
        }
        return false;
    }

    public record PlannedStructure(int instanceId, String structureId, AABB boundingBox, Status status) {}
    public enum Status { PLANNED, GENERATED }
}