package org.chatterjay.emiextend.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import org.chatterjay.emiextend.server.IPNLockHandler;
import org.chatterjay.emiextend.util.EmiCraftHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseMenu.class)
public class AEBaseMenuMixin {

    @Unique
    private static final long SINGLE_CRAFT_SIGNAL = Long.MIN_VALUE;

    @Unique
    private final IPNLockHandler emilink$lockHandler = new IPNLockHandler();

    @Inject(method = "doAction", at = @At(value = "INVOKE", target = "Lappeng/menu/slot/CraftingTermSlot;doClick(Lappeng/helpers/InventoryAction;Lnet/minecraft/world/entity/player/Player;)V"), remap = false)
    private void emilink$signalSingleCraft(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        if (id == SINGLE_CRAFT_SIGNAL && action == InventoryAction.CRAFT_SHIFT) {
            EmiCraftHelper.markSingleCraft();
        }
    }

    @Inject(method = "doAction", at = @At("HEAD"), remap = false)
    private void emilink$beforeDoAction(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        if (action == InventoryAction.MOVE_REGION) {
            emilink$lockHandler.beforeMoveRegion(player);
        }
    }

    @Inject(method = "doAction", at = @At("RETURN"), remap = false)
    private void emilink$afterDoAction(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        if (emilink$lockHandler.isActive()) {
            emilink$lockHandler.afterMoveRegion(player);
        }
        EmiCraftHelper.clear();
    }
}
