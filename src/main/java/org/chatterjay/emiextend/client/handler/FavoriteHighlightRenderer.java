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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Renders protected favorites as connected groups and the active drag rectangle. */
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
            int x1 = Math.min(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
            int y1 = Math.min(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());
            int x2 = Math.max(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
            int y2 = Math.max(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());
            var cells = new ArrayList<OverlayCell>();
            var markedBySpace = new IdentityHashMap<EmiScreenManager.ScreenSpace, Set<Long>>();
            FavoriteDragSelectHandler.forEachVisibleCell((space, stack, x, y, col, row) -> {
                boolean inside = dragging
                        && FavoriteDragSelectHandler.isCellSelected(x, y, x1, y1, x2, y2);
                if (inside || FavoriteProtection.isProtected(stack)) {
                    cells.add(new OverlayCell(space, x, y, col, row));
                    markedBySpace.computeIfAbsent(space, ignored -> new HashSet<>())
                            .add(cellKey(col, row));
                }
            });
            for (var cell : cells) {
                var marked = markedBySpace.get(cell.space());
                int x = cell.x();
                int y = cell.y();
                int size = 18;
                drawOutline(guiGraphics, x, y, size, borderColor,
                        !marked.contains(cellKey(cell.col() - 1, cell.row())),
                        !marked.contains(cellKey(cell.col() + 1, cell.row())),
                        !marked.contains(cellKey(cell.col(), cell.row() - 1)),
                        !marked.contains(cellKey(cell.col(), cell.row() + 1)));
            }
            if (dragging) drawSelectionBorder(guiGraphics, x1, y1, x2, y2, borderColor);
        } catch (Throwable e) {
            ModLogger.debug("FavoriteHighlightRenderer: overlay error: {}", e.toString());
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    /** Draw only the border after EMI has rendered its item icons. */
    private static void drawSelectionBorder(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2,
                                             int borderColor) {
        if (x2 <= x1 || y2 <= y1) return;
        guiGraphics.fill(x1, y1, x2, y1 + 1, borderColor);
        guiGraphics.fill(x1, y2 - 1, x2, y2, borderColor);
        guiGraphics.fill(x1, y1, x1 + 1, y2, borderColor);
        guiGraphics.fill(x2 - 1, y1, x2, y2, borderColor);
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int size, int color,
                                    boolean drawLeft, boolean drawRight,
                                    boolean drawTop, boolean drawBottom) {
        if (drawTop) guiGraphics.fill(x, y, x + size, y + 1, color);
        if (drawBottom) guiGraphics.fill(x, y + size - 1, x + size, y + size, color);
        if (drawLeft) guiGraphics.fill(x, y, x + 1, y + size, color);
        if (drawRight) guiGraphics.fill(x + size - 1, y, x + size, y + size, color);
    }

    private static long cellKey(int col, int row) {
        return ((long) col << 32) ^ (row & 0xFFFFFFFFL);
    }

    private record OverlayCell(EmiScreenManager.ScreenSpace space, int x, int y, int col, int row) {}

    /**
     * Render the translucent part before EMI draws the slot contents. The
     * caller invokes this once for each ScreenSpace, so backgrounds are never
     * repeated and item icons remain visually above the hint layer.
     */
    public static void renderBackground(GuiGraphics guiGraphics,
                                        EmiScreenManager.ScreenSpace targetSpace) {
        if (!EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get()
                || EmiScreenManager.isDisabled() || targetSpace == null) return;

        boolean dragging = FavoriteDragSelectHandler.isActive();
        int backgroundColor = EmiLinkConfig.getFavoriteProtectionBackgroundArgb();
        int x1 = Math.min(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
        int y1 = Math.min(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());
        int x2 = Math.max(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
        int y2 = Math.max(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 0);
        try {
            FavoriteDragSelectHandler.forEachVisibleCell(targetSpace, (space, stack, x, y, col, row) -> {
                if (space != targetSpace) return;
                boolean inside = dragging
                        && FavoriteDragSelectHandler.isCellSelected(x, y, x1, y1, x2, y2);
                if (inside || FavoriteProtection.isProtected(stack)) {
                    guiGraphics.fill(x, y, x + 18, y + 18, backgroundColor);
                }
            });
        } catch (Throwable e) {
            ModLogger.debug("FavoriteHighlightRenderer: background error: {}", e.toString());
        } finally {
            guiGraphics.pose().popPose();
        }
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
