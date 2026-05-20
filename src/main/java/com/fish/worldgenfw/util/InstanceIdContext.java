package com.fish.worldgenfw.util;

public class InstanceIdContext {
    private static final ThreadLocal<Integer> NEXT_INSTANCE_ID = new ThreadLocal<>();

    public static void setNextId(int id) { NEXT_INSTANCE_ID.set(id); }
    public static Integer getNextId() { return NEXT_INSTANCE_ID.get(); }
    public static void clear() { NEXT_INSTANCE_ID.remove(); }
}