package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorites;
import org.chatterjay.emiextend.client.BookmarkPageHelper;
import org.chatterjay.emiextend.client.BomTreePageHelper;
import org.chatterjay.emiextend.client.MobSeparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager$ScreenSpace")
public abstract class EmiScreenSpaceStacksMixin {

    @Shadow private int tw;

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
                var syn = EmiFavorites.syntheticFavorites;
                if (syn != null && !syn.isEmpty()) {
                    List<? extends EmiIngredient> favorites = EmiFavorites.favorites;
                    if (favorites != null) {
                        int pageSize = BookmarkPageHelper.getLastPageSize();
                        if (pageSize <= 0) {
                            var orig = cir.getReturnValue();
                            pageSize = Math.max(0, orig == null ? 0 : orig.size() - syn.size());
                        }
                        int page = BomTreePageHelper.getActiveFavoritePage();
                        int pageStart = Math.max(0, page * pageSize);
                        int pageEnd = pageSize <= 0 ? favorites.size() : Math.min(pageStart + pageSize, favorites.size());
                        int pageItems = 0;
                        for (int i = pageStart; i < pageEnd; i++) {
                            EmiIngredient ingredient = favorites.get(i);
                            if (!(ingredient instanceof dev.emi.emi.runtime.EmiFavorite fav)
                                    || !BookmarkPageHelper.isEmptyPlaceholder(fav)) {
                                pageItems++;
                            }
                        }

                        int insertAt = pageStart + pageItems;
                        List<EmiIngredient> result = new ArrayList<>(Math.max(favorites.size(), insertAt) + tw + syn.size());
                        for (int i = 0; i < Math.max(favorites.size(), insertAt); i++) {
                            if (i < favorites.size()) {
                                result.add(favorites.get(i));
                            } else {
                                result.add(EmiStack.EMPTY);
                            }
                        }

                        int remainder = pageItems % tw;
                        int padding = remainder > 0 ? tw - remainder : 0;
                        for (int i = 0; i < padding; i++) {
                            result.add(insertAt++, EmiStack.EMPTY);
                        }
                        result.addAll(insertAt, syn);
                        cir.setReturnValue(result);
                    }
                }
            }
        } catch (Exception e) {
            // Safety: don't break sidebar rendering
        }
    }
}
