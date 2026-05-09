package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.mixin.MEStorageScreenAccessor;
import org.chatterjay.emiextend.util.ModLogger;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class InputEvents {
    private InputEvents() {}

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (!ModKeybindings.FILL_SEARCH_KEY.matches(event.getKeyCode(), event.getScanCode())) {
            return;
        }
        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return;

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) return;

        var emiStacks = ingredient.getEmiStacks();
        if (emiStacks.isEmpty()) return;

        var first = emiStacks.getFirst();

        boolean alt = Screen.hasAltDown();
        var text = alt ? "@" + first.getId().getNamespace() : first.getName().getString();
        if (text.isEmpty()) return;

        var screen = Minecraft.getInstance().screen;
        if (screen == null) return;

        // AE2: MEStorageScreen search field
        if (AE2Proxy.isMEStorageScreen(screen)) {
            try {
                var acc = (MEStorageScreenAccessor) screen;
                acc.emilink$getSearchField().setValue(text);
                acc.emilink$setSearchText(text);
                event.setCanceled(true);
                return;
            } catch (Throwable e) {
                ModLogger.warn("FILL_SEARCH_KEY: AE2 exception: {}", e.getMessage());
            }
        }

        // BD: DimensionsNetGUI search field (via reflection)
        if (BDProxy.isBDNetGUI(screen)) {
            if (BDProxy.setSearchText(screen, text)) {
                event.setCanceled(true);
            } else {
                ModLogger.warn("FILL_SEARCH_KEY: BD setSearchText failed");
            }
            return;
        }

        // EMI search fallback for any screen with EMI sidebar
        try {
            EmiApi.setSearchText(text);
            event.setCanceled(true);
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: EMI setSearchText failed: {}", e.getMessage());
        }
    }
}
