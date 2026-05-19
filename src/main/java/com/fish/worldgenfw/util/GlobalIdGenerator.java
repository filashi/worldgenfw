// com/fish/worldgenfw/util/GlobalIdGenerator.java
package com.fish.worldgenfw.util;

import java.util.concurrent.atomic.AtomicInteger;

public class GlobalIdGenerator {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static int nextId() {
        return COUNTER.getAndIncrement();
    }
}