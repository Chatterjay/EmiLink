package org.chatterjay.emiextend.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.runtime.EmiDrawContext;
import org.chatterjay.emiextend.client.NetworkBadgeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hides EMI amount decorations while EmiLink owns the sidebar amount display. */
@Mixin(value = ItemEmiStack.class, remap = false)
public abstract class EmiItemStackRenderMixin {

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"),
            remap = false,
            require = 0)
    private void emilink$skipNativeAmount(GuiGraphics graphics, Font font, ItemStack stack,
                                           int x, int y, String text) {
        if (!NetworkBadgeRenderState.isSidebarRendering()
                || !NetworkBadgeRenderState.shouldReplaceNativeAmount(stack)) {
            graphics.renderItemDecorations(font, stack, x, y, text);
            return;
        }

        // Keep durability bars and other native decorations, but suppress only
        // the native count. EmiScreenSpaceMixin draws that count at half scale
        // on the right side next to the network amount.
        graphics.renderItemDecorations(font, stack.copyWithCount(1), x, y, null);
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Ldev/emi/emi/EmiRenderHelper;renderAmount(Ldev/emi/emi/runtime/EmiDrawContext;IILnet/minecraft/network/chat/Component;)V"),
            remap = false,
            require = 0)
    private void emilink$skipEmiAmount(EmiDrawContext context, int x, int y, Component amount) {
        if (!NetworkBadgeRenderState.isSidebarRendering()
                || !NetworkBadgeRenderState.ownsSidebarAmount()) {
            EmiRenderHelper.renderAmount(context, x, y, amount);
        }
    }
}
