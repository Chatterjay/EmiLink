package org.chatterjay.emiextend.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.storage.MEStorage;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import appeng.util.Platform;
import appeng.util.inv.PlayerInternalInventory;
import org.chatterjay.emiextend.util.EmiCraftHelper;

@Mixin(CraftingTermSlot.class)
public class CraftingTermSlotMixin {

    @Shadow(remap = false)
    private MEStorage storage;

    @Inject(method = "doClick", at = @At("HEAD"), remap = false, cancellable = true)
    private void emiextend$handleSingleCraft(InventoryAction action, Player player, CallbackInfo ci) {
        if (!EmiCraftHelper.checkSingleCraft()) return;
        EmiCraftHelper.clear();

        var all = storage.getAvailableStacks();
        ItemStack result = ((CraftingTermSlotInvoker) this).emilink$invokeCraftItem(player, storage, all);

        if (!result.isEmpty()) {
            ItemStack extra = new PlayerInternalInventory(player.getInventory()).addItems(result);
            if (!extra.isEmpty()) {
                Platform.spawnDrops(player.level(), player.blockPosition(), java.util.List.of(extra));
            }
        }

        ci.cancel();
    }
}
