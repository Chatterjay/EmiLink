package org.chatterjay.emilink.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emilink.network.NetworkHandler;
import org.chatterjay.emilink.network.packet.c2s.BDActionPacket;
import org.chatterjay.emilink.util.ModLogger;
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
        if (!cir.getReturnValueZ()) return;
        if (!Screen.hasControlDown()) return;
        if (context.getDestination() != EmiCraftContext.Destination.INVENTORY) return;

        if (NetworkHandler.sendToServer(new BDActionPacket(ItemStack.EMPTY, 2))) {
            ModLogger.debug("BD_QUICKCRAFT ctrl-fill result craft sent recipe={} amount={}",
                    recipe == null ? "null" : recipe.getId(), context.getAmount());
        }
    }
}
