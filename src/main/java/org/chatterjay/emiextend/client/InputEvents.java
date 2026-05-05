package org.chatterjay.emiextend.client;

import appeng.client.gui.me.common.MEStorageScreen;
import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
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

        var name = emiStacks.getFirst().getName().getString();
        if (name.isEmpty()) {
            ModLogger.debug("FILL_SEARCH_KEY: name is empty");
            return;
        }

        var screen = Minecraft.getInstance().screen;
        if (screen == null) return;

        // AE2: MEStorageScreen search field
        if (screen instanceof MEStorageScreen<?> me) {
            try {
                var acc = (MEStorageScreenAccessor) me;
                acc.emilink$getSearchField().setValue(name);
                acc.emilink$setSearchText(name);
                event.setCanceled(true);
                ModLogger.debug("FILL_SEARCH_KEY: set AE2 search text to '{}'", name);
                return;
            } catch (Throwable e) {
                ModLogger.warn("FILL_SEARCH_KEY: AE2 exception: {}", e.getMessage());
            }
        }

        // BD: DimensionsNetGUI search field (via reflection)
        if (BDProxy.isBDNetGUI(screen)) {
            if (BDProxy.setSearchText(screen, name)) {
                event.setCanceled(true);
                ModLogger.debug("FILL_SEARCH_KEY: set BD search text to '{}'", name);
            } else {
                ModLogger.warn("FILL_SEARCH_KEY: BD setSearchText failed");
            }
            return;
        }

        ModLogger.debug("FILL_SEARCH_KEY: screen not supported: {}", screen.getClass().getName());
    }
}
