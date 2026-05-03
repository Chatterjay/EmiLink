package org.chatterjay.emiextend.mixin;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import dev.emi.emi.screen.EmiScreenBase;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EmiScreenBase.class)
public class EmiScreenBaseMixin {

    @Redirect(method = "of", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;isEmpty()Z"), remap = false)
    private static boolean emilink$redirectIsEmpty(NonNullList<Slot> slots, Screen screen) {
        if (screen instanceof CraftConfirmScreen) {
            return false;
        }
        return slots.isEmpty();
    }
}
