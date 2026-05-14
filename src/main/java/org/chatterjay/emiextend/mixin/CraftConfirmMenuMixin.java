package org.chatterjay.emiextend.mixin;

import appeng.menu.me.crafting.CraftConfirmMenu;
import org.chatterjay.emiextend.client.handler.BookmarkOnCancelHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftConfirmMenu.class)
public class CraftConfirmMenuMixin {

    @Inject(method = "goBack", at = @At("HEAD"))
    private void emilink$bookmarkMissingOnCancel(CallbackInfo ci) {
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        BookmarkOnCancelHandler.bookmarkMissingOnCancel(menu);
    }
}
