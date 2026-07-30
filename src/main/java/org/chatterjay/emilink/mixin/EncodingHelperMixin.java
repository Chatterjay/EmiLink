package org.chatterjay.emilink.mixin;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.client.handler.WrapAsBookHandler;
import org.chatterjay.emilink.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = EncodingHelper.class, remap = false)
public class EncodingHelperMixin {

    @Inject(method = "encodeProcessingRecipe", at = @At("HEAD"))
    private static void emilink$beforeEncodeProcessing(
            PatternEncodingTermMenu menu,
            List<List<GenericStack>> inputs,
            List<GenericStack> outputs,
            CallbackInfo ci) {
        ModLogger.debug("WB_FLOW EncodeHead: menu={} inputRows={} outputs={} wrapEnabled={} active={}",
                menu == null ? "null" : menu.getClass().getName(),
                inputs == null ? "null" : inputs.size(), outputs == null ? "null" : outputs.size(),
                safeWrapEnabled(), WrapAsBookHandler.isActive());
        debugInputs("HEAD", inputs);
        if (menu != null) {
            WrapAsBookHandler.debugDumpState("encode-head", menu, outputs);
        }
    }

    @Inject(method = "encodeProcessingRecipe", at = @At("RETURN"))
    private static void emilink$afterEncodeProcessing(
            PatternEncodingTermMenu menu,
            List<List<GenericStack>> inputs,
            List<GenericStack> outputs,
            CallbackInfo ci) {
        ModLogger.debug("WB_FLOW EncodeReturn: menu={} inputRows={} outputs={} wrapEnabled={} active={}",
                menu == null ? "null" : menu.getClass().getName(),
                inputs == null ? "null" : inputs.size(), outputs == null ? "null" : outputs.size(),
                safeWrapEnabled(), WrapAsBookHandler.isActive());
        debugInputs("RETURN", inputs);
        if (menu == null) {
            ModLogger.debug("WB_FLOW EncodeReturn: abort because menu is null");
            return;
        }
        WrapAsBookHandler.debugDumpState("encode-return-before-apply", menu, outputs);
        if (Config.ENABLE_WRAP_BOOK.get()) {
            WrapAsBookHandler.applyWrap(menu, inputs, outputs);
        } else {
            ModLogger.debug("WB_FLOW EncodeReturn: wrap book disabled by config");
        }
        WrapAsBookHandler.debugDumpState("encode-return-after-apply", menu, outputs);
    }

    private static boolean safeWrapEnabled() {
        try {
            return Config.ENABLE_WRAP_BOOK.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void debugInputs(String stage, List<List<GenericStack>> inputs) {
        if (inputs == null) {
            ModLogger.debug("WB_FLOW Encode{}: inputs=null", stage);
            return;
        }
        for (int row = 0; row < inputs.size(); row++) {
            List<GenericStack> choices = inputs.get(row);
            ModLogger.debug("WB_FLOW Encode{}: inputRow[{}] choices={}",
                    stage, row, choices == null ? "null" : choices.size());
            if (choices == null) continue;
            for (int col = 0; col < choices.size(); col++) {
                GenericStack stack = choices.get(col);
                Object what = stack == null ? null : stack.what();
                ModLogger.debug("WB_FLOW Encode{}: inputRow[{}][{}] amount={} whatClass={}",
                        stage, row, col, stack == null ? "null" : stack.amount(),
                        what == null ? "null" : what.getClass().getName());
            }
        }
    }
}
