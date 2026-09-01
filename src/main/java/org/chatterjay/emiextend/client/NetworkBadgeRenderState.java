package org.chatterjay.emiextend.client;

import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.integration.AE2Proxy;

/** Shared render state used by the EMI sidebar mixins. */
public final class NetworkBadgeRenderState {

    private static final ThreadLocal<Boolean> SIDEBAR_RENDERING =
            ThreadLocal.withInitial(() -> false);

    private NetworkBadgeRenderState() {}

    /** Whether EmiLink owns all amount rendering for the active sidebar frame. */
    public static boolean shouldReplaceNativeAmount(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        return ownsSidebarAmount();
    }

    /** Shared condition for both EMI amount rendering paths. */
    public static boolean ownsSidebarAmount() {
        return AE2Proxy.isLoaded()
                && EmiLinkConfig.ENABLE_NETWORK_BADGES.get()
                && AENetworkCache.hasAEAccess();
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
