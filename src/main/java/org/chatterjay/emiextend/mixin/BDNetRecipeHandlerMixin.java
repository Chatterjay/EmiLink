package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.wintercogs.beyonddimensions.integration.module.emi.recipe.NetRecipeHandler", remap = false)
public class BDNetRecipeHandlerMixin {
    @Inject(method = "craft", at = @At("RETURN"), require = 0)
    private <T extends AbstractContainerMenu> void emilink$craftAfterCtrlFill(
            EmiRecipe recipe,
            EmiCraftContext<T> context,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (!net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            return;
        }
        if (context.getDestination() != EmiCraftContext.Destination.INVENTORY) {
            return;
        }

        PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, 2));
        ModLogger.debug("BD_QUICKCRAFT ctrl-fill craft-result sent recipe={} amount={}",
                recipe == null ? "null" : recipe.getId(), context.getAmount());
    }
}
