package org.chatterjay.emiextend.client.handler;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.chatterjay.emiextend.client.FavoriteProtection;
import org.chatterjay.emiextend.client.util.ModifierKeyUtil;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Modifier + left-drag over an EMI sidebar batch-favorites every stack in the
 * rectangle; modifier + right-drag removes protection from those stacks.
 * The selection owns the EMI drag while the modifier is held, including on the
 * favorites sidebar, so EMI cannot reorder or activate the underlying stack.
 */
public final class FavoriteDragSelectHandler {
    private static boolean active = false;
    private static boolean protectMode = false;
    private static int startX, startY, currentX, currentY;
    private static final long DELETE_DOUBLE_PRESS_WINDOW_MS = 400L;
    private static String pendingDeleteId;
    private static long pendingDeleteAt;
    private static final Set<String> lastProtectedSelectionIds = new LinkedHashSet<>();

    private FavoriteDragSelectHandler() {}

    public static boolean isActive() {
        return active;
    }

    public static int dragStartX() {
        return startX;
    }

    public static int dragStartY() {
        return startY;
    }

    public static int dragCurrentX() {
        return currentX;
    }

    public static int dragCurrentY() {
        return currentY;
    }

    /**
     * A single Delete is consumed, while two quick presses on a protected
     * favorite remove the whole most recent protected drag selection containing
     * the hovered favorite.
     */
    public static boolean handleProtectedFavoriteDelete(int keyCode, int mouseX, int mouseY) {
        if (!EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get() || keyCode != GLFW.GLFW_KEY_DELETE) {
            return false;
        }
        if (EmiScreenManager.isDisabled()) return false;
        if (pendingDeleteExpired()) clearPendingDelete();
        var screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen != null && screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox) {
            return false;
        }

        EmiIngredient target = findProtectedFavorite(mouseX, mouseY);
        if (target == null) {
            clearPendingDelete();
            return false;
        }

        String id = getStackId(target);
        if (id == null) {
            clearPendingDelete();
            return false;
        }

        long now = System.currentTimeMillis();
        boolean sameProtectedSelection = pendingDeleteId != null
                && lastProtectedSelectionIds.contains(pendingDeleteId)
                && lastProtectedSelectionIds.contains(id);
        boolean doublePress = (id.equals(pendingDeleteId) || sameProtectedSelection)
                && now - pendingDeleteAt <= DELETE_DOUBLE_PRESS_WINDOW_MS;
        pendingDeleteId = doublePress ? null : id;
        pendingDeleteAt = doublePress ? 0L : now;

        if (!doublePress) {
            ModLogger.debug("FavoriteDragSelect: Delete armed for protected favorite {}", id);
            return true;
        }

        Set<String> removalIds = new LinkedHashSet<>();
        if (lastProtectedSelectionIds.contains(id)) {
            removalIds.addAll(lastProtectedSelectionIds);
        } else {
            removalIds.add(id);
        }

        Set<String> removedIds = removeFavorites(removalIds);
        lastProtectedSelectionIds.removeAll(removedIds);
        try {
            EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
        } catch (Throwable error) {
            ModLogger.debug("FavoriteDragSelect: favorite refresh failed: {}", error.toString());
        }
        ModLogger.debug("FavoriteDragSelect: double Delete removed={}/{} favorites, trigger={}",
                removedIds.size(), removalIds.size(), id);
        return true;
    }

    private static Set<String> removeFavorites(Set<String> ids) {
        Set<String> removedIds = new LinkedHashSet<>();
        if (ids.isEmpty() || EmiFavorites.favorites == null) return removedIds;

        for (EmiIngredient favorite : new ArrayList<>(EmiFavorites.favorites)) {
            String favoriteId = getStackId(favorite);
            if (favoriteId == null || !ids.contains(favoriteId)) continue;

            FavoriteProtection.unprotect(favorite);
            boolean accepted = false;
            try {
                accepted = EmiFavorites.removeFavorite(favorite);
                if (!accepted && favorite instanceof dev.emi.emi.runtime.EmiFavorite emiFavorite) {
                    accepted = EmiFavorites.removeFavorite(emiFavorite.getStack());
                }
            } catch (Throwable error) {
                ModLogger.debug("FavoriteDragSelect: Delete removal failed for {}: {}",
                        favoriteId, error.toString());
            }

            if (accepted) {
                removedIds.add(favoriteId);
            } else {
                FavoriteProtection.protect(favorite);
            }
        }
        return removedIds;
    }

    private static boolean pendingDeleteExpired() {
        return pendingDeleteId != null
                && System.currentTimeMillis() - pendingDeleteAt > DELETE_DOUBLE_PRESS_WINDOW_MS;
    }

    private static void clearPendingDelete() {
        pendingDeleteId = null;
        pendingDeleteAt = 0L;
    }

    private static EmiIngredient findProtectedFavorite(int mouseX, int mouseY) {
        final EmiIngredient[] result = {null};
        forEachVisibleCell((space, stack, x, y, col, row) -> {
            if (result[0] != null || space.getType() != SidebarType.FAVORITES) return;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18
                    && FavoriteProtection.isProtected(stack)) {
                result[0] = stack;
            }
        });
        return result[0];
    }

    private static String getStackId(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return null;
        if (ingredient instanceof dev.emi.emi.runtime.EmiFavorite favorite) {
            ingredient = favorite.getStack();
        }
        var stacks = ingredient.getEmiStacks();
        if (stacks == null || stacks.size() != 1) return null;
        var id = stacks.get(0).getId();
        return id == null ? null : id.toString();
    }

    /** Returns true when the press was consumed as the start of a drag selection. */
    public static boolean onMousePressed(Screen screen, double mouseX, double mouseY, int button) {
        if (!EmiLinkConfig.ENABLE_FAVORITE_DRAG_SELECT.get()) return false;
        if (button != 0 && button != 1) return false;
        if (!isModifierHeld()) return false;
        if (screen == null || EmiScreenManager.isDisabled()) return false;
        if (screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;
        var panel = EmiScreenManager.getHoveredPanel(mx, my);
        if (panel == null || !panel.isVisible()) return false;

        var hoveredSpace = panel.getHoveredSpace(mx, my);
        active = true;
        protectMode = button == 0;
        startX = currentX = mx;
        startY = currentY = my;
        clearEmiDragState();
        ModLogger.debug("FavoriteDragSelect: started mode={} at ({},{}), space type={}",
                protectMode ? "protect" : "unprotect", mx, my,
                hoveredSpace == null ? "none" : hoveredSpace.getType());
        return true;
    }

    public static boolean onMouseDragged(double mouseX, double mouseY) {
        if (!active) return false;
        currentX = (int) mouseX;
        currentY = (int) mouseY;
        clearEmiDragState();
        return true;
    }

    /** Returns true when the release finished an active drag selection. */
    public static boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (!active) return false;
        active = false;
        clearEmiDragState();
        if ((button != 0 && button != 1) || protectMode != (button == 0)) {
            ModLogger.debug("FavoriteDragSelect: released with mismatched button {}, discarding", button);
            return true;
        }

        int x1 = Math.min(startX, (int) mouseX);
        int y1 = Math.min(startY, (int) mouseY);
        int x2 = Math.max(startX, (int) mouseX);
        int y2 = Math.max(startY, (int) mouseY);

        int favored = 0;
        int protectedCount = 0;
        int unprotected = 0;
        var selectedStacks = collectVisibleStacks(x1, y1, x2, y2);
        if (protectMode) {
            lastProtectedSelectionIds.clear();
        }
        for (EmiIngredient stack : selectedStacks) {
            if (protectMode) {
                String id = getStackId(stack);
                if (id != null) lastProtectedSelectionIds.add(id);
                boolean alreadyFavorited = false;
                try {
                    alreadyFavorited = EmiFavorites.favorites != null
                            && EmiFavorites.favorites.stream().anyMatch(f -> f.strictEquals(stack));
                    if (!alreadyFavorited) {
                        EmiFavorites.addFavorite(stack);
                        favored++;
                        ModLogger.debug("FavoriteDragSelect: added favorite for {}", stack);
                    }
                } catch (Throwable e) {
                    ModLogger.debug("FavoriteDragSelect: addFavorite failed for {}: {}", stack, e);
                }
                protectedCount++;
            } else {
                String id = getStackId(stack);
                if (id != null) lastProtectedSelectionIds.remove(id);
                unprotected++;
            }
        }
        if (protectMode) {
            FavoriteProtection.protectAll(selectedStacks);
        } else {
            FavoriteProtection.unprotectAll(selectedStacks);
        }
        if (favored > 0) {
            try {
                EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
            } catch (Throwable e) {
                ModLogger.debug("FavoriteDragSelect: repopulatePanels failed: {}", e.toString());
            }
        }
        ModLogger.debug("FavoriteDragSelect: finished rect=({},{})-({},{}), favored={} protected={} unprotected={} deleteGroup={}",
                x1, y1, x2, y2, favored, protectedCount, unprotected, lastProtectedSelectionIds.size());
        return true;
    }

    public static void clearEmiDragState() {
        EmiScreenManager.pressedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
        EmiScreenManager.draggedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
    }

    private static boolean isModifierHeld() {
        return ModifierKeyUtil.isHeld(EmiLinkConfig.getFavoriteDragSelectKeyName());
    }

    private static java.util.List<EmiIngredient> collectVisibleStacks(int x1, int y1, int x2, int y2) {
        java.util.List<EmiIngredient> result = new ArrayList<>();
        forEachVisibleCell((space, stack, x, y, col, row) -> {
            if (isCellSelected(x, y, x1, y1, x2, y2)) result.add(stack);
        });
        return result;
    }

    /** Selects a slot when any part of the rectangle overlaps its 18x18 cell. */
    public static boolean isCellSelected(int cellX, int cellY, int x1, int y1, int x2, int y2) {
        int left = Math.min(x1, x2);
        int top = Math.min(y1, y2);
        int right = Math.max(x1, x2) + 1;
        int bottom = Math.max(y1, y2) + 1;
        return cellX < right && cellX + 18 > left
                && cellY < bottom && cellY + 18 > top;
    }

    public interface CellVisitor {
        void accept(EmiScreenManager.ScreenSpace space, EmiIngredient stack,
                   int x, int y, int col, int row);
    }

    /** Walks every visible sidebar cell using EMI's own screen-space offsets. */
    public static void forEachVisibleCell(CellVisitor visitor) {
        for (SidebarSide side : SidebarSide.values()) {
            var panel = EmiScreenManager.getPanelFor(side);
            if (panel == null || !panel.isVisible()) continue;
            for (var space : panel.getSpaces()) {
                if (space == null || space.pageSize <= 0) continue;
                var stacks = space.getStacks();
                if (stacks == null || stacks.isEmpty()) continue;

                boolean isMainSpace = space == panel.space;
                for (int row = 0; row < space.th; row++) {
                    int columns = space.getWidth(row);
                    for (int col = 0; col < columns; col++) {
                        int local = space.getRawOffset(col, row);
                        if (local < 0) continue;
                        int index = local;
                        if (isMainSpace) index += space.pageSize * panel.page;
                        if (index < 0 || index >= stacks.size()) continue;
                        EmiIngredient stack = stacks.get(index);
                        if (stack == null || stack.isEmpty()) continue;
                        visitor.accept(space, stack, space.getX(col, row), space.getY(col, row), col, row);
                    }
                }
            }
        }
    }

    /** Walk only one visible space; used by the per-space background pass. */
    public static void forEachVisibleCell(EmiScreenManager.ScreenSpace targetSpace, CellVisitor visitor) {
        if (targetSpace == null || visitor == null) return;
        for (SidebarSide side : SidebarSide.values()) {
            var panel = EmiScreenManager.getPanelFor(side);
            if (panel == null || !panel.isVisible()) continue;
            for (var space : panel.getSpaces()) {
                if (space != targetSpace || space.pageSize <= 0) continue;
                var stacks = space.getStacks();
                if (stacks == null || stacks.isEmpty()) return;
                boolean isMainSpace = space == panel.space;
                for (int row = 0; row < space.th; row++) {
                    int columns = space.getWidth(row);
                    for (int col = 0; col < columns; col++) {
                        int local = space.getRawOffset(col, row);
                        if (local < 0) continue;
                        int index = local + (isMainSpace ? space.pageSize * panel.page : 0);
                        if (index < 0 || index >= stacks.size()) continue;
                        EmiIngredient stack = stacks.get(index);
                        if (stack == null || stack.isEmpty()) continue;
                        visitor.accept(space, stack, space.getX(col, row), space.getY(col, row), col, row);
                    }
                }
                return;
            }
        }
    }
}
