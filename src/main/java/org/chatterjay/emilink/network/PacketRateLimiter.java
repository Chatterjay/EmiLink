package org.chatterjay.emilink.network;

import org.chatterjay.emilink.Config;

import java.util.concurrent.atomic.AtomicInteger;

public final class PacketRateLimiter {
    private static final AtomicInteger currentTick = new AtomicInteger(-1);
    private static final AtomicInteger packetsThisTick = new AtomicInteger(0);

    private PacketRateLimiter() {}

    public static void onTick() {
        currentTick.incrementAndGet();
        packetsThisTick.set(0);
    }

    public static boolean allowDebugPacket() {
        if (!Config.ENABLE_DEBUG_PACKET_LIMIT.get()) return true;
        return packetsThisTick.updateAndGet(n -> n + 1) <= 1;
    }

    public static void reset() {
        currentTick.set(-1);
        packetsThisTick.set(0);
    }
}
