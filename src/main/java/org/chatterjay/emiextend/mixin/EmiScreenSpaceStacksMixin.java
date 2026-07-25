package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import org.chatterjay.emiextend.client.bookmark.BookmarkPageHelper;
import org.chatterjay.emiextend.client.bookmark.BomTreePageHelper;
import org.chatterjay.emiextend.client.bookmark.MobSeparator;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager$ScreenSpace")
public abstract class EmiScreenSpaceStacksMixin {

    @Shadow private int tw;

    @Shadow public int pageSize;

    @Shadow public int[] widths;

    @Shadow
    public abstract SidebarType getType();

    @Shadow
    public abstract List<? extends EmiIngredient> getStacks();

    @Shadow
    public abstract int getRawOffset(int x, int y);

    @Shadow
    public int tx;

    @Shadow
    public int ty;

    private static final ThreadLocal<Integer> EMILINK_RENDER_START_INDEX = ThreadLocal.withInitial(() -> 0);

    @Inject(method = "getStacks", at = @At("RETURN"), cancellable = true, remap = false)
    private void emilink$separateStacks(CallbackInfoReturnable<List<? extends EmiIngredient>> cir) {
        try {
            var type = getType();
            if (type == SidebarType.INDEX
                    && EmiLinkConfig.ENABLE_MOB_SEPARATOR.get()
                    && MobSeparator.hasMobItems()
                    && tw > 0) {
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

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void emilink$captureRenderStartIndex(EmiDrawContext context, int mouseX, int mouseY,
                                                 float delta, int startIndex, CallbackInfo ci) {
        EMILINK_RENDER_START_INDEX.set(startIndex);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void emilink$clearRenderStartIndex(EmiDrawContext context, int mouseX, int mouseY,
                                               float delta, int startIndex, CallbackInfo ci) {
        EMILINK_RENDER_START_INDEX.remove();
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Ldev/emi/emi/EmiRenderHelper;drawSlotHightlight(Ldev/emi/emi/runtime/EmiDrawContext;IIIII)V"),
            remap = false,
            require = 0)
    private void emilink$skipEmptyFavoriteHover(EmiDrawContext context, int x, int y, int w, int h, int z) {
        if (getType() == SidebarType.FAVORITES) {
            int local = getRawOffset((x - tx) / 18, (y - ty) / 18);
            int index = EMILINK_RENDER_START_INDEX.get() + local;
            var stacks = getStacks();
            if (local < 0 || index < 0 || index >= stacks.size()) {
                return;
            }
            var stack = stacks.get(index);
            if (stack == null || stack.isEmpty()) {
                return;
            }
        }
        EmiRenderHelper.drawSlotHightlight(context, x, y, w, h, z);
    }
}
