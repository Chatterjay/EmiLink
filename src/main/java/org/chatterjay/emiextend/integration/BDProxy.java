package org.chatterjay.emiextend.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BDProxy {
    private static Boolean loaded;
    private static Class<?> baseMenuClass;
    private static Class<?> netGUIClass;
    private static Class<?> craftMenuClass;
    private static Class<?> netClass;
    private static Class<?> itemKeyClass;
    private static Class<?> keyAmountClass;
    private static Class<?> iStackKeyClass;
    private static Class<?> batchTransferPacketClass;

    private static boolean initReflection() {
        if (loaded != null) return loaded;
        var modList = ModList.get();
        loaded = modList != null && modList.isLoaded("beyonddimensions");
        if (!loaded) return false;

        try {
            netGUIClass = Class.forName("com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI");
            baseMenuClass = Class.forName("com.wintercogs.beyonddimensions.common.menu.BDBaseMenu");
            craftMenuClass = Class.forName("com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu");
            netClass = Class.forName("com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet");
            itemKeyClass = Class.forName("com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey");
            keyAmountClass = Class.forName("com.wintercogs.beyonddimensions.api.storage.key.KeyAmount");
            iStackKeyClass = Class.forName("com.wintercogs.beyonddimensions.api.storage.key.IStackKey");
            batchTransferPacketClass = Class.forName("com.wintercogs.beyonddimensions.network.packet.c2s.BatchTransferPacket");
            return true;
        } catch (Exception e) {
            ModLogger.warn("BDProxy reflection init failed: {}", e.getMessage());
            loaded = false;
            return false;
        }
    }

    public static boolean isLoaded() {
        return initReflection();
    }

    // ---- Screen / Menu type checks ----

    public static boolean isBDNetGUI(Screen screen) {
        return isLoaded() && netGUIClass.isInstance(screen);
    }

    public static boolean isBDCraftGUI(Screen screen) {
        return isLoaded() && craftMenuClass.isInstance(screen);
    }

    public static boolean isBDBaseMenu(AbstractContainerMenu menu) {
        return isLoaded() && baseMenuClass.isInstance(menu);
    }

    public static boolean isBDCraftMenu(AbstractContainerMenu menu) {
        return isLoaded() && craftMenuClass.isInstance(menu);
    }

    // ---- Field accessors ----

    public static int getInventoryStartIndex(AbstractContainerMenu menu) {
        try {
            return baseMenuClass.getField("inventoryStartIndex").getInt(menu);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int getInventoryEndIndex(AbstractContainerMenu menu) {
        try {
            return baseMenuClass.getField("inventoryEndIndex").getInt(menu);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int getResultSlotIndex(AbstractContainerMenu menu) {
        try {
            return craftMenuClass.getField("resultSlotIndex").getInt(menu);
        } catch (Exception e) {
            return -1;
        }
    }

    // ---- F Search ----

    public static boolean setSearchText(Screen screen, String text) {
        if (!isLoaded() || text == null || text.isEmpty()) return false;
        try {
            Field field = netGUIClass.getDeclaredField("searchField");
            field.setAccessible(true);
            EditBox searchField = (EditBox) field.get(netGUIClass.cast(screen));
            if (searchField != null) {
                searchField.setValue(text);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    // ---- EMI shift+click: extract from network ----

    public static void pullFromNetwork(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return;
        PacketDistributor.sendToServer(new BDActionPacket(stack, 0));
    }

    public static boolean extractFromNetwork(Player player, ItemStack targetStack) {
        return extractFromNetwork(player, targetStack, false, new int[0]);
    }

    public static boolean extractAllFromNetwork(Player player, ItemStack targetStack) {
        return extractAllFromNetwork(player, targetStack, new int[0]);
    }

    public static boolean extractAllFromNetwork(Player player, ItemStack targetStack, int[] lockedSlots) {
        return extractFromNetwork(player, targetStack, true, lockedSlots);
    }

    private static boolean extractFromNetwork(Player player, ItemStack targetStack, boolean extractAll, int... lockedSlots) {
        if (!isLoaded()) return false;
        try {
            var getNet = netClass.getMethod("getPrimaryNetFromPlayer", Player.class);
            Object net = getNet.invoke(null, player);
            if (net == null) return false;

            var getStorage = netClass.getMethod("getUnifiedStorage");
            Object storage = getStorage.invoke(net);

            var keyCtor = itemKeyClass.getConstructor(ItemStack.class);
            Object targetKey = keyCtor.newInstance(targetStack);

            var getMaxStack = itemKeyClass.getMethod("getVanillaMaxStackSize");
            long batchSize = (long) getMaxStack.invoke(targetKey);

            var extractMethod = storage.getClass().getMethod("extract", iStackKeyClass, long.class, boolean.class, boolean.class);
            var insertMethod = storage.getClass().getMethod("insert", iStackKeyClass, long.class, boolean.class);
            var inventory = player.getInventory();

            do {
                Object extracted = extractMethod.invoke(storage, targetKey, batchSize, false, true);
                long amount = (long) keyAmountClass.getMethod("amount").invoke(extracted);
                if (amount <= 0) break;

                Object stackObj = keyAmountClass.getMethod("toStack").invoke(extracted);
                if (!(stackObj instanceof ItemStack extractedStack)) break;

                int remaining = extractedStack.getCount();

                // Fill existing stacks first (skip locked slots)
                for (int i = 0; i < inventory.items.size() && remaining > 0; i++) {
                    if (isLocked(i, lockedSlots)) continue;
                    ItemStack slotStack = inventory.getItem(i);
                    if (slotStack.isEmpty()) continue;
                    if (!ItemStack.isSameItemSameComponents(slotStack, extractedStack)) continue;
                    int space = slotStack.getMaxStackSize() - slotStack.getCount();
                    if (space <= 0) continue;
                    int toAdd = Math.min(remaining, space);
                    slotStack.grow(toAdd);
                    remaining -= toAdd;
                }

                // Fill empty slots (skip locked slots)
                for (int i = 0; i < inventory.items.size() && remaining > 0; i++) {
                    if (isLocked(i, lockedSlots)) continue;
                    if (!inventory.getItem(i).isEmpty()) continue;
                    int maxStack = extractedStack.getMaxStackSize();
                    int toAdd = Math.min(remaining, maxStack);
                    ItemStack newStack = extractedStack.copy();
                    newStack.setCount(toAdd);
                    inventory.setItem(i, newStack);
                    remaining -= toAdd;
                }

                // Return overflow to network
                if (remaining > 0) {
                    insertMethod.invoke(storage, targetKey, (long) remaining, false);
                    break;
                }
            } while (extractAll);

            inventory.setChanged();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Space+Click: deposit to network ----

    public static boolean depositToNetwork(Player player, int mode, int... lockedSlots) {
        if (!isLoaded()) {
            return false;
        }
        try {
            var getNet = netClass.getMethod("getPrimaryNetFromPlayer", Player.class);
            Object net = getNet.invoke(null, player);
            if (net == null) {
                return false;
            }

            var getStorage = netClass.getMethod("getUnifiedStorage");
            Object storage = getStorage.invoke(net);

            var insertMethod = storage.getClass().getMethod("insert", iStackKeyClass, long.class, boolean.class);

            var inventory = player.getInventory();
            int startSlot = (mode == 2) ? 0 : 9;
            int endSlot = (mode == 2) ? 9 : inventory.items.size();

            var keyCtor = itemKeyClass.getConstructor(ItemStack.class);
            int deposited = 0;

            for (int i = startSlot; i < endSlot; i++) {
                if (isLocked(i, lockedSlots)) {
                    continue;
                }

                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) continue;

                Object key = keyCtor.newInstance(stack);
                Object remaining = insertMethod.invoke(storage, key, (long) stack.getCount(), false);
                long remainingAmount = (long) keyAmountClass.getMethod("amount").invoke(remaining);
                if (remainingAmount < stack.getCount()) {
                    deposited++;
                }
                stack.setCount((int) remainingAmount);
            }

            inventory.setChanged();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Space+Click fallback: BD's BatchTransferPacket ----

    // ---- Locked slot check ----

    private static boolean isLocked(int slotIndex, int... lockedSlots) {
        if (lockedSlots == null || lockedSlots.length == 0) return false;
        for (int ls : lockedSlots) {
            if (ls == slotIndex) return true;
        }
        return false;
    }

    // ---- Space+Click fallback: BD's BatchTransferPacket ----

    public static void sendBatchTransfer(ItemStack stack, boolean dirToStorage) {
        if (!isLoaded() || stack == null) return;
        try {
            var keyAmountCtor = keyAmountClass.getConstructor(iStackKeyClass, long.class);
            var keyCtor = itemKeyClass.getConstructor(ItemStack.class);
            Object key = keyCtor.newInstance(stack);
            Object keyAmount = keyAmountCtor.newInstance(key, (long) stack.getCount());

            Constructor<?> packetCtor = batchTransferPacketClass.getConstructor(keyAmountClass, boolean.class);
            Object packet = packetCtor.newInstance(keyAmount, dirToStorage);

            var sendMethod = PacketDistributor.class.getMethod("sendToServer", net.minecraft.network.protocol.common.custom.CustomPacketPayload.class);
            sendMethod.invoke(null, packet);
        } catch (Exception e) {
        }
    }

    // ---- Space+Click on result slot: mass craft ----

    public static boolean massCraft(Player player) {
        if (!isLoaded()) return false;
        try {
            var menu = player.containerMenu;
            if (!craftMenuClass.isInstance(menu)) return false;

            int resultSlotIndex = (int) craftMenuClass.getField("resultSlotIndex").get(menu);
            if (resultSlotIndex < 0 || resultSlotIndex >= menu.slots.size()) return false;

            var inventory = player.getInventory();
            int maxCrafts = 512;

            for (int c = 0; c < maxCrafts; c++) {
                var resultSlot = menu.slots.get(resultSlotIndex);
                if (!resultSlot.hasItem()) break;

                // Simulate taking the result item via PICKUP click
                // This triggers BD's native slot.onTake → consume grid → refill from network → recalculate
                menu.clicked(resultSlotIndex, 0, ClickType.PICKUP, player);

                // The crafted item is now on the cursor (carried)
                ItemStack carried = menu.getCarried();
                if (carried.isEmpty()) break;

                // Move carried item to inventory
                ItemStack remaining = carried.copy();
                for (int i = 0; i < inventory.items.size() && !remaining.isEmpty(); i++) {
                    ItemStack slotStack = inventory.getItem(i);
                    if (slotStack.isEmpty()) {
                        int toAdd = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                        inventory.setItem(i, remaining.split(toAdd));
                    } else if (ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                        int space = slotStack.getMaxStackSize() - slotStack.getCount();
                        if (space > 0) {
                            int toAdd = Math.min(remaining.getCount(), space);
                            slotStack.grow(toAdd);
                            remaining.shrink(toAdd);
                        }
                    }
                }
                menu.setCarried(ItemStack.EMPTY);

                if (!remaining.isEmpty()) {
                    // Inventory full — place back in result slot
                    resultSlot.set(remaining);
                    break;
                }
            }

            inventory.setChanged();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
