package org.chatterjay.emiextend.mixin;

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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftConfirmMenu.class)
public class CraftConfirmMenuMixin {

    @Inject(method = "goBack", at = @At("HEAD"))
    private void emilink$bookmarkMissingOnCancel(CallbackInfo ci) {
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        var player = menu.getPlayerInventory().player;
        // Only on logical client with Shift held
        if (player == null || !player.level().isClientSide() || !Screen.hasShiftDown()) {
            return;
        }

        CraftingPlanSummary plan = menu.getPlan();
        if (plan == null) {
            return;
        }

        // Lazy-build EMI index lookup for non-item/fluid keys (chemicals, etc.)
        java.util.Map<ResourceLocation, EmiStack> emiLookup = null;

        for (var entry : plan.getEntries()) {
            if (entry.getMissingAmount() == 0) {
                continue;
            }
            AEKey what = entry.getWhat();
            if (what instanceof AEItemKey itemKey) {
                EmiFavorites.addFavorite(EmiStack.of(itemKey.toStack()));
            } else if (what instanceof AEFluidKey fluidKey) {
                EmiFavorites.addFavorite(EmiStack.of(fluidKey.getFluid()));
            } else {
                if (emiLookup == null) {
                    emiLookup = new java.util.HashMap<>();
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
