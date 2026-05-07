package org.chatterjay.emiextend.util;

import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class IPNProxy {
    private static boolean checked = false;
    private static boolean available = false;
    private static Method getInstanceMethod;
    private static Method getLockedSlotsMethod;

    private IPNProxy() {}

    @SuppressWarnings("unchecked")
    private static void init() {
        if (checked) return;
        checked = true;
        try {
            Class<?> ipnClass = Class.forName("org.anti_ad.mc.ipn.api.access.IPN");
            getInstanceMethod = ipnClass.getMethod("getInstance");
            getLockedSlotsMethod = ipnClass.getMethod("getLockedSlots");
            available = true;
        } catch (Throwable t) {
        }
    }

    /**
     * Get the set of locked player inventory slot indices.
     * Returns empty set if IPN is not installed or no slots are locked.
     */
    public static Set<Integer> getLockedSlots() {
        init();
        if (!available) return Collections.emptySet();

        try {
            Object instance = getInstanceMethod.invoke(null);
            if (instance == null) return Collections.emptySet();

            Object result = getLockedSlotsMethod.invoke(instance);
            if (!(result instanceof List<?> slotList)) return Collections.emptySet();

            Set<Integer> locked = new LinkedHashSet<>();
            for (Object obj : slotList) {
                int idx;
                if (obj instanceof Slot slot) {
                    idx = slot.getSlotIndex();
                } else if (obj instanceof Number num) {
                    // IPN's getLockedSlots() returns List<Integer> (slot indices directly)
                    idx = num.intValue();
                } else {
                    continue;
                }
                // Only include main inventory + hotbar (0-35), exclude armor/offhand
                if (idx >= 0 && idx < 36) {
                    locked.add(idx);
                }
            }
            return locked;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    /**
     * Check if a specific player inventory slot index is locked by IPN.
     */
    public static boolean isPlayerSlotLocked(int slotIndex) {
        return getLockedSlots().contains(slotIndex);
    }

    /**
     * Check if there are any locked slots at all (e.g. for bulkTransferAll guard).
     */
    public static boolean hasLockedSlots() {
        return !getLockedSlots().isEmpty();
    }

    /**
     * Get locked slot indices within a specific range [start, end).
     */
    public static int[] getLockedSlotsInRange(int start, int end) {
        Set<Integer> locked = getLockedSlots();
        return locked.stream()
                .filter(i -> i >= start && i < end)
                .mapToInt(Integer::intValue)
                .toArray();
    }
}
