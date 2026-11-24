package org.chatterjay.emiextend.client.handler;

import appeng.api.stacks.GenericStack;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.menu.slot.FakeSlot;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bookmark priority for patterns encoded through EMI recipe transfer:
 * 1. If a user bookmark has the same ID as one of an input's possible stacks → use the bookmarked variant
 * 2. If no bookmark matches and the input has several variants, exclude backpack-derived variants
 *    (synthetic favorites)
 *
 * Processing patterns ({@link #preferBookmarked}) narrow each input's candidates before AE2 picks and
 * encodes them, because AE2 skips empty inputs and merges equal stacks: the encoded slots don't line up
 * with the recipe's inputs. Crafting patterns ({@link #applyBookmarkPriority}) keep one slot per grid
 * position, so their slots are rewritten after the transfer.
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
     * Processing patterns: each input's candidate stacks (from {@code EmiStackHelper.ofInputs}), narrowed to
     * the bookmarked variant (phase 1) or without synthetic variants (phase 2). AE2 then picks among them,
     * merges equal stacks and writes the slots as usual, with the recipe's amounts and fluids intact.
     */
    public static List<List<GenericStack>> preferBookmarked(List<List<GenericStack>> inputs) {
        if (inputs == null || inputs.isEmpty()) return inputs;

        // Bookmarks as AE keys, in bookmark order (the first matching bookmark wins, as for crafting)
        var favorites = EmiFavorites.favorites;
        List<GenericStack> bookmarked = new ArrayList<>();
        if (favorites != null) {
            for (var fav : favorites) {
                if (fav == null) continue;
                for (var favStack : fav.getEmiStacks()) {
                    if (favStack == null || favStack.isEmpty()) continue;
                    var generic = EmiStackHelper.toGenericStack(favStack);
                    if (generic != null) bookmarked.add(generic);
                }
            }
        }
        ModLogger.debug("BookmarkPriority: {} inputs, {} bookmarked stacks", inputs.size(), bookmarked.size());

        Set<ResourceLocation> syntheticIds = null;
        List<List<GenericStack>> result = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            var candidates = inputs.get(i);
            if (candidates == null || candidates.isEmpty()) {
                result.add(candidates);
                continue;
            }

            // Phase 1: the bookmarked variant, with the recipe's amount
            GenericStack chosen = null;
            for (var fav : bookmarked) {
                for (var candidate : candidates) {
                    if (fav.what().getType() == candidate.what().getType()
                            && fav.what().getId().equals(candidate.what().getId())) {
                        chosen = new GenericStack(fav.what(), candidate.amount());
                        break;
                    }
                }
                if (chosen != null) break;
            }
            if (chosen != null) {
                ModLogger.debug("BookmarkPriority: input[{}] uses bookmarked {}", i, chosen.what());
                result.add(List.of(chosen));
                continue;
            }

            // Phase 2: no bookmark match, leave out synthetic (inventory) variants
            if (candidates.size() > 1) {
                if (syntheticIds == null) syntheticIds = getSyntheticIds();
                if (!syntheticIds.isEmpty()) {
                    List<GenericStack> clean = new ArrayList<>();
                    for (var candidate : candidates) {
                        if (!syntheticIds.contains(candidate.what().getId())) clean.add(candidate);
                    }
                    if (!clean.isEmpty() && clean.size() < candidates.size()) {
                        ModLogger.debug("BookmarkPriority: input[{}] without synthetic variants: {} of {}",
                                i, clean.size(), candidates.size());
                        result.add(clean);
                        continue;
                    }
                }
            }
            result.add(candidates);
        }
        return result;
    }

    /**
     * Crafting patterns: for each crafting grid slot that was filled by the recipe transfer:
     * 1. If a user-bookmarked stack has the same item ID as any of the
     *    input's possible stacks, use the bookmarked stack
     * 2. Otherwise, if the input is a tag, exclude backpack-derived variants
     */
    public static void applyBookmarkPriority(FakeSlot[] slots, List<EmiIngredient> inputs) {
        ModLogger.debug("BookmarkPriority: CALLED inputs size={}, slots={}", inputs == null ? 0 : inputs.size(), slots == null ? 0 : slots.length);

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
                                ModLogger.debug("BookmarkPriority: slot[{}] replaced with bookmarked {} (slot index {})",
                                        i, favId, slots[i].index);
                                sendSetFilter(slots[i], favItem, ingredient.getAmount());
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
                        ModLogger.debug("BookmarkPriority: slot[{}] replaced with non-synthetic {} (slot index {})",
                                i, id, slots[i].index);
                        sendSetFilter(slots[i], itemStack, ingredient.getAmount());
                        break;
                    }
                }
            }
        }
    }

    private static void sendSetFilter(FakeSlot slot, ItemStack stack, long amount) {
        // Use the EMI recipe ingredient amount so the pattern gets the
        // correct count per cycle (client slot count is stale at this point).
        var copy = stack.copy();
        if (amount > 1) {
            copy.setCount((int) Math.min(amount, 64));
        }
        PacketDistributor.sendToServer(
                new InventoryActionPacket(
                        InventoryAction.SET_FILTER,
                        slot.index,
                        copy));
    }
}
