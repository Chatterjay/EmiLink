package org.chatterjay.emiextend.mixin;

import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.StringJoiner;

@Mixin(FillCraftingGridFromRecipePacket.class)
public class FillCraftingGridFromRecipePacketMixin {
    @Inject(method = "handleOnServer", at = @At("HEAD"), remap = false)
    private void emilink$logFillCraftingGridStart(ServerPlayer player, CallbackInfo ci) {
        var packet = (FillCraftingGridFromRecipePacket) (Object) this;
        ModLogger.debug("AE_EMI_CTRL_CRAFT server-fill-start player={} recipeId={} craftMissing={} templates={} menu={}",
                player == null ? "null" : player.getGameProfile().getName(),
                packet.recipeId(),
                packet.craftMissing(),
                emilink$describeTemplates(packet),
                player == null || player.containerMenu == null ? "null" : player.containerMenu.getClass().getName());
    }

    @Inject(method = "handleOnServer", at = @At("RETURN"), remap = false)
    private void emilink$logFillCraftingGridEnd(ServerPlayer player, CallbackInfo ci) {
        var packet = (FillCraftingGridFromRecipePacket) (Object) this;
        ModLogger.debug("AE_EMI_CTRL_CRAFT server-fill-end player={} recipeId={} craftMissing={} menu={}",
                player == null ? "null" : player.getGameProfile().getName(),
                packet.recipeId(),
                packet.craftMissing(),
                player == null || player.containerMenu == null ? "null" : player.containerMenu.getClass().getName());
    }

    @Unique
    private static String emilink$describeTemplates(FillCraftingGridFromRecipePacket packet) {
        var joiner = new StringJoiner(" | ", "[", "]");
        for (int i = 0; i < packet.ingredientTemplates().size(); i++) {
            ItemStack stack = packet.ingredientTemplates().get(i);
            if (!stack.isEmpty()) {
                joiner.add(i + "=" + stack.getDisplayName().getString() + " x" + stack.getCount());
            }
        }
        return joiner.toString();
    }
}
