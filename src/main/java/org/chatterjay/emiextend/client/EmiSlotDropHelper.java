package org.chatterjay.emiextend.client;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.registry.EmiDragDropHandlers;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.chatterjay.emiextend.mixin.AbstractContainerScreenAccessor;
import org.chatterjay.emiextend.util.ModLogger;

/**
 * Marks ghost/filter slots through EMI's own drag-and-drop registry, which is
 * what defines the slots that support being marked by dragging a stack onto them.
 */
public final class EmiSlotDropHelper {

    /** Size of the clickable cell around a slot, matching EMI's own drop targets. */
    private static final int CELL_SIZE = 18;

    private EmiSlotDropHelper() {}

    /** Offer an ingredient to the drop handlers registered for this screen. */
    public static boolean dropOnScreen(Screen screen, EmiIngredient ingredient, int x, int y) {
        if (screen == null || ingredient == null || ingredient.isEmpty()) return false;
        try {
            return EmiDragDropHandlers.dropStack(screen, ingredient, x, y);
        } catch (Throwable error) {
            ModLogger.debug("EmiSlotDrop: drop handler failed on {}: {}",
                    screen.getClass().getName(), error.toString());
            return false;
        }
    }

    public static int slotCenterX(AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot) {
        return ((AbstractContainerScreenAccessor) screen).emilink$getLeftPos() + slot.x + 8;
    }

    public static int slotCenterY(AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot) {
        return ((AbstractContainerScreenAccessor) screen).emilink$getTopPos() + slot.y + 8;
    }

    /** Index in {@code menu.slots} of the slot under the given screen position, or -1. */
    public static int slotIndexAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        if (screen == null) return -1;
        var slots = screen.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            var slot = slots.get(i);
            int x = slotCenterX(screen, slot) - CELL_SIZE / 2;
            int y = slotCenterY(screen, slot) - CELL_SIZE / 2;
            if (mouseX >= x && mouseX < x + CELL_SIZE && mouseY >= y && mouseY < y + CELL_SIZE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Mark the first empty slot at or after {@code fromIndex} that accepts the
     * ingredient. Returns the filled slot index in {@code menu.slots}, or -1 when
     * no remaining slot accepted it.
     */
    public static int fillFirstEmptySlot(AbstractContainerScreen<?> screen, EmiIngredient ingredient,
                                         int fromIndex) {
        if (screen == null || ingredient == null || ingredient.isEmpty()) return -1;
        var slots = screen.getMenu().slots;
        for (int i = Math.max(0, fromIndex); i < slots.size(); i++) {
            var slot = slots.get(i);
            if (!slot.isActive() || slot.hasItem()) continue;
            if (dropOnScreen(screen, ingredient, slotCenterX(screen, slot), slotCenterY(screen, slot))) {
                ModLogger.debug("EmiSlotDrop: marked slot {} of {}",
                        slot.index, screen.getClass().getName());
                return i;
            }
        }
        return -1;
    }
}
