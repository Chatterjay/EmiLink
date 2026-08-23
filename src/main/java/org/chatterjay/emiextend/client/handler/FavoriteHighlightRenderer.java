package org.chatterjay.emiextend.client.handler;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.chatterjay.emiextend.client.FavoriteProtection;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.List;

/** Renders the protected-favorite outlines and the active drag rectangle. */
public final class FavoriteHighlightRenderer {
    private FavoriteHighlightRenderer() {}

    public static void renderOverlays(GuiGraphics guiGraphics) {
        if (!EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get()) return;
        if (EmiScreenManager.isDisabled()) return;
        boolean dragging = FavoriteDragSelectHandler.isActive();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 150);
        try {
            int borderColor = EmiLinkConfig.getFavoriteProtectionBorderArgb();
            int backgroundColor = EmiLinkConfig.getFavoriteProtectionBackgroundArgb();
            int x1 = Math.min(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
            int y1 = Math.min(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());
            int x2 = Math.max(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
            int y2 = Math.max(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());
            if (dragging) drawSelectionRect(guiGraphics, x1, y1, x2, y2, backgroundColor, borderColor);

            FavoriteDragSelectHandler.forEachVisibleCell((space, stack, x, y) -> {
                boolean inside = dragging
                        && FavoriteDragSelectHandler.isCellSelected(x, y, x1, y1, x2, y2);
                if (inside || FavoriteProtection.isProtected(stack)) {
                    drawOutline(guiGraphics, x + 1, y + 1, 16, borderColor);
                }
            });
        } catch (Throwable e) {
            ModLogger.debug("FavoriteHighlightRenderer: overlay error: {}", e.toString());
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    private static void drawSelectionRect(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2,
                                          int backgroundColor, int borderColor) {
        if (x2 <= x1 || y2 <= y1) return;
        guiGraphics.fill(x1, y1, x2, y2, backgroundColor);
        guiGraphics.fill(x1, y1, x2, y1 + 1, borderColor);
        guiGraphics.fill(x1, y2 - 1, x2, y2, borderColor);
        guiGraphics.fill(x1, y1, x1 + 1, y2, borderColor);
        guiGraphics.fill(x2 - 1, y1, x2, y2, borderColor);
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int size, int color) {
        guiGraphics.fill(x, y, x + size, y + 1, color);
        guiGraphics.fill(x, y + size - 1, x + size, y + size, color);
        guiGraphics.fill(x, y, x + 1, y + size, color);
        guiGraphics.fill(x + size - 1, y, x + size, y + size, color);
    }

    public static List<ClientTooltipComponent> appendProtectedTooltip(
            EmiIngredient hovered, List<ClientTooltipComponent> original) {
        if (original == null || original.isEmpty()) return original;
        if (!EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get()
                || !FavoriteProtection.isProtected(hovered)) return original;
        var result = new ArrayList<>(original);
        result.add(dev.emi.emi.api.render.EmiTooltipComponents.of(
                Component.translatable("emilink.tooltip.favorite_protected")
                        .withStyle(ChatFormatting.GOLD)));
        return result;
    }
}
