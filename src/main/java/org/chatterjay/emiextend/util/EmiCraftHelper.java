package org.chatterjay.emiextend.util;

/**
 * ThreadLocal flag for passing single-craft signal from InventoryActionPacket.extraId
 * to CraftingTermSlot.doClick within the same server thread.
 */
public class EmiCraftHelper {
    private static final ThreadLocal<Boolean> singleCraftToInventory = ThreadLocal.withInitial(() -> false);

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
