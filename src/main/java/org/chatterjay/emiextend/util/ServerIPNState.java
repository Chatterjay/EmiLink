package org.chatterjay.emiextend.util;

import java.util.*;

/**
 * Server-side storage for IPN-locked slot indices.
 * Updated via AELockedSlotsPacket before each AE Space+click.
 */
public final class ServerIPNState {
    private static final Map<UUID, Set<Integer>> lockedSlotsPerPlayer = new HashMap<>();

    private ServerIPNState() {}

    public static void setLockedSlots(UUID playerUuid, int[] slots) {
        if (slots == null || slots.length == 0) {
            lockedSlotsPerPlayer.remove(playerUuid);
        } else {
            Set<Integer> set = new LinkedHashSet<>();
            for (int s : slots) set.add(s);
            lockedSlotsPerPlayer.put(playerUuid, set);
        }
    }

    public static Set<Integer> getLockedSlots(UUID playerUuid) {
        return lockedSlotsPerPlayer.getOrDefault(playerUuid, Collections.emptySet());
    }

    public static void clear(UUID playerUuid) {
        lockedSlotsPerPlayer.remove(playerUuid);
    }
}
