package org.chatterjay.emilink.network.packet.c2s;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.locator.MenuLocator;
import appeng.menu.me.crafting.CraftAmountMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Pulls an item from the AE2 network (or opens CraftAmount if not in stock).
 * Uses the player's currently open AEBaseMenu to get the grid/locator,
 * works for both wired and wireless terminals.
 */
public class PullFromNetworkC2SPacket {
    private final GenericStack stack;

    public PullFromNetworkC2SPacket(GenericStack stack) {
        this.stack = stack;
    }

    public static void encode(PullFromNetworkC2SPacket msg, FriendlyByteBuf buf) {
        GenericStack.writeBuffer(msg.stack, buf);
    }

    public static PullFromNetworkC2SPacket decode(FriendlyByteBuf buf) {
        return new PullFromNetworkC2SPacket(GenericStack.readBuffer(buf));
    }

    public static void handle(PullFromNetworkC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> handleServer(context, msg));
        context.setPacketHandled(true);
    }

    private static void handleServer(NetworkEvent.Context context, PullFromNetworkC2SPacket msg) {
        ServerPlayer player = context.getSender();
        if (player == null || msg.stack == null || msg.stack.what() == null) return;
        if (!(player.containerMenu instanceof AEBaseMenu aeMenu)) {
            ModLogger.warn("PullFromNetwork: player menu is not AEBaseMenu");
            return;
        }

        AEKey what = msg.stack.what();

        // Only handle items for extraction (AEItemKey)
        if (!(what instanceof AEItemKey itemKey)) {
            ModLogger.warn("PullFromNetwork: not an item: {}", what.getClass().getName());
            return;
        }

        try {
            // Get the grid/network from the current menu via reflection (getActionHost is protected)
            Object actionHost;
            try {
                var getActionHost = AEBaseMenu.class.getDeclaredMethod("getActionHost");
                getActionHost.setAccessible(true);
                actionHost = getActionHost.invoke(aeMenu);
            } catch (Exception e) {
                ModLogger.warn("PullFromNetwork: can't access action host: {}", e.getMessage());
                return;
            }
            if (actionHost == null) {
                ModLogger.warn("PullFromNetwork: no action host");
                return;
            }

            // IActionHost.getActionableNode() → IGridNode
            Object gridNode = actionHost.getClass().getMethod("getActionableNode").invoke(actionHost);
            if (gridNode == null) {
                ModLogger.warn("PullFromNetwork: no grid node");
                return;
            }

            // IGridNode.getGrid() → IGrid
            Object grid = invokeMethod(gridNode, "getGrid");
            if (grid == null) {
                ModLogger.warn("PullFromNetwork: no grid");
                return;
            }

            // Try to extract items from network to player inventory
            var inventory = player.getInventory();
            int freeSlot = inventory.getFreeSlot();
            if (freeSlot == -1) {
                ModLogger.warn("PullFromNetwork: no free inventory slot");
                return;
            }

            int maxSize = itemKey.toStack(1).getMaxStackSize();

            // IGrid.getEnergyService() → IEnergyService
            Object energy = invokeMethod(grid, "getEnergyService");
            // IGrid.getStorageService() → IStorageService → IStorageService.getInventory() → MEStorage
            Object storageSvc = invokeMethod(grid, "getStorageService");
            if (storageSvc == null) {
                ModLogger.warn("PullFromNetwork: no storage service");
                return;
            }
            Object storage = invokeMethod(storageSvc, "getInventory");

            if (energy == null || storage == null) {
                ModLogger.warn("PullFromNetwork: energy or storage is null");
                return;
            }

            // StorageHelper.poweredExtraction(IEnergySource, MEStorage, AEKey, long, IActionSource)
            Class<?> storageHelper = Class.forName("appeng.api.storage.StorageHelper");
            Class<?> playerSource = Class.forName("appeng.me.helpers.PlayerSource");
            Object actionSource = playerSource.getConstructor(
                    Class.forName("net.minecraft.world.entity.player.Player")).newInstance(player);

            long extracted = (long) storageHelper.getMethod("poweredExtraction",
                    Class.forName("appeng.api.networking.energy.IEnergySource"),
                    Class.forName("appeng.api.storage.MEStorage"),
                    AEKey.class, long.class,
                    Class.forName("appeng.api.networking.security.IActionSource"))
                    .invoke(null, energy, storage, itemKey, (long) maxSize, actionSource);

            if (extracted > 0) {
                ItemStack extractedStack = itemKey.toStack((int) extracted);
                inventory.setItem(freeSlot, extractedStack);
                player.containerMenu.broadcastChanges();
                ModLogger.info("PullFromNetwork: pulled {}x {}", extracted,
                        itemKey.getDisplayName().getString());
                return;
            }

            // No items in network — check if craftable, open CraftAmountMenu
            Object craftingSvc = invokeMethod(grid, "getCraftingService");
            if (craftingSvc != null && (boolean) craftingSvc.getClass()
                    .getMethod("isCraftable", AEKey.class).invoke(craftingSvc, what)) {
                Field locatorField = AEBaseMenu.class.getDeclaredField("locator");
                locatorField.setAccessible(true);
                MenuLocator locator = (MenuLocator) locatorField.get(aeMenu);
                if (locator != null) {
                    CraftAmountMenu.open(player, locator, what, 1);
                    ModLogger.info("PullFromNetwork: opened craft screen for {}",
                            what.getDisplayName().getString());
                }
            }
        } catch (Exception e) {
            ModLogger.warn("PullFromNetwork: error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static Object invokeMethod(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }
}
