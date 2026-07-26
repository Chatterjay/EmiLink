package org.chatterjay.emilink.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emilink.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.StringJoiner;

@Mixin(targets = "appeng.core.sync.packets.FillCraftingGridFromRecipePacket", remap = false)
public class FillCraftingGridFromRecipePacketMixin {
    @Inject(method = "serverPacketData", at = @At("HEAD"), require = 0)
    private void emilink$logFillCraftingGridStart(ServerPlayer player, CallbackInfo ci) {
        ModLogger.debug("AE_EMI_FILL_GRID start player={} recipeId={} craftMissing={} templates={} menu={}",
                player == null ? "null" : player.getGameProfile().getName(),
                emilink$getField("recipeId"),
                emilink$getField("craftMissing"),
                emilink$describeTemplates(emilink$getField("ingredientTemplates")),
                player == null || player.containerMenu == null ? "null" : player.containerMenu.getClass().getName());
    }

    @Inject(method = "serverPacketData", at = @At("RETURN"), require = 0)
    private void emilink$logFillCraftingGridEnd(ServerPlayer player, CallbackInfo ci) {
        ModLogger.debug("AE_EMI_FILL_GRID end player={} recipeId={} craftMissing={} menu={}",
                player == null ? "null" : player.getGameProfile().getName(),
                emilink$getField("recipeId"),
                emilink$getField("craftMissing"),
                player == null || player.containerMenu == null ? "null" : player.containerMenu.getClass().getName());
    }

    @Unique
    private Object emilink$getField(String name) {
        try {
            var field = this.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(this);
        } catch (Throwable t) {
            return "?";
        }
    }

    @Unique
    private static String emilink$describeTemplates(Object templates) {
        if (!(templates instanceof Iterable<?> iterable)) return String.valueOf(templates);
        var joiner = new StringJoiner(" | ", "[", "]");
        int i = 0;
        for (Object value : iterable) {
            if (value instanceof ItemStack stack && !stack.isEmpty()) {
                joiner.add(i + "=" + stack.getHoverName().getString() + " x" + stack.getCount());
            }
            i++;
        }
        return joiner.toString();
    }
}
