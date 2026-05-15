package org.chatterjay.emiextend.client.handler;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.FakeSlot;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * After EMI recipe transfer fills pattern encoding slots, this handler:
 * 1. Checks user bookmarks for a matching item → uses the bookmarked variant
 * 2. If no bookmark match and the input is a tag, excludes backpack-derived
 *    variants (synthetic favorites) and picks the first clean variant
 */
public final class BookmarkPriorityHandler {

    private BookmarkPriorityHandler() {}

    private static Set<ResourceLocation> getSyntheticIds() {
        var syn = EmiFavorites.syntheticFavorites;
        ModLogger.debug("BookmarkPriority: syntheticFavorites size={}", syn == null ? 0 : syn.size());
        if (syn == null || syn.isEmpty()) return Set.of();
        var ids = new HashSet<ResourceLocation>();
        for (var s : syn) {
            if (s == null) continue;
            for (var stack : s.getEmiStacks()) {
                if (stack != null && !stack.isEmpty()) {
                    var id = stack.getId();
                    if (id != null) {
                        ids.add(id);
                        ModLogger.debug("BookmarkPriority: synthetic id={}", id);
                    }
                }
            }
        }
        return ids;
    }

    /**
     * For each processing input slot that was filled by the recipe transfer:
     * 1. If a user-bookmarked stack has the same item ID as any of the
     *    input's possible stacks, use the bookmarked stack
     * 2. Otherwise, if the input is a tag, exclude backpack-derived variants
     */
    public static void applyBookmarkPriority(FakeSlot[] slots, List<EmiIngredient> inputs) {
        ModLogger.info("BookmarkPriority: CALLED inputs size={}, slots={}", inputs == null ? 0 : inputs.size(), slots == null ? 0 : slots.length);

        if (inputs == null || inputs.isEmpty()) return;
        if (slots == null || slots.length == 0) return;

        var favorites = EmiFavorites.favorites;
        boolean hasFavorites = favorites != null && !favorites.isEmpty();
        ModLogger.debug("BookmarkPriority: favorites count={}, hasFavorites={}",
                favorites == null ? 0 : favorites.size(), hasFavorites);

        // Print bookmark IDs for debugging
        if (hasFavorites) {
            for (var fav : favorites) {
                if (fav == null) continue;
                for (var fs : fav.getEmiStacks()) {
                    if (fs == null) continue;
                    ModLogger.debug("BookmarkPriority: fav stack id={}, empty={}", fs.getId(), fs.isEmpty());
                }
            }
        }

        // Cache synthetic IDs for Phase 2
        Set<ResourceLocation> syntheticIds = null;

        int limit = Math.min(inputs.size(), slots.length);
        ModLogger.debug("BookmarkPriority: processing {} slots", limit);
        for (int i = 0; i < limit; i++) {
            var ingredient = inputs.get(i);
            ModLogger.debug("BookmarkPriority: slot[{}] ingredient={}", i, ingredient == null ? "null" : (ingredient.isEmpty() ? "empty" : "has-content"));
            if (ingredient == null || ingredient.isEmpty()) continue;

            var inputStacks = ingredient.getEmiStacks();
            ModLogger.debug("BookmarkPriority: slot[{}] inputStacks count={}", i, inputStacks == null ? 0 : inputStacks.size());
            if (inputStacks == null || inputStacks.isEmpty()) continue;

            // Log input stack IDs
            for (var s : inputStacks) {
                if (s != null) {
                    ModLogger.debug("BookmarkPriority: slot[{}] input id={}, empty={}", i, s.getId(), s.isEmpty());
                }
            }

            // Phase 1: Try to find a user-bookmarked match by item ID
            boolean replaced = false;
            if (hasFavorites) {
                for (var fav : favorites) {
                    if (fav == null) continue;
                    for (var favStack : fav.getEmiStacks()) {
                        if (favStack == null || favStack.isEmpty()) continue;
                        var favId = favStack.getId();
                        if (favId == null) continue;
                        var favItem = favStack.getItemStack();
                        if (favItem.isEmpty()) continue;

                        for (var inputStack : inputStacks) {
                            if (inputStack == null || inputStack.isEmpty()) continue;
                            var inputId = inputStack.getId();
                            boolean match = favId.equals(inputId);
                            ModLogger.debug("BookmarkPriority: slot[{}] compare fav={} input={} match={}",
                                    i, favId, inputId, match);
                            if (match) {
                                ModLogger.info("BookmarkPriority: slot[{}] replaced with bookmarked {} (slot index {})",
                                        i, favId, slots[i].index);
                                sendSetFilter(slots[i].index, favItem);
                                replaced = true;
                                break;
                            }
                        }
                        if (replaced) break;
                    }
                    if (replaced) break;
                }
            }

            if (replaced) continue;

            // Phase 2: No bookmark match — exclude synthetic (inventory) variants
            ModLogger.debug("BookmarkPriority: slot[{}] no bookmark match, trying Phase 2 (stacks={})", i, inputStacks.size());
            if (inputStacks.size() <= 1) continue;
            if (syntheticIds == null) {
                syntheticIds = getSyntheticIds();
            }
            ModLogger.debug("BookmarkPriority: syntheticIds count={}", syntheticIds.size());
            if (syntheticIds.isEmpty()) continue;

            for (var stack : inputStacks) {
                if (stack == null || stack.isEmpty()) continue;
                var id = stack.getId();
                boolean isSynthetic = id != null && syntheticIds.contains(id);
                ModLogger.debug("BookmarkPriority: slot[{}] checking stack id={} isSynthetic={}", i, id, isSynthetic);
                if (id != null && !isSynthetic) {
                    var itemStack = stack.getItemStack();
                    if (!itemStack.isEmpty()) {
                        ModLogger.info("BookmarkPriority: slot[{}] replaced with non-synthetic {} (slot index {})",
                                i, id, slots[i].index);
                        sendSetFilter(slots[i].index, itemStack);
                        break;
                    }
                }
            }
        }
    }

    private static void sendSetFilter(int slotIndex, ItemStack stack) {
        PacketDistributor.sendToServer(
                new InventoryActionPacket(
                        InventoryAction.SET_FILTER,
                        slotIndex,
                        stack.copy()));
    }
}
