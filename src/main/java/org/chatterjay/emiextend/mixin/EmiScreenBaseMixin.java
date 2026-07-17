package org.chatterjay.emiextend.mixin;

import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EmiScreenBase.class)
public class EmiScreenBaseMixin {
    @Invoker("<init>")
    private static EmiScreenBase emilink$newEmiScreenBase(Screen screen, Bounds bounds) {
        throw new AssertionError();
    }

    @Redirect(method = "of", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;isEmpty()Z"), remap = false)
    private static boolean emilink$redirectIsEmpty(NonNullList<Slot> slots, Screen screen) {
        // Check via reflection so optional mods can be absent
        try {
            Class<?> ccs = Class.forName("appeng.client.gui.me.crafting.CraftConfirmScreen");
            if (ccs.isInstance(screen)) return false;
        } catch (Exception ignored) {}
        try {
            Class<?> cpuScreen = Class.forName("appeng.client.gui.me.crafting.CraftingCPUScreen");
            if (cpuScreen.isInstance(screen)) return false;
        } catch (Exception ignored) {}
        return slots.isEmpty();
    }

    @Inject(method = "of", at = @At("RETURN"), cancellable = true, remap = false)
    private static void emilink$addSfmTextEditorBounds(Screen screen, CallbackInfoReturnable<EmiScreenBase> cir) {
        if (screen == null || !cir.getReturnValue().isEmpty()) return;

        String screenClass = screen.getClass().getName();
        if ("ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1".equals(screenClass)) {
            cir.setReturnValue(emilink$newEmiScreenBase(screen, new Bounds(
                    screen.width / 2 - 200,
                    screen.height / 2 - 110,
                    400,
                    230
            )));
        } else if ("ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2".equals(screenClass)) {
            cir.setReturnValue(emilink$newEmiScreenBase(screen, new Bounds(0, 0, screen.width, screen.height)));
        }
    }
}
