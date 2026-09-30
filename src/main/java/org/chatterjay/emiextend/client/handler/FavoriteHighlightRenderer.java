package org.chatterjay.emiextend.client.handler;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.chatterjay.emiextend.client.BatchMark;
import org.chatterjay.emiextend.client.FavoriteProtection;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;

/**
 * Renders the sidebar selection outlines and the active drag rectangle.
 * <p>
 * Every cell uses its full 18x18 square, so neighbouring cells merge into one
 * connected block. An edge that belongs to only one selection is drawn in that
 * selection's colour. An edge that is the border of both selections is handed
 * over half way: the half nearer the cell's top/left keeps the protection
 * colour, the half nearer the bottom/right continues in the mark colour. A run
 * of neighbouring cells therefore shows both colours interleaved along the same
 * line instead of one of them replacing the other or nesting an offset square.
 */
public final class FavoriteHighlightRenderer {
    private FavoriteHighlightRenderer() {}

    private static final int CELL = 18;

    private static final int EDGE_TOP = 0;
    private static final int EDGE_RIGHT = 1;
    private static final int EDGE_BOTTOM = 2;
    private static final int EDGE_LEFT = 3;

    /** One rendered sidebar cell and the selections it belongs to. */
    private record Cell(int x, int y, int col, int row, boolean protectedCell, boolean markedCell) {}

    public static void renderOverlays(GuiGraphics guiGraphics) {
        boolean protectEnabled = EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get();
        boolean markEnabled = EmiLinkConfig.ENABLE_BATCH_MARK.get();
        if (!protectEnabled && !markEnabled) return;
        if (EmiScreenManager.isDisabled()) return;

        boolean dragging = FavoriteDragSelectHandler.isActive();
        var dragMode = FavoriteDragSelectHandler.mode();
        boolean dragProtect = dragging && dragMode == FavoriteDragSelectHandler.DragMode.PROTECT;
        boolean dragMark = dragging && dragMode == FavoriteDragSelectHandler.DragMode.MARK;

        int x1 = Math.min(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
        int y1 = Math.min(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());
        int x2 = Math.max(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
        int y2 = Math.max(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());

        int protectColor = EmiLinkConfig.getFavoriteProtectionBorderArgb();
        int markColor = EmiLinkConfig.getBatchMarkBorderArgb();

        var bySpace = collectCells(protectEnabled, markEnabled, dragProtect, dragMark, x1, y1, x2, y2);
        if (bySpace.isEmpty() && !dragging) return;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 150);
        try {
            for (var cells : bySpace.values()) {
                for (var cell : cells.values()) {
                    drawCellOutline(guiGraphics, cells, cell, protectColor, markColor);
                }
            }
            if (dragProtect) {
                drawSelectionBorder(guiGraphics, x1, y1, x2, y2, protectColor);
            } else if (dragMark) {
                drawSelectionBorder(guiGraphics, x1, y1, x2, y2, markColor);
            }
        } catch (Throwable e) {
            ModLogger.debug("FavoriteHighlightRenderer: overlay error: {}", e.toString());
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    /**
     * Collect every cell that belongs to a selection, keyed by screen space and
     * cell position so neighbour lookups stay O(1).
     */
    private static Map<EmiScreenManager.ScreenSpace, Map<Long, Cell>> collectCells(
            boolean protectEnabled, boolean markEnabled, boolean dragProtect, boolean dragMark,
            int x1, int y1, int x2, int y2) {
        var bySpace = new IdentityHashMap<EmiScreenManager.ScreenSpace, Map<Long, Cell>>();
        FavoriteDragSelectHandler.forEachVisibleCell((space, stack, x, y, col, row) -> {
            boolean protectedCell = protectEnabled && FavoriteProtection.isProtected(stack);
            boolean markedCell = markEnabled && BatchMark.isMarked(stack);
            if (dragProtect || dragMark) {
                boolean inside = FavoriteDragSelectHandler.isCellSelected(x, y, x1, y1, x2, y2);
                if (dragProtect) protectedCell |= inside;
                if (dragMark) markedCell |= inside;
            }
            if (!protectedCell && !markedCell) return;
            bySpace.computeIfAbsent(space, ignored -> new LinkedHashMap<>())
                    .put(cellKey(col, row), new Cell(x, y, col, row, protectedCell, markedCell));
        });
        return bySpace;
    }

    /**
     * Draw the edges of one cell that are on the border of a selection. Edges on
     * both borders split the square: the top and left edges continue the
     * protection outline, the bottom and right edges continue the mark outline.
     */
    private static void drawCellOutline(GuiGraphics guiGraphics, Map<Long, Cell> cells, Cell cell,
                                        int protectColor, int markColor) {
        for (int edge = 0; edge < 4; edge++) {
            Cell neighbour = cells.get(neighbourKey(cell, edge));
            boolean protectionEdge = cell.protectedCell()
                    && (neighbour == null || !neighbour.protectedCell());
            boolean markEdge = cell.markedCell()
                    && (neighbour == null || !neighbour.markedCell());
            if (!protectionEdge && !markEdge) continue;

            if (protectionEdge && markEdge) {
                if (EmiLinkConfig.SHARED_EDGE_SPLIT.get()) {
                    drawSplitEdge(guiGraphics, cell, edge, protectColor, markColor);
                } else {
                    drawEdge(guiGraphics, cell, edge, markColor);
                }
            } else {
                drawEdge(guiGraphics, cell, edge, protectionEdge ? protectColor : markColor);
            }
        }
    }

    private static long neighbourKey(Cell cell, int edge) {
        return switch (edge) {
            case EDGE_TOP -> cellKey(cell.col(), cell.row() - 1);
            case EDGE_BOTTOM -> cellKey(cell.col(), cell.row() + 1);
            case EDGE_LEFT -> cellKey(cell.col() - 1, cell.row());
            default -> cellKey(cell.col() + 1, cell.row());
        };
    }

    /**
     * An edge shared by both selections is split in half: the half nearer the
     * cell's top or left keeps the protection colour and the other half is handed
     * over to the mark colour, so the two outlines continue into each other.
     */
    private static void drawSplitEdge(GuiGraphics guiGraphics, Cell cell, int edge,
                                      int protectColor, int markColor) {
        int x = cell.x();
        int y = cell.y();
        int half = CELL / 2;
        switch (edge) {
            case EDGE_TOP -> {
                guiGraphics.fill(x, y, x + half, y + 1, protectColor);
                guiGraphics.fill(x + half, y, x + CELL, y + 1, markColor);
            }
            case EDGE_BOTTOM -> {
                guiGraphics.fill(x, y + CELL - 1, x + half, y + CELL, protectColor);
                guiGraphics.fill(x + half, y + CELL - 1, x + CELL, y + CELL, markColor);
            }
            case EDGE_LEFT -> {
                guiGraphics.fill(x, y, x + 1, y + half, protectColor);
                guiGraphics.fill(x, y + half, x + 1, y + CELL, markColor);
            }
            default -> {
                guiGraphics.fill(x + CELL - 1, y, x + CELL, y + half, protectColor);
                guiGraphics.fill(x + CELL - 1, y + half, x + CELL, y + CELL, markColor);
            }
        }
    }

    private static void drawEdge(GuiGraphics guiGraphics, Cell cell, int edge, int color) {
        int x = cell.x();
        int y = cell.y();
        switch (edge) {
            case EDGE_TOP -> guiGraphics.fill(x, y, x + CELL, y + 1, color);
            case EDGE_BOTTOM -> guiGraphics.fill(x, y + CELL - 1, x + CELL, y + CELL, color);
            case EDGE_LEFT -> guiGraphics.fill(x, y, x + 1, y + CELL, color);
            default -> guiGraphics.fill(x + CELL - 1, y, x + CELL, y + CELL, color);
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

    private static long cellKey(int col, int row) {
        return ((long) col << 32) ^ (row & 0xFFFFFFFFL);
    }

    /**
     * Render the translucent part before EMI draws the slot contents. The
     * caller invokes this once for each ScreenSpace, so backgrounds are never
     * repeated and item icons remain visually above the hint layer.
     */
    public static void renderBackground(GuiGraphics guiGraphics,
                                        EmiScreenManager.ScreenSpace targetSpace) {
        if (targetSpace == null || EmiScreenManager.isDisabled()) return;

        boolean protectEnabled = EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get();
        boolean markEnabled = EmiLinkConfig.ENABLE_BATCH_MARK.get();
        if (!protectEnabled && !markEnabled) return;

        boolean dragging = FavoriteDragSelectHandler.isActive();
        var dragMode = FavoriteDragSelectHandler.mode();
        boolean dragProtect = dragging && dragMode == FavoriteDragSelectHandler.DragMode.PROTECT;
        boolean dragMark = dragging && dragMode == FavoriteDragSelectHandler.DragMode.MARK;

        int x1 = Math.min(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
        int y1 = Math.min(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());
        int x2 = Math.max(FavoriteDragSelectHandler.dragStartX(), FavoriteDragSelectHandler.dragCurrentX());
        int y2 = Math.max(FavoriteDragSelectHandler.dragStartY(), FavoriteDragSelectHandler.dragCurrentY());

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 0);
        try {
            if (protectEnabled) {
                fillLayer(guiGraphics, targetSpace, false,
                        EmiLinkConfig.getFavoriteProtectionBackgroundArgb(),
                        dragProtect, x1, y1, x2, y2);
            }
            if (markEnabled) {
                fillLayer(guiGraphics, targetSpace, true,
                        EmiLinkConfig.getBatchMarkBackgroundArgb(),
                        dragMark, x1, y1, x2, y2);
            }
        } catch (Throwable e) {
            ModLogger.debug("FavoriteHighlightRenderer: background error: {}", e.toString());
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    private static void fillLayer(GuiGraphics guiGraphics, EmiScreenManager.ScreenSpace targetSpace,
                                  boolean markLayer, int backgroundColor,
                                  boolean dragActive, int x1, int y1, int x2, int y2) {
        FavoriteDragSelectHandler.forEachVisibleCell(targetSpace, (space, stack, x, y, col, row) -> {
            if (space != targetSpace) return;
            boolean member = markLayer ? BatchMark.isMarked(stack) : FavoriteProtection.isProtected(stack);
            if (!member && dragActive) {
                member = FavoriteDragSelectHandler.isCellSelected(x, y, x1, y1, x2, y2);
            }
            if (member) {
                guiGraphics.fill(x, y, x + CELL, y + CELL, backgroundColor);
            }
        });
    }

    /** Adds the selection hints for protected and batch-marked stacks to a sidebar tooltip. */
    public static List<ClientTooltipComponent> appendSelectionTooltip(
            EmiIngredient hovered, List<ClientTooltipComponent> original) {
        if (original == null || original.isEmpty()) return original;

        List<ClientTooltipComponent> result = original;
        if (EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get() && FavoriteProtection.isProtected(hovered)) {
            result = new ArrayList<>(result);
            result.add(dev.emi.emi.api.render.EmiTooltipComponents.of(
                    Component.translatable("emilink.tooltip.favorite_protected")
                            .withStyle(ChatFormatting.GOLD)));
        }
        if (EmiLinkConfig.ENABLE_BATCH_MARK.get() && BatchMark.isMarked(hovered)) {
            if (result == original) {
                result = new ArrayList<>(result);
            }
            result.add(dev.emi.emi.api.render.EmiTooltipComponents.of(
                    Component.translatable("emilink.tooltip.batch_marked")
                            .withStyle(ChatFormatting.AQUA)));
        }
        return result;
    }
}
