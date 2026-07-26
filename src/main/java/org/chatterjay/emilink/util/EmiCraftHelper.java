package org.chatterjay.emilink.util;

/**
 * ThreadLocal flag for passing single-craft signal from InventoryActionPacket.extraId
 * to CraftingTermSlot.doClick within the same server thread.
 */
public final class EmiCraftHelper {
    private static final ThreadLocal<Boolean> singleCraftToInventory = ThreadLocal.withInitial(() -> false);

    private EmiCraftHelper() {
    }

    public static void markSingleCraft() {
        singleCraftToInventory.set(true);
    }

    public static boolean checkSingleCraft() {
        return singleCraftToInventory.get();
    }

    public static void clear() {
        singleCraftToInventory.remove();
    }
}
