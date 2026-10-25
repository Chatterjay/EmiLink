package org.chatterjay.emiextend.client;

import net.minecraft.world.item.ItemStack;

/** Shared render state used by the EMI sidebar mixins. */
public final class NetworkBadgeRenderState {

    private static final ThreadLocal<Boolean> SIDEBAR_RENDERING =
            ThreadLocal.withInitial(() -> false);

    private NetworkBadgeRenderState() {}

    /**
     * Whether EmiLink replaces the amount EMI would draw for this item stack.
     * Kept for the item decoration path, which only ever sees an ItemStack.
     */
    public static boolean shouldReplaceNativeAmount(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        return ownsSidebarAmount();
    }

    /**
     * Whether EmiLink replaces the amount EMI would draw for a stack of any type.
     * Fluids, chemicals and other AE keys have no ItemStack, but their amounts
     * are drawn through the same EMI helper and must be replaced as well.
     */
    public static boolean shouldReplaceAmount() {
        return ownsSidebarAmount();
    }

    /**
     * Shared condition for every EMI amount rendering path. True for EmiLink's
     * own overlay and for the ExtendedAE Plus fallback, so the native EMI amount
     * is suppressed in both cases and only the network amount is drawn.
     */
    public static boolean ownsSidebarAmount() {
        return NetworkStockSource.overlayEnabled();
    }

    public static boolean isSidebarRendering() {
        return SIDEBAR_RENDERING.get();
    }

    public static void beginSidebarRender() {
        SIDEBAR_RENDERING.set(true);
    }

    public static void endSidebarRender() {
        SIDEBAR_RENDERING.set(false);
    }
}
