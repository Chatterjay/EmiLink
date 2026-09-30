package org.chatterjay.emiextend.client.handler;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.client.Minecraft;
import org.chatterjay.emiextend.client.BatchMark;
import org.chatterjay.emiextend.client.EmiSlotDropHelper;
import org.chatterjay.emiextend.client.EmiStackId;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.util.ModLogger;

/**
 * Batch marking follow-up.
 * <p>
 * The user drags one of the marked sidebar stacks onto a filter slot by hand.
 * EmiLink performs that drop itself — which tells us whether a slot accepted the
 * stack — and then fills the remaining marks into the following slots in the
 * order they were selected. When the screen runs out of accepting slots, the
 * remaining marks are discarded.
 */
public final class BatchMarkFill {

    /** True when the press that started the current drag was inside the favorites bar. */
    private static boolean dragStartedInFavorites = false;

    private BatchMarkFill() {}

    /**
     * Remember where a stack drag starts. Marks belong to the favorites bar, so a
     * drag started anywhere else (for example the index view) must not trigger the
     * batch fill.
     */
    public static void onMousePressed(double mouseX, double mouseY) {
        var panel = dev.emi.emi.screen.EmiScreenManager.getHoveredPanel((int) mouseX, (int) mouseY);
        dragStartedInFavorites = panel != null && panel.isVisible()
                && panel.getType() == dev.emi.emi.config.SidebarType.FAVORITES;
    }

    /**
     * Handles a release that drops a marked stack onto a slot.
     *
     * @return true when the drop was performed here, so the caller must stop EMI
     *         from dropping the same stack a second time.
     */
    public static boolean tryHandleMarkedDrop(double mouseX, double mouseY, int button,
                                              EmiIngredient dragged) {
        if (!EmiLinkConfig.ENABLE_BATCH_MARK.get()) return false;
        // With the fill disabled the drop stays a single mark and EMI performs it.
        if (!EmiLinkConfig.ENABLE_BATCH_MARK_FILL.get()) return false;
        if (button != 0 || dragged == null || dragged.isEmpty()) return false;
        if (!dragStartedInFavorites) return false;

        var handled = EmiApi.getHandledScreen();
        // Only take over when EMI would drop onto this container itself. With an
        // EMI overlay on top, keep EMI's own behaviour.
        if (handled == null || Minecraft.getInstance().screen != handled) return false;

        String draggedId = EmiStackId.of(dragged);
        if (draggedId == null || !BatchMark.isMarkedId(draggedId)) return false;

        int anchorIndex = EmiSlotDropHelper.slotIndexAt(handled, mouseX, mouseY);
        if (anchorIndex < 0) return false;

        int anchorX = (int) mouseX;
        int anchorY = (int) mouseY;
        if (!EmiSlotDropHelper.dropOnScreen(handled, dragged, anchorX, anchorY)) {
            // The slot under the cursor does not accept this stack; leave the
            // release to EMI so its normal handling still applies.
            return false;
        }

        fillRemainingMarks(handled, draggedId, anchorIndex);
        return true;
    }

    /**
     * Fill the other marked stacks into the slots after the one the user dropped
     * onto, in selection order. Stops as soon as no further slot accepts a stack.
     */
    private static void fillRemainingMarks(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> handled,
                                           String draggedId, int anchorIndex) {
        List<String> remaining = BatchMark.orderedIds(draggedId);
        if (remaining.isEmpty()) return;

        ModLogger.debug("BatchMark: dropped {} on slot {}, filling {} remaining marks",
                draggedId, anchorIndex, remaining.size());

        Map<String, EmiIngredient> resolved = resolveMarkedStacks(remaining);
        int from = anchorIndex + 1;
        int filled = 0;
        for (String id : remaining) {
            EmiIngredient stack = resolved.get(id);
            if (stack == null) {
                ModLogger.debug("BatchMark: {} is no longer available in EMI, skipping", id);
                continue;
            }
            int at = EmiSlotDropHelper.fillFirstEmptySlot(handled, stack, from);
            if (at < 0) {
                ModLogger.debug("BatchMark: no slot accepted {}; {} marks discarded",
                        id, remaining.size() - filled);
                break;
            }
            from = at + 1;
            filled++;
        }
        ModLogger.debug("BatchMark: batch fill finished anchor={} filled={}/{}",
                anchorIndex, filled, remaining.size());
    }

    /**
     * Resolve the marked ids back into EMI stacks: one pass over the index, then
     * the favorites list for entries that are not part of the index.
     */
    private static Map<String, EmiIngredient> resolveMarkedStacks(List<String> ids) {
        var wanted = new HashSet<>(ids);
        var found = new LinkedHashMap<String, EmiIngredient>();
        try {
            for (var stack : EmiApi.getIndexStacks()) {
                if (stack == null || stack.isEmpty()) continue;
                var id = stack.getId();
                if (id == null) continue;
                String key = id.toString();
                if (wanted.contains(key)) {
                    found.putIfAbsent(key, stack);
                }
            }
        } catch (Throwable error) {
            ModLogger.debug("BatchMark: index lookup failed: {}", error.toString());
        }
        if (found.size() < wanted.size()) {
            try {
                if (EmiFavorites.favorites != null) {
                    for (var favorite : EmiFavorites.favorites) {
                        String key = EmiStackId.of(favorite);
                        if (key != null && wanted.contains(key)) {
                            found.putIfAbsent(key, favorite);
                        }
                    }
                }
            } catch (Throwable error) {
                ModLogger.debug("BatchMark: favorite lookup failed: {}", error.toString());
            }
        }
        return found;
    }
}
