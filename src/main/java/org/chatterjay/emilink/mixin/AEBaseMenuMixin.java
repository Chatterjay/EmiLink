package org.chatterjay.emilink.mixin;

import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import net.minecraft.server.level.ServerPlayer;
import org.chatterjay.emilink.server.IPNLockHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseMenu.class)
public class AEBaseMenuMixin {

    @Unique
    private final IPNLockHandler emilink$lockHandler = new IPNLockHandler();

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
    }
}
