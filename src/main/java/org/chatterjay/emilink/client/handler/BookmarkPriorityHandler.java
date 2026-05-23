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
        if (syn == null || syn.isEmpty()) return Set.of();
        var ids = new HashSet<ResourceLocation>();
        for (var s : syn) {
            if (s == null) continue;
            for (var stack : s.getEmiStacks()) {
                if (stack != null && !stack.isEmpty()) {
                    var id = stack.getId();
                    if (id != null) ids.add(id);
                }
            }
        }
        return ids;
    }

    public static void applyBookmarkPriority(FakeSlot[] slots, List<EmiIngredient> inputs) {
        if (inputs == null || inputs.isEmpty()) return;
        if (slots == null || slots.length == 0) return;

        var favorites = EmiFavorites.favorites;
        boolean hasFavorites = favorites != null && !favorites.isEmpty();

        Set<ResourceLocation> syntheticIds = null;
        int replaced = 0;

        int limit = Math.min(inputs.size(), slots.length);
        for (int i = 0; i < limit; i++) {
            var ingredient = inputs.get(i);
            if (ingredient == null || ingredient.isEmpty()) continue;

            var inputStacks = ingredient.getEmiStacks();
            if (inputStacks == null || inputStacks.isEmpty()) continue;

            // Phase 1: Try to find a user-bookmarked match by item ID
            boolean didReplace = false;
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
                            if (favId.equals(inputStack.getId())) {
                                sendSetFilter(slots[i].index, favItem);
                                didReplace = true;
                                replaced++;
                                break;
                            }
                        }
                        if (didReplace) break;
                    }
                    if (didReplace) break;
                }
            }

            if (didReplace) continue;

            // Phase 2: No bookmark match — exclude synthetic (inventory) variants
            if (inputStacks.size() <= 1) continue;
            if (syntheticIds == null) syntheticIds = getSyntheticIds();
            if (syntheticIds.isEmpty()) continue;

            for (var stack : inputStacks) {
                if (stack == null || stack.isEmpty()) continue;
                var id = stack.getId();
                if (id != null && !syntheticIds.contains(id)) {
                    var itemStack = stack.getItemStack();
                    if (!itemStack.isEmpty()) {
                        sendSetFilter(slots[i].index, itemStack);
                        replaced++;
                        break;
                    }
                }
            }
        }

        if (replaced > 0) {
            ModLogger.info("BookmarkPriority: replaced {} slots with favorites", replaced);
        }
    }

    public static void applyFromGenericStack(FakeSlot[] slots, List<List<GenericStack>> genericInputs) {
        if (genericInputs == null || genericInputs.isEmpty()) return;
        if (slots == null || slots.length == 0) return;

        var favorites = EmiFavorites.favorites;
        boolean hasFavorites = favorites != null && !favorites.isEmpty();

        Set<ResourceLocation> syntheticIds = null;
        int replaced = 0;

        int limit = Math.min(genericInputs.size(), slots.length);
        for (int i = 0; i < limit; i++) {
            var alternatives = genericInputs.get(i);
            if (alternatives == null || alternatives.isEmpty()) continue;

            boolean didReplace = false;
            if (hasFavorites) {
                for (var alt : alternatives) {
                    if (alt == null) continue;
                    var what = alt.what();
                    if (!(what instanceof AEItemKey itemKey)) continue;
                    ItemStack stack = itemKey.toStack();
                    if (stack.isEmpty()) continue;

                    var stackId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (stackId == null) continue;

                    for (var fav : favorites) {
                        if (fav == null) continue;
                        for (var favStack : fav.getEmiStacks()) {
                            if (favStack == null || favStack.isEmpty()) continue;
                            if (stackId.equals(favStack.getId())) {
                                sendSetFilter(slots[i].index, stack);
                                didReplace = true;
                                replaced++;
                                break;
                            }
                        }
                        if (didReplace) break;
                    }
                    if (didReplace) break;
                }
            }

            if (didReplace) continue;

            // Phase 2: Exclude synthetic (inventory) variants
            if (alternatives.size() <= 1) continue;
            if (syntheticIds == null) syntheticIds = getSyntheticIds();
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
                    sendSetFilter(slots[i].index, stack);
                    replaced++;
                    break;
                }
            }
        }

        if (replaced > 0) {
            ModLogger.info("BookmarkPriority: replaced {} slots with favorites", replaced);
        }
    }

    private static void sendSetFilter(int slotIndex, ItemStack stack) {
        NetworkHandler.instance().sendToServer(
                new InventoryActionPacket(InventoryAction.SET_FILTER, slotIndex, stack.copy()));
    }
}
