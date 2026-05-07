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

    /** Check if an ItemStack is an AE2 wireless terminal (server-safe reflection). */
    @Unique
    private static boolean emilink$isWirelessTerminal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            Class<?> clazz = Class.forName("appeng.items.tools.powered.WirelessTerminalItem");
            return clazz.isInstance(stack.getItem());
        } catch (Exception e) {
            return false;
        }
    }

    @Inject(method = "doAction", at = @At(value = "INVOKE", target = "Lappeng/menu/slot/CraftingTermSlot;doClick(Lappeng/helpers/InventoryAction;Lnet/minecraft/world/entity/player/Player;)V"), remap = false)
    private void emilink$signalSingleCraft(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        if (id == SINGLE_CRAFT_SIGNAL && action == InventoryAction.CRAFT_SHIFT) {
            EmiCraftHelper.markSingleCraft();
        }
    }

    @Inject(method = "doAction", at = @At("HEAD"), remap = false)
    private void emilink$beforeDoAction(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        emilink$savedLockedItems.clear();
        ModLogger.debug("AE doAction: action={}, slot={}, id={}, player={}", action, slot, id, player.getName().getString());
        if (action != InventoryAction.MOVE_REGION) return;

        var locked = ServerIPNState.getLockedSlots(player.getUUID());
        ModLogger.debug("AE MOVE_REGION: locked slots from ServerIPNState: {}", locked);
        if (locked.isEmpty()) return;

        var inv = player.getInventory();
        int cleared = 0;
        int skipped = 0;
        for (int idx : locked) {
            if (idx >= 0 && idx < 36) {
                ItemStack stack = inv.getItem(idx);
                if (!stack.isEmpty()) {
                    // Don't clear the slot if it holds the wireless terminal (would cut AE network)
                    if (emilink$isWirelessTerminal(stack)) {
                        skipped++;
                        continue;
                    }
                    emilink$savedLockedItems.put(idx, stack.copy());
                    inv.setItem(idx, ItemStack.EMPTY);
                    cleared++;
                }
            }
        }
        ModLogger.debug("AE MOVE_REGION: cleared {} locked item(s) for player {} (skipped {} wireless terminal)",
                cleared, player.getName().getString(), skipped);
    }

    @Inject(method = "doAction", at = @At("RETURN"), remap = false)
    private void emilink$afterDoAction(ServerPlayer player, InventoryAction action, int slot, long id, CallbackInfo ci) {
        // Restore items that were cleared for IPN-locked slots
        if (!emilink$savedLockedItems.isEmpty()) {
            var inv = player.getInventory();
            // Phase 1: restore all locked slot items first
            Map<Integer, ItemStack> displaced = new HashMap<>();
            for (var entry : emilink$savedLockedItems.entrySet()) {
                int idx = entry.getKey();
                ItemStack saved = entry.getValue();
                ItemStack current = inv.getItem(idx);
                inv.setItem(idx, saved);
                if (!current.isEmpty()) {
                    displaced.put(idx, current);
                }
            }
            // Phase 2: reroute items that MOVE_REGION placed in locked slots
            // (all locked slots are now occupied, so add() won't target them)
            for (var entry : displaced.entrySet()) {
                if (!player.getInventory().add(entry.getValue())) {
                    player.drop(entry.getValue(), false);
                }
            }
            ModLogger.debug("AE MOVE_REGION: restored {} locked slot(s) for player {}, rerouted {} item(s)",
                    emilink$savedLockedItems.size(), player.getName().getString(), displaced.size());
            emilink$savedLockedItems.clear();
        }

        EmiCraftHelper.clear();
    }
}
