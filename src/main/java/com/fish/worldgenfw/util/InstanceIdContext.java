// com/fish/worldgenfw/util/InstanceIdContext.java
package com.fish.worldgenfw.util;

/**
 * 线程本地存储，用于在 GenerationContext 阶段预分配 instanceId，
 * 并传递给 StructureStart 构造器使用。
 */
public class InstanceIdContext {
    private static final ThreadLocal<Integer> NEXT_INSTANCE_ID = new ThreadLocal<>();

    public static void setNextId(int id) {
        NEXT_INSTANCE_ID.set(id);
    }

    public static Integer getNextId() {
        return NEXT_INSTANCE_ID.get();
    }

    public static void clear() {
        NEXT_INSTANCE_ID.remove();
    }
}