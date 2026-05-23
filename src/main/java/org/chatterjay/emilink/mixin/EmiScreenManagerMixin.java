package org.chatterjay.emilink.mixin;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emilink.client.handler.EmiInteractionHandler;
import org.chatterjay.emilink.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.class, remap = false)
public class EmiScreenManagerMixin {
    @Shadow
    private static int lastMouseX;
    @Shadow
    private static int lastMouseY;

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, require = 0)
    private static void emilink$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        ModLogger.info("EmiScreenManagerMixin: keyPressed keyCode={} scanCode={} handled={}",
                keyCode, scanCode, EmiInteractionHandler.onKeyPressed(keyCode, scanCode, modifiers, lastMouseX, lastMouseY));
        if (EmiInteractionHandler.onKeyPressed(keyCode, scanCode, modifiers, lastMouseX, lastMouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        ModLogger.info("EmiScreenManagerMixin: mouseReleased button={} at ({},{})", button, mouseX, mouseY);
        if (EmiInteractionHandler.onMouseReleased(mouseX, mouseY, button)) {
            ModLogger.info("EmiScreenManagerMixin: mouseReleased handled by EmiInteractionHandler");
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void emilink$onGetHoveredStack(int mouseX, int mouseY, boolean checkSidebar, boolean includeBatches, CallbackInfoReturnable<EmiStackInteraction> cir) {
        // Only override if EMI didn't find a stack
        if (cir.getReturnValue() != null && !cir.getReturnValue().isEmpty()) return;

        var screen = Minecraft.getInstance().screen;
        if (screen == null) return;

        // Check for AE2 screens with custom item rendering via getStackUnderMouse
        try {
            var method = screen.getClass().getMethod("getStackUnderMouse", double.class, double.class);
            Object swb = method.invoke(screen, (double) mouseX, (double) mouseY);
            if (swb == null) return;

            Object genericStack = swb.getClass().getMethod("stack").invoke(swb);
            if (genericStack == null) return;

            Object what = genericStack.getClass().getMethod("what").invoke(genericStack);
            if (what == null) return;

            // Try AEItemKey -> ItemStack
            try {
                Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
                if (aeItemKeyClass.isInstance(what)) {
                    ItemStack itemStack = (ItemStack) aeItemKeyClass.getMethod("toStack").invoke(what);
                    if (!itemStack.isEmpty()) {
                        cir.setReturnValue(new EmiStackInteraction(EmiStack.of(itemStack)));
                        return;
                    }
                }
            } catch (Exception ignored) {}

            // Try generic AEKey lookup via ResourceLocation
            try {
                var idMethod = what.getClass().getMethod("getId");
                Object id = idMethod.invoke(what);
                if (id instanceof ResourceLocation rl) {
                    for (var es : EmiApi.getIndexStacks()) {
                        if (rl.equals(es.getId())) {
                            cir.setReturnValue(new EmiStackInteraction(es));
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }
}
