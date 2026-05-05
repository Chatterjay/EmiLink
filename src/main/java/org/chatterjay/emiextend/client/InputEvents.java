package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.mixin.MEStorageScreenAccessor;
import org.chatterjay.emiextend.util.ModLogger;

public final class InputEvents {
    private InputEvents() {}

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (!ModKeybindings.FILL_SEARCH_KEY.matches(event.getKeyCode(), event.getScanCode())) {
            return;
        }
        ModLogger.debug("FILL_SEARCH_KEY pressed");

        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) {
            ModLogger.debug("FILL_SEARCH_KEY: no hovered stack");
            return;
        }

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) {
            ModLogger.debug("FILL_SEARCH_KEY: ingredient is null/empty");
            return;
        }

        var emiStacks = ingredient.getEmiStacks();
        if (emiStacks.isEmpty()) {
            ModLogger.debug("FILL_SEARCH_KEY: no emi stacks");
            return;
        }

        var first = emiStacks.getFirst();

        boolean alt = Screen.hasAltDown();
        var text = alt ? "@" + first.getId().getNamespace() : first.getName().getString();
        if (text.isEmpty()) {
            ModLogger.debug("FILL_SEARCH_KEY: {} is empty", alt ? "modid" : "name");
            return;
        }

        var screen = Minecraft.getInstance().screen;
        if (screen == null) return;

        // AE2: MEStorageScreen search field
        if (AE2Proxy.isMEStorageScreen(screen)) {
            try {
                var acc = (MEStorageScreenAccessor) screen;
                acc.emilink$getSearchField().setValue(text);
                acc.emilink$setSearchText(text);
                event.setCanceled(true);
                ModLogger.debug("FILL_SEARCH_KEY: set AE2 search text to '{}' ({})", text, alt ? "modid" : "name");
                return;
            } catch (Throwable e) {
                ModLogger.warn("FILL_SEARCH_KEY: AE2 exception: {}", e.getMessage());
            }
        }

        // BD: DimensionsNetGUI search field (via reflection)
        if (BDProxy.isBDNetGUI(screen)) {
            if (BDProxy.setSearchText(screen, text)) {
                event.setCanceled(true);
                ModLogger.debug("FILL_SEARCH_KEY: set BD search text to '{}' ({})", text, alt ? "modid" : "name");
            } else {
                ModLogger.warn("FILL_SEARCH_KEY: BD setSearchText failed");
            }
            return;
        }

        ModLogger.debug("FILL_SEARCH_KEY: screen not supported: {}", screen.getClass().getName());
    }
}
