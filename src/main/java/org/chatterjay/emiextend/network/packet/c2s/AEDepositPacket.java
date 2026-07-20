package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.network.PacketHelper;

import java.util.function.Supplier;

/**
 * Client→Server: Deposit an item into the AE2 network.
 * slotIndex = -1 for cursor item, >= 0 for inventory slot (batch deposit).
 * After insert, the server clears the source slot to prevent duplication.
 */
public record AEDepositPacket(ItemStack stack, int slotIndex) {

    public static void encode(AEDepositPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
        buf.writeVarInt(msg.slotIndex);
    }

    public static AEDepositPacket decode(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int slotIndex = buf.readVarInt();
        return new AEDepositPacket(stack, slotIndex);
    }

    void handleInServer(Player player) {
        if (player == null || stack == null || stack.isEmpty()) return;

        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
            Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
            Object modulate = actionableClass.getField("MODULATE").get(null);
            Object actionSource = actionSourceClass.getMethod("ofPlayer", Player.class).invoke(null, player);

            Object inventory = resolveInventoryFromMenu(player);
            if (inventory == null) {
                inventory = resolveInventoryFromWirelessTerminal(player, aeItemKeyClass);
            }
            if (inventory == null) {
                org.chatterjay.emiextend.util.ModLogger.warn("AEDeposit: no grid available");
                return;
            }

            Object aeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, stack);
            if (aeKey == null) {
                org.chatterjay.emiextend.util.ModLogger.warn("AEDeposit: failed to create AEKey for {}", stack);
                return;
            }

            var insertMethod = inventory.getClass().getMethod("insert", aeKeyClass, long.class, actionableClass, actionSourceClass);
            long inserted = (long) insertMethod.invoke(inventory, aeKey, (long) stack.getCount(), modulate, actionSource);
            if (inserted <= 0) return;

            if (slotIndex == -1) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                player.containerMenu.broadcastChanges();
            } else if (slotIndex >= 0 && slotIndex < player.getInventory().items.size()) {
                player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
                player.containerMenu.broadcastChanges();
            }

        } catch (Exception e) {
            org.chatterjay.emiextend.util.ModLogger.warn("AEDeposit: error: {}", e.getMessage());
        }
    }

    static Object resolveInventoryFromMenu(Player player) {
        try {
            var menu = player.containerMenu;
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(menu)) return null;

            Object grid = org.chatterjay.emiextend.network.AE2GridQueryUtil.resolveGrid(aeBaseMenuClass, menu);
            if (grid == null) return null;

            Object storageSvc = org.chatterjay.emiextend.network.AE2GridQueryUtil.callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            if (storageSvc == null) return null;

            return org.chatterjay.emiextend.network.AE2GridQueryUtil.callMethodOnBestMatch(storageSvc, "getInventory");
        } catch (Exception e) {
            return null;
        }
    }

    static Object resolveInventoryFromWirelessTerminal(Player player, Class<?> aeItemKeyClass) {
        try {
            ItemStack terminal = findWirelessTerminal(player);
            if (terminal == null || terminal.isEmpty()) return null;

            java.util.function.Consumer<?> noop = msg -> {};
            Object grid = terminal.getItem().getClass()
                    .getMethod("getLinkedGrid", ItemStack.class, net.minecraft.world.level.Level.class, java.util.function.Consumer.class)
                    .invoke(terminal.getItem(), terminal, player.level(), noop);
            if (grid == null) return null;

            Object storageSvc = org.chatterjay.emiextend.network.AE2GridQueryUtil.callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            if (storageSvc == null) return null;

            return org.chatterjay.emiextend.network.AE2GridQueryUtil.callMethodOnBestMatch(storageSvc, "getInventory");
        } catch (Exception e) {
            org.chatterjay.emiextend.util.ModLogger.warn("AEDeposit: wireless terminal error: {}", e.getMessage());
            return null;
        }
    }

    private static ItemStack findWirelessTerminal(Player player) {
        try {
            Class<?> wtClass = Class.forName("appeng.items.tools.powered.WirelessTerminalItem");
            var inv = player.getInventory();
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.getItem(i);
                if (wtClass.isInstance(s.getItem())) return s;
            }
            if (wtClass.isInstance(player.getOffhandItem().getItem())) {
                return player.getOffhandItem();
            }
            ItemStack curiosStack = findInCurios(player, wtClass);
            if (!curiosStack.isEmpty()) {
                return curiosStack;
            }
        } catch (Exception e) {
            org.chatterjay.emiextend.util.ModLogger.warn("AEDeposit: findWirelessTerminal error: {}", e.getMessage());
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findInCurios(Player player, Class<?> targetClass) {
        try {
            net.minecraftforge.fml.ModList modList = net.minecraftforge.fml.ModList.get();
            if (modList == null || !modList.isLoaded("curios")) return ItemStack.EMPTY;
            var curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCuriosInventory = curiosApi.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            java.util.Optional<?> opt = (java.util.Optional<?>) getCuriosInventory.invoke(null, player);
            if (opt.isEmpty()) return ItemStack.EMPTY;
            Object handler = opt.get();
            java.util.function.Predicate<ItemStack> predicate = s -> !s.isEmpty() && targetClass.isInstance(s.getItem());
            java.util.Optional<?> result = (java.util.Optional<?>) handler.getClass()
                    .getMethod("findFirstCurio", java.util.function.Predicate.class)
                    .invoke(handler, predicate);
            if (result.isPresent()) {
                Object slotResult = result.get();
                return (ItemStack) slotResult.getClass().getMethod("stack").invoke(slotResult);
            }
        } catch (Exception e) {
            org.chatterjay.emiextend.util.ModLogger.warn("AEDeposit: findInCurios error: {}", e.getMessage());
        }
        return ItemStack.EMPTY;
    }

    public static void handle(AEDepositPacket msg, Supplier<NetworkEvent.Context> ctx) {
        PacketHelper.handleServerBound(ctx, () -> msg.handleInServer(ctx.get().getSender()));
    }
}
