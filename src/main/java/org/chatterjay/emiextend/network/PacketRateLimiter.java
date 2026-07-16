package org.chatterjay.emiextend.network;

import org.chatterjay.emiextend.config.EmiLinkConfig;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-packet-type rate limiter keyed on game tick.
 * Debug packets are limited to 1 per tick to prevent network congestion.
 */
public final class PacketRateLimiter {
    private static final AtomicInteger currentTick = new AtomicInteger(-1);
    private static final AtomicInteger packetsThisTick = new AtomicInteger(0);

    private PacketRateLimiter() {}

    /** Notify the rate limiter of a new game tick. */
    public static void onTick() {
        currentTick.incrementAndGet();
        packetsThisTick.set(0);
    }

    /**
     * Check if a debug packet is allowed this tick.
     * {@link #onTick()} must be called each game tick for accurate tracking.
     */
    public static boolean allowDebugPacket() {
        if (!EmiLinkConfig.ENABLE_DEBUG_PACKET_LIMIT.get()) return true;
        return packetsThisTick.updateAndGet(n -> n + 1) <= 1;
    }

    /** Reset state (e.g., on world unload or disconnect). */
    public static void reset() {
        currentTick.set(-1);
        packetsThisTick.set(0);
    }
}
