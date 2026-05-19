// com/fish/worldgenfw/service/GenerationIntentCache.java
package com.fish.worldgenfw.service;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生成意图缓存，用于存储计划生成但尚未完成方块放置的结构信息。
 * 线程安全。
 */
public class GenerationIntentCache {

    private static final ConcurrentHashMap<Integer, PlannedStructure> CACHE = new ConcurrentHashMap<>();

    /**
     * 注册一个生成意图。
     */
    public static void put(int instanceId, PlannedStructure structure) {
        CACHE.put(instanceId, structure);
    }

    /**
     * 获取指定实例的意图。
     */
    public static PlannedStructure get(int instanceId) {
        return CACHE.get(instanceId);
    }

    /**
     * 获取所有当前处于 PLANNED 状态的结构。
     */
    public static List<PlannedStructure> getAllPlanned() {
        List<PlannedStructure> result = new ArrayList<>();
        for (PlannedStructure s : CACHE.values()) {
            if (s.status() == Status.PLANNED) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * 移除指定实例的缓存记录。
     */
    public static void remove(int instanceId) {
        CACHE.remove(instanceId);
    }

    /**
     * 清空全部缓存。
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * 计划结构记录。
     */
    public record PlannedStructure(int instanceId, String structureId, AABB boundingBox, Status status) {}

    public enum Status {
        PLANNED,     // 计划中，尚未生成
        GENERATED    // 已生成完成
    }
}