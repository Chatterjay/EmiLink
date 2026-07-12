package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;
import org.chatterjay.emiextend.client.bookmark.BookmarkPageHelper;
import org.chatterjay.emiextend.client.bookmark.BomTreePageHelper;
import org.chatterjay.emiextend.client.bookmark.MobSeparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager$ScreenSpace")
public abstract class EmiScreenSpaceStacksMixin {

    @Shadow private int tw;

    @Shadow public int pageSize;

    @Shadow public int[] widths;

    @Shadow
    public abstract SidebarType getType();

    @Inject(method = "getStacks", at = @At("RETURN"), cancellable = true, remap = false)
    private void emilink$separateStacks(CallbackInfoReturnable<List<? extends EmiIngredient>> cir) {
        try {
            var type = getType();
            if (type == SidebarType.INDEX && MobSeparator.hasMobItems() && tw > 0) {
                var original = cir.getReturnValue();
                if (original != null && !original.isEmpty()) {
                    cir.setReturnValue(MobSeparator.separateStacks(original, tw));
                }
            } else if (type == SidebarType.FAVORITES && tw > 0) {
                if (pageSize > 0) {
                    BookmarkPageHelper.rememberPageSize(pageSize);
                    cir.setReturnValue(BomTreePageHelper.buildVirtualFavoriteStacks(pageSize, widths));
                }
            }
        } catch (Exception e) {
            // Safety: don't break sidebar rendering
        }
    }
}
