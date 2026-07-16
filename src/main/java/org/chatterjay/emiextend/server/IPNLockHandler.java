package org.chatterjay.emiextend.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.util.ServerIPNState;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles IPN (Inventory Profiles Next) locked slot save/restore logic.
 * When MOVE_REGION action fires, locked slots are temporarily cleared to prevent
 * IPN from moving items into them, then restored after the action completes.
 */
public final class IPNLockHandler {
    private final Map<Integer, ItemStack> savedLockedItems = new HashMap<>();
    private boolean active = false;

    public boolean isActive() {
        return active;
    }

    /**
     * Call before MOVE_REGION: save items in locked slots and clear them.
     */
    public void beforeMoveRegion(ServerPlayer player) {
        savedLockedItems.clear();
        active = true;
        var locked = ServerIPNState.getLockedSlots(player.getUUID());
        if (locked.isEmpty()) return;

        var inv = player.getInventory();
        for (int idx : locked) {
            if (idx >= 0 && idx < 36) {
                ItemStack stack = inv.getItem(idx);
                if (!stack.isEmpty()) {
                    if (isWirelessTerminal(stack)) continue;
                    savedLockedItems.put(idx, stack.copy());
                    inv.setItem(idx, ItemStack.EMPTY);
                }
            }
        }
    }

    /**
     * Call after MOVE_REGION: restore saved locked slot items and reroute any
     * items IPN placed in locked slots.
     */
    public void afterMoveRegion(ServerPlayer player) {
        if (savedLockedItems.isEmpty()) {
            active = false;
            return;
        }
        var inv = player.getInventory();
        Map<Integer, ItemStack> displaced = new HashMap<>();
        for (var entry : savedLockedItems.entrySet()) {
            int idx = entry.getKey();
            ItemStack saved = entry.getValue();
            ItemStack current = inv.getItem(idx);
            inv.setItem(idx, saved);
            if (!current.isEmpty()) {
                displaced.put(idx, current);
            }
        }
        for (var entry : displaced.entrySet()) {
            if (!player.getInventory().add(entry.getValue())) {
                player.drop(entry.getValue(), false);
            }
        }
        savedLockedItems.clear();
        active = false;
    }

    private static boolean isWirelessTerminal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            Class<?> clazz = Class.forName("appeng.items.tools.powered.WirelessTerminalItem");
            return clazz.isInstance(stack.getItem());
        } catch (Exception e) {
            return false;
        }
    }
}
