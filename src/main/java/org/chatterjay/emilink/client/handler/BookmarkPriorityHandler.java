package org.chatterjay.emilink.client.handler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.FakeSlot;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.chatterjay.emilink.util.ModLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static void applyBookmarkPriority(FakeSlot[] slots, List<EmiIngredient> inputs) {
        ModLogger.info("BookmarkPriority: CALLED inputs size={}, slots={",
                inputs == null ? 0 : inputs.size(), slots == null ? 0 : slots.length);

        if (inputs == null || inputs.isEmpty()) return;
        if (slots == null || slots.length == 0) return;

        var favorites = EmiFavorites.favorites;
        boolean hasFavorites = favorites != null && !favorites.isEmpty();
        ModLogger.debug("BookmarkPriority: favorites count={}, hasFavorites={}",
                favorites == null ? 0 : favorites.size(), hasFavorites);

        if (hasFavorites) {
            for (var fav : favorites) {
                if (fav == null) continue;
                for (var fs : fav.getEmiStacks()) {
                    if (fs == null) continue;
                    ModLogger.debug("BookmarkPriority: fav stack id={}, empty={}", fs.getId(), fs.isEmpty());
                }
            }
        }

        Set<ResourceLocation> syntheticIds = null;

        int limit = Math.min(inputs.size(), slots.length);
        ModLogger.debug("BookmarkPriority: processing {} slots", limit);
        for (int i = 0; i < limit; i++) {
            var ingredient = inputs.get(i);
            ModLogger.debug("BookmarkPriority: slot[{}] ingredient={}", i,
                    ingredient == null ? "null" : (ingredient.isEmpty() ? "empty" : "has-content"));
            if (ingredient == null || ingredient.isEmpty()) continue;

            var inputStacks = ingredient.getEmiStacks();
            ModLogger.debug("BookmarkPriority: slot[{}] inputStacks count={}", i, inputStacks == null ? 0 : inputStacks.size());
            if (inputStacks == null || inputStacks.isEmpty()) continue;

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

    public static void applyFromGenericStack(FakeSlot[] slots, List<List<GenericStack>> genericInputs) {
        ModLogger.info("BookmarkPriority: applyFromGenericStack called, slots={}, inputs={}",
                slots == null ? 0 : slots.length,
                genericInputs == null ? 0 : genericInputs.size());

        if (genericInputs == null || genericInputs.isEmpty()) return;
        if (slots == null || slots.length == 0) return;

        var favorites = EmiFavorites.favorites;
        boolean hasFavorites = favorites != null && !favorites.isEmpty();
        ModLogger.info("BookmarkPriority: favorites count={}, hasFavorites={}",
                favorites == null ? 0 : favorites.size(), hasFavorites);

        if (hasFavorites) {
            for (var fav : favorites) {
                if (fav == null) continue;
                for (var fs : fav.getEmiStacks()) {
                    if (fs == null) continue;
                    ModLogger.info("BookmarkPriority: fav id={}, empty={}", fs.getId(), fs.isEmpty());
                }
            }
        }

        Set<ResourceLocation> syntheticIds = null;

        int limit = Math.min(genericInputs.size(), slots.length);
        ModLogger.info("BookmarkPriority: processing {} entries, genericInputs.size={}, slots.length={}",
                limit, genericInputs.size(), slots.length);
        for (int i = 0; i < limit; i++) {
            var alternatives = genericInputs.get(i);
            ModLogger.info("BookmarkPriority: slot[{}] alternatives={} null={}",
                    i, alternatives == null ? 0 : alternatives.size(), alternatives == null);
            if (alternatives == null || alternatives.isEmpty()) continue;

            boolean replaced = false;
            if (hasFavorites) {
                for (var alt : alternatives) {
                    if (alt == null) {
                        ModLogger.info("BookmarkPriority: slot[{}] alt is null", i);
                        continue;
                    }
                    var what = alt.what();
                    ModLogger.info("BookmarkPriority: slot[{}] what class={}",
                            i, what == null ? "null" : what.getClass().getName());
                    if (!(what instanceof AEItemKey itemKey)) {
                        ModLogger.info("BookmarkPriority: slot[{}] what is not AEItemKey, type={}",
                                i, what == null ? "null" : what.getClass().getSimpleName());
                        continue;
                    }
                    ItemStack stack = itemKey.toStack();
                    ModLogger.info("BookmarkPriority: slot[{}] stack={} empty={}",
                            i, stack.isEmpty() ? "empty" : stack.getHoverName().getString(), stack.isEmpty());
                    if (stack.isEmpty()) continue;

                    var stackId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    ModLogger.info("BookmarkPriority: slot[{}] stackId={}", i, stackId);
                    if (stackId == null) continue;

                    for (var fav : favorites) {
                        if (fav == null) continue;
                        ModLogger.info("BookmarkPriority: slot[{}] checking fav stacks count={}",
                                i, fav.getEmiStacks().size());
                        for (var favStack : fav.getEmiStacks()) {
                            if (favStack == null || favStack.isEmpty()) continue;
                            boolean match = stackId.equals(favStack.getId());
                            ModLogger.info("BookmarkPriority: slot[{}] compare stackId={} favId={} match={}",
                                    i, stackId, favStack.getId(), match);
                            if (match) {
                                ModLogger.info("BookmarkPriority: slot[{}] MATCHED bookmark {}, sending SET_FILTER",
                                        i, stackId);
                                sendSetFilter(slots[i].index, stack);
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

            // Phase 2: Exclude synthetic (inventory) variants
            if (alternatives.size() <= 1) continue;
            if (syntheticIds == null) {
                syntheticIds = getSyntheticIds();
            }
            if (syntheticIds.isEmpty()) continue;

            for (var alt : alternatives) {
                if (alt == null) continue;
                var what = alt.what();
                if (!(what instanceof AEItemKey itemKey)) continue;
                ItemStack stack = itemKey.toStack();
                if (stack.isEmpty()) continue;

                var stackId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (stackId == null) continue;
                if (!syntheticIds.contains(stackId)) {
                    ModLogger.debug("BookmarkPriority: slot[{}] matched non-synthetic {}", i, stackId);
                    sendSetFilter(slots[i].index, stack);
                    break;
                }
            }
        }
    }

    private static void sendSetFilter(int slotIndex, ItemStack stack) {
        NetworkHandler.instance().sendToServer(
                new InventoryActionPacket(InventoryAction.SET_FILTER, slotIndex, stack.copy()));
    }
}
