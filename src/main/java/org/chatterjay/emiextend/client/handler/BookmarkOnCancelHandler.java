package org.chatterjay.emiextend.client.handler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * When a crafting plan is cancelled with Shift held, bookmarks all missing
 * items to EMI so the player can easily find what they need.
 */
public final class BookmarkOnCancelHandler {

    private BookmarkOnCancelHandler() {}

    /**
     * Bookmark missing items from the crafting plan. Should only be called
     * on the client thread when Shift is held.
     */
    public static void bookmarkMissingOnCancel(CraftConfirmMenu menu) {
        var player = menu.getPlayerInventory().player;
        if (player == null || !player.level().isClientSide() || !Screen.hasShiftDown()) {
            return;
        }

        CraftingPlanSummary plan = menu.getPlan();
        if (plan == null) return;

        // Lazy-build EMI index lookup for non-item/fluid keys (chemicals, etc.)
        Map<ResourceLocation, EmiStack> emiLookup = null;

        for (var entry : plan.getEntries()) {
            if (entry.getMissingAmount() == 0) continue;

            AEKey what = entry.getWhat();
            if (what instanceof AEItemKey itemKey) {
                EmiFavorites.addFavorite(EmiStack.of(itemKey.toStack()));
            } else if (what instanceof AEFluidKey fluidKey) {
                EmiFavorites.addFavorite(EmiStack.of(fluidKey.getFluid()));
            } else {
                if (emiLookup == null) {
                    emiLookup = new HashMap<>();
                    for (var es : EmiApi.getIndexStacks()) {
                        var id = es.getId();
                        if (id != null) {
                            emiLookup.put(id, es);
                        }
                    }
                }
                var found = emiLookup.get(what.getId());
                if (found != null) {
                    EmiFavorites.addFavorite(found);
                }
            }
        }
    }
}
