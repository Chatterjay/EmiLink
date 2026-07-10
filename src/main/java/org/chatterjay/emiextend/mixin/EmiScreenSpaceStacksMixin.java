package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorites;
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
                    var orig = cir.getReturnValue();
                    if (orig != null && orig.size() > syn.size()) {
                        // Build: [normal favorites] + [padding] + [synthetic favorites]
                        int normalCount = orig.size() - syn.size();
                        List<EmiIngredient> result = new ArrayList<>(normalCount + tw + syn.size());
                        // Normal favorites come first
                        for (int i = 0; i < normalCount; i++) {
                            result.add(orig.get(i));
                        }
                        // Pad to fill the current row
                        int remainder = normalCount % tw;
                        if (remainder > 0) {
                            for (int i = 0; i < tw - remainder; i++) {
                                result.add(EmiStack.EMPTY);
                            }
                        }
                        // Synthetic favorites start on a new row
                        result.addAll(syn);
                        cir.setReturnValue(result);
                    }
                }
            }
        } catch (Exception e) {
            // Safety: don't break sidebar rendering
        }
    }
}
