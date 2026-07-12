package org.chatterjay.emiextend.mixin;

import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import org.chatterjay.emiextend.client.BookmarkPageHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager$SidebarPanel", remap = false)
public abstract class EmiSidebarPanelMixin {
    @Shadow
    public ScreenSpace space;

    @Shadow
    public abstract SidebarType getType();

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
            return Math.max(size, space.pageSize * BookmarkPageHelper.MIN_FAVORITE_PAGES);
        }
        return size;
    }
}
