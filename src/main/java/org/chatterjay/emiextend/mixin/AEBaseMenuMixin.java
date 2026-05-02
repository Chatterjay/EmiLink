package org.chatterjay.emiextend.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import org.chatterjay.emiextend.util.EmiCraftHelper;

@Mixin(AEBaseMenu.class)
public class AEBaseMenuMixin {

    @Unique
    private static final long SINGLE_CRAFT_SIGNAL = Long.MIN_VALUE;

    @Inject(method = "doAction", at = @At(value = "INVOKE", target = "Lappeng/menu/slot/CraftingTermSlot;doClick(Lappeng/helpers/InventoryAction;Lnet/minecraft/world/entity/player/Player;)V"), remap = false)
    private void emiextend$signalSingleCraft(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        if (id == SINGLE_CRAFT_SIGNAL && action == InventoryAction.CRAFT_SHIFT) {
            EmiCraftHelper.markSingleCraft();
        }
    }

    @Inject(method = "doAction", at = @At("RETURN"), remap = false)
    private void emiextend$cleanupFlag(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        EmiCraftHelper.clear();
    }
}
