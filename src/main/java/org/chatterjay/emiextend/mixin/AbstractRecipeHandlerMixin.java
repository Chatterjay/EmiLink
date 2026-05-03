package org.chatterjay.emiextend.mixin;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.CraftingTermMenu;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.integration.modules.emi.AbstractRecipeHandler")
public class AbstractRecipeHandlerMixin {

    @Unique
    private static final long SINGLE_CRAFT_SIGNAL = Long.MIN_VALUE;

    @Inject(method = "craft", at = @At("RETURN"), remap = false)
    private <T extends AEBaseMenu> void emilink$afterCraft(EmiRecipe recipe, EmiCraftContext<T> context, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;

        EmiCraftContext.Destination dest = context.getDestination();
        if (dest == EmiCraftContext.Destination.NONE) return;

        T menu = context.getScreenHandler();
        if (!(menu instanceof CraftingTermMenu ctm)) return;

        var outputSlots = ctm.getSlots(SlotSemantics.CRAFTING_RESULT);
        if (outputSlots.isEmpty()) return;
        int slotIndex = outputSlots.get(0).index;

        if (context.getAmount() > 1) {
            InventoryAction action = (dest == EmiCraftContext.Destination.INVENTORY)
                    ? InventoryAction.CRAFT_ALL
                    : InventoryAction.CRAFT_STACK;
            PacketDistributor.sendToServer(new InventoryActionPacket(action, slotIndex, 0));
        } else if (dest == EmiCraftContext.Destination.INVENTORY) {
            PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.CRAFT_SHIFT, slotIndex, SINGLE_CRAFT_SIGNAL));
        } else {
            PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.CRAFT_ITEM, slotIndex, 0));
        }
    }
}
