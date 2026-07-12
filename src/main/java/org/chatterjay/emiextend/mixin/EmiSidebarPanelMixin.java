package org.chatterjay.emiextend.mixin;

import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import org.chatterjay.emiextend.client.BookmarkPageHelper;
import org.chatterjay.emiextend.client.BomTreePageHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager$SidebarPanel", remap = false)
public abstract class EmiSidebarPanelMixin {
    @Shadow
    public ScreenSpace space;

    @Shadow
    public int page;

    @Shadow
    public abstract SidebarType getType();

    @Inject(method = "render", at = @At("HEAD"))
    private void emilink$activateBookmarkPageTree(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (getType() == SidebarType.FAVORITES && space != null && space.pageSize > 0) {
            BomTreePageHelper.activateFavoritePage(page, space.pageSize, space.widths);
        }
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"),
            require = 0)
    private int emilink$favoritePageCountForRender(List<?> stacks) {
        return emilink$favoritePageCountSize(stacks);
    }

    @Redirect(method = "wrapPage",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"),
            require = 0)
    private int emilink$favoritePageCountForWrap(List<?> stacks) {
        return emilink$favoritePageCountSize(stacks);
    }

    @Redirect(method = "hasMultiplePages",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"),
            require = 0)
    private int emilink$favoritePageCountForButtons(List<?> stacks) {
        return emilink$favoritePageCountSize(stacks);
    }

    @Redirect(method = "scroll",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"),
            require = 0)
    private int emilink$favoritePageCountForScroll(List<?> stacks) {
        return emilink$favoritePageCountSize(stacks);
    }

    private int emilink$favoritePageCountSize(List<?> stacks) {
        int size = stacks.size();
        if (getType() == SidebarType.FAVORITES && space != null && space.pageSize > 0) {
            BookmarkPageHelper.rememberPageSize(space.pageSize);
            BomTreePageHelper.activateFavoritePage(page, space.pageSize, space.widths);
            return BomTreePageHelper.virtualFavoriteSize(space.pageSize, space.widths);
        }
        return size;
    }
}
