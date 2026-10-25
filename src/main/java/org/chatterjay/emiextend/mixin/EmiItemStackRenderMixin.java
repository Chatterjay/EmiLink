package org.chatterjay.emiextend.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import org.chatterjay.emiextend.client.NetworkBadgeRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hides the native item count EMI decorates sidebar icons with while EmiLink owns
 * the sidebar amount display.
 * <p>
 * Stack amounts (items, fluids, chemicals and other AE keys alike) go through
 * {@code EmiRenderHelper.renderAmount} and are taken over by
 * {@link EmiRenderHelperMixin}; this mixin only covers the item decoration text
 * that is drawn directly by EMIs item stack renderer.
 */
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
        // the native count. EmiScreenSpaceMixin draws the network amount instead.
        graphics.renderItemDecorations(font, stack.copyWithCount(1), x, y, null);
    }
}
