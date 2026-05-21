package com.fish.worldgenfw.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储结构实例ID到偏移量的映射，用于在StructureGenerateMixin和StructureStartMixin之间传递数据。
 */
public class OffsetMap {
    private static final Map<Integer, int[]> OFFSET_MAP = new ConcurrentHashMap<>();

    public static void putOffset(int instanceId, int offsetX, int offsetZ) {
        OFFSET_MAP.put(instanceId, new int[]{offsetX, offsetZ});
    }

    public static int[] removeOffset(int instanceId) {
        return OFFSET_MAP.remove(instanceId);
    }

    public static void clear() {
        OFFSET_MAP.clear();
    }
}