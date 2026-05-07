package org.chatterjay.emiextend.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import org.chatterjay.emiextend.util.EmiCraftHelper;
import org.chatterjay.emiextend.util.ModLogger;
import org.chatterjay.emiextend.util.ServerIPNState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(AEBaseMenu.class)
public class AEBaseMenuMixin {

    @Unique
    private static final long SINGLE_CRAFT_SIGNAL = Long.MIN_VALUE;

    @Unique
    private final Map<Integer, ItemStack> emilink$savedLockedItems = new HashMap<>();

    @Inject(method = "doAction", at = @At(value = "INVOKE", target = "Lappeng/menu/slot/CraftingTermSlot;doClick(Lappeng/helpers/InventoryAction;Lnet/minecraft/world/entity/player/Player;)V"), remap = false)
    private void emilink$signalSingleCraft(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        if (id == SINGLE_CRAFT_SIGNAL && action == InventoryAction.CRAFT_SHIFT) {
            EmiCraftHelper.markSingleCraft();
        }
    }

    @Inject(method = "doAction", at = @At("HEAD"), remap = false)
    private void emilink$beforeDoAction(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        emilink$savedLockedItems.clear();
        if (action != InventoryAction.MOVE_REGION) return;

        var locked = ServerIPNState.getLockedSlots(player.getUUID());
        if (locked.isEmpty()) return;

        var inv = player.getInventory();
        for (int idx : locked) {
            if (idx >= 0 && idx < 36) {
                ItemStack stack = inv.getItem(idx);
                if (!stack.isEmpty()) {
                    emilink$savedLockedItems.put(idx, stack.copy());
                    inv.setItem(idx, ItemStack.EMPTY);
                }
            }
        }
        if (!emilink$savedLockedItems.isEmpty()) {
            ModLogger.debug("AE MOVE_REGION: cleared {} locked slot(s) for player {}",
                    emilink$savedLockedItems.size(), player.getName().getString());
        }
    }

    @Inject(method = "doAction", at = @At("RETURN"), remap = false)
    private void emilink$afterDoAction(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        // Restore items that were cleared for IPN-locked slots
        if (!emilink$savedLockedItems.isEmpty()) {
            var inv = player.getInventory();
            emilink$savedLockedItems.forEach((idx, savedStack) -> {
                if (idx >= 0 && idx < 36 && inv.getItem(idx).isEmpty()) {
                    inv.setItem(idx, savedStack);
                }
            });
            ModLogger.debug("AE MOVE_REGION: restored {} locked slot(s) for player {}",
                    emilink$savedLockedItems.size(), player.getName().getString());
            emilink$savedLockedItems.clear();
        }

        EmiCraftHelper.clear();
    }
}
