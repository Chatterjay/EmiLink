package org.chatterjay.emilink.mixin;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.client.handler.WrapAsBookHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = EncodingHelper.class, remap = false)
public class EncodingHelperMixin {

    @Inject(method = "encodeProcessingRecipe", at = @At("RETURN"))
    private static void emilink$afterEncodeProcessing(
            PatternEncodingTermMenu menu,
            List<List<GenericStack>> inputs,
            List<GenericStack> outputs,
            CallbackInfo ci) {
        if (menu == null) return;
        if (Config.ENABLE_WRAP_BOOK.get()) {
            WrapAsBookHandler.applyWrap(menu, outputs);
        }
    }
}
