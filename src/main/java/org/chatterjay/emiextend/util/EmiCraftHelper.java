package org.chatterjay.emiextend.util;

/**
 * ThreadLocal flag for passing single-craft signal from InventoryActionPacket.extraId
 * to CraftingTermSlot.doClick within the same server thread.
 */
public class EmiCraftHelper {
    private static final ThreadLocal<Boolean> singleCraftToInventory = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> singleCraftProduced = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> singleCraftDelivered = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> singleCraftToNetwork = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> aeAutocraftFromQuickCraft = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> aeAutocraftHandoff = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Long> aeAutocraftRequestedAmount = ThreadLocal.withInitial(() -> 0L);

    public static void markSingleCraft() {
        singleCraftToInventory.set(true);
    }

    public static boolean checkSingleCraft() {
        return singleCraftToInventory.get();
    }

    /** Mark that the current single-craft request produced a real result. */
    public static void markSingleCraftProduced() {
        singleCraftProduced.set(true);
    }

    /** Consume the result flag for the current server-side craft request. */
    public static boolean consumeSingleCraftProduced() {
        boolean produced = singleCraftProduced.get();
        singleCraftProduced.set(false);
        return produced;
    }

    /** Record how many output items reached inventory, AE, or the cursor. */
    public static void markSingleCraftDelivered(int amount) {
        singleCraftDelivered.set(Math.max(0, amount));
    }

    /** Consume the number of output items that were stored instead of dropped. */
    public static int consumeSingleCraftDelivered() {
        int delivered = singleCraftDelivered.get();
        singleCraftDelivered.set(0);
        return delivered;
    }

    /** Allow intermediate output overflow to be inserted into AE storage. */
    public static void markSingleCraftToNetwork() {
        singleCraftToNetwork.set(true);
    }

    public static boolean checkSingleCraftToNetwork() {
        return singleCraftToNetwork.get();
    }

    public static void markAeAutocraftFromQuickCraft() {
        aeAutocraftFromQuickCraft.set(true);
    }

    public static void markAeAutocraftFromQuickCraft(long requestedAmount) {
        aeAutocraftFromQuickCraft.set(true);
        aeAutocraftRequestedAmount.set(Math.max(0L, requestedAmount));
    }

    public static boolean checkAeAutocraftFromQuickCraft() {
        return aeAutocraftFromQuickCraft.get();
    }

    public static long getAeAutocraftRequestedAmount() {
        return aeAutocraftRequestedAmount.get();
    }

    public static void markAeAutocraftHandoff() {
        aeAutocraftHandoff.set(true);
    }

    public static boolean checkAeAutocraftHandoff() {
        return aeAutocraftHandoff.get();
    }

    public static void clear() {
        singleCraftToInventory.remove();
        singleCraftProduced.remove();
        singleCraftDelivered.remove();
        singleCraftToNetwork.remove();
        aeAutocraftFromQuickCraft.remove();
        aeAutocraftHandoff.remove();
        aeAutocraftRequestedAmount.remove();
    }
}
