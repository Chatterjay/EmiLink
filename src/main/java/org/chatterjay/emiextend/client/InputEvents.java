package org.chatterjay.emiextend.client;

import appeng.client.gui.me.common.MEStorageScreen;
import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
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
        if (screen instanceof MEStorageScreen<?> me) {
            try {
                var acc = (MEStorageScreenAccessor) me;
                acc.emilink$getSearchField().setValue(name);
                acc.emilink$setSearchText(name);
                event.setCanceled(true);
                ModLogger.debug("FILL_SEARCH_KEY: set search text to '{}'", name);
            } catch (Throwable e) {
                ModLogger.warn("FILL_SEARCH_KEY: exception setting search text: {}", e.getMessage());
            }
        } else {
            ModLogger.debug("FILL_SEARCH_KEY: screen is not MEStorageScreen, it's {}", screen != null ? screen.getClass().getName() : "null");
        }
    }
}
