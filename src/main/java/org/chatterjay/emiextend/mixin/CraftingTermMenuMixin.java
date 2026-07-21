package org.chatterjay.emiextend.mixin;

import appeng.helpers.IMenuCraftingPacket;
import appeng.menu.me.items.CraftingTermMenu;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.StringJoiner;

@Mixin(CraftingTermMenu.class)
public class CraftingTermMenuMixin {
    @Inject(method = "startAutoCrafting", at = @At("HEAD"), remap = false)
    private void emilink$logStartAutoCrafting(List<IMenuCraftingPacket.AutoCraftEntry> toCraft, CallbackInfo ci) {
        ModLogger.debug("AE_EMI_CTRL_CRAFT start-autocrafting entries={} detail={}",
                toCraft == null ? 0 : toCraft.size(),
                emilink$describeAutoCraftEntries(toCraft));
    }

    @Unique
    private static String emilink$describeAutoCraftEntries(List<IMenuCraftingPacket.AutoCraftEntry> toCraft) {
        if (toCraft == null) return "null";
        var joiner = new StringJoiner(" | ", "[", "]");
        for (var entry : toCraft) {
            var stack = entry.what().toStack();
            joiner.add(stack.getDisplayName().getString() + " slots=" + entry.slots());
        }
        return joiner.toString();
    }
}
