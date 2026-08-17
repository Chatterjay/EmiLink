package org.chatterjay.emiextend.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.client.ClientPacketHelper;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BDProxy {
    private static Boolean loaded;
    private static Class<?> baseMenuClass;
    private static Class<?> netGUIClass;
    private static Class<?> craftGUIClass;
    private static Class<?> craftMenuClass;
    private static Class<?> netClass;
    private static Class<?> itemKeyClass;
    private static Class<?> keyAmountClass;
    private static Class<?> iStackKeyClass;
    private static Class<?> batchTransferPacketClass;
    private static final Map<UUID, CraftReturnSnapshot> craftReturnSnapshots = new ConcurrentHashMap<>();

    private record CraftReturnSnapshot(int containerId, boolean firstCraftReturnDir) {
    }

    private static boolean initReflection() {
        if (loaded != null) return loaded;
        var modList = ModList.get();
        loaded = modList != null && modList.isLoaded("beyonddimensions");
        if (!loaded) return false;

        try {
            netGUIClass = Class.forName("com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI");
            baseMenuClass = Class.forName("com.wintercogs.beyonddimensions.common.menu.BDBaseMenu");
            craftGUIClass = Class.forName("com.wintercogs.beyonddimensions.client.gui.DimensionsCraftGUI");
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
        return isLoaded() && craftGUIClass.isInstance(screen);
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
            EditBox searchField = getNetSearchField(screen);
            if (searchField != null) {
                searchField.setValue(text);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public static String getSearchText(Screen screen) {
        if (!isLoaded() || !isBDNetGUI(screen)) return null;
        try {
            EditBox searchField = getNetSearchField(screen);
            return searchField == null ? null : searchField.getValue();
        } catch (Exception ignored) {
        }
        return null;
    }

    public static boolean isSearchFocused(Screen screen) {
        if (!isLoaded() || !isBDNetGUI(screen)) return false;
        try {
            EditBox searchField = getNetSearchField(screen);
            return searchField != null
                    && (screen.getFocused() == searchField || searchField.isFocused() || searchField.canConsumeInput());
        } catch (Exception ignored) {
        }
        return false;
    }

    private static EditBox getNetSearchField(Screen screen) throws IllegalAccessException, NoSuchFieldException {
        Field field = netGUIClass.getDeclaredField("searchField");
        field.setAccessible(true);
        return (EditBox) field.get(netGUIClass.cast(screen));
    }

    // ---- EMI shift+click: extract from network ----

    public static void pullFromNetwork(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return;
        ClientPacketHelper.sendToServer(new BDActionPacket(stack, 0));
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

    public static long getStoredAmount(Player player, ItemStack targetStack) {
        if (!isLoaded() || player == null || targetStack == null || targetStack.isEmpty()) {
            return 0;
        }
        long menuAmount = getStoredAmountFromMenu(player.containerMenu, targetStack);
        if (menuAmount > 0) {
            return menuAmount;
        }
        try {
            var getNet = netClass.getMethod("getPrimaryNetFromPlayer", Player.class);
            Object net = getNet.invoke(null, player);
            if (net == null) return 0;

            var getStorage = netClass.getMethod("getUnifiedStorage");
            Object storage = getStorage.invoke(net);
            if (storage == null) return 0;

            var keyCtor = itemKeyClass.getConstructor(ItemStack.class);
            Object key = keyCtor.newInstance(targetStack);

            for (String methodName : new String[]{"getStackByKey", "getAmount", "getStoredAmount", "getCount", "amountOf"}) {
                try {
                    var method = storage.getClass().getMethod(methodName, iStackKeyClass);
                    Object value = method.invoke(storage, key);
                    if (value instanceof Number number) {
                        return Math.max(0, number.longValue());
                    }
                    if (value != null && keyAmountClass.isInstance(value)) {
                        return Math.max(0, (long) keyAmountClass.getMethod("amount").invoke(value));
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Exception e) {
            ModLogger.warn("BDProxy getStoredAmount failed: {}", e.getMessage());
        }
        return 0;
    }

    public static boolean beginQuickCraftReturnToInventory(Player player) {
        if (!isLoaded() || player == null) {
            return false;
        }
        try {
            var menu = player.containerMenu;
            if (!craftMenuClass.isInstance(menu)) {
                return false;
            }

            Field field = craftMenuClass.getField("firstCraftReturnDir");
            UUID playerId = player.getUUID();
            CraftReturnSnapshot existing = craftReturnSnapshots.get(playerId);
            if (existing == null || existing.containerId() != menu.containerId) {
                craftReturnSnapshots.put(playerId, new CraftReturnSnapshot(menu.containerId, field.getBoolean(menu)));
            }
            field.setBoolean(menu, true);
            return true;
        } catch (Exception e) {
            ModLogger.warn("BDProxy beginQuickCraftReturnToInventory failed: {}", e.getMessage());
            return false;
        }
    }

    public static boolean restoreQuickCraftReturnDirection(Player player) {
        if (!isLoaded() || player == null) {
            return false;
        }
        try {
            CraftReturnSnapshot snapshot = craftReturnSnapshots.remove(player.getUUID());
            if (snapshot == null) {
                return false;
            }

            var menu = player.containerMenu;
            if (!craftMenuClass.isInstance(menu) || menu.containerId != snapshot.containerId()) {
                return false;
            }

            Field field = craftMenuClass.getField("firstCraftReturnDir");
            field.setBoolean(menu, snapshot.firstCraftReturnDir());
            return true;
        } catch (Exception e) {
            ModLogger.warn("BDProxy restoreQuickCraftReturnDirection failed: {}", e.getMessage());
            return false;
        }
    }

    public static boolean isQuickCraftReturningToInventory(Player player, int containerId) {
        if (player == null) {
            return false;
        }
        CraftReturnSnapshot snapshot = craftReturnSnapshots.get(player.getUUID());
        return snapshot != null && snapshot.containerId() == containerId;
    }

    public static boolean isQuickCraftReturningToInventory(Object menu) {
        if (!(menu instanceof AbstractContainerMenu containerMenu)) {
            return false;
        }
        try {
            Field playerField = findField(menu.getClass(), "player");
            if (playerField == null) {
                return false;
            }
            playerField.setAccessible(true);
            Object value = playerField.get(menu);
            if (!(value instanceof Player player)) {
                return false;
            }
            return isQuickCraftReturningToInventory(player, containerMenu.containerId);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean transferRecipeFromNetworkOnly(Object menu, List<?> inputKeys, List<?> amounts) {
        if (!isLoaded() || menu == null || !craftMenuClass.isInstance(menu)) {
            return false;
        }
        try {
            Object storage = getStorageFromMenu(menu);
            if (storage == null) {
                ModLogger.warn("BD_QUICKCRAFT transferRecipe network-only skipped: no storage");
                return true;
            }

            Field craftSlotsField = findField(menu.getClass(), "craftSlots");
            if (craftSlotsField == null) {
                ModLogger.warn("BD_QUICKCRAFT transferRecipe network-only skipped: no craftSlots field");
                return true;
            }
            craftSlotsField.setAccessible(true);
            Object craftSlotsObj = craftSlotsField.get(menu);
            if (!(craftSlotsObj instanceof Container craftSlots)) {
                ModLogger.warn("BD_QUICKCRAFT transferRecipe network-only skipped: craftSlots is {}",
                        craftSlotsObj == null ? "null" : craftSlotsObj.getClass().getName());
                return true;
            }

            Method cleanCraftSlots = craftMenuClass.getMethod("cleanCraftSlots", boolean.class);
            cleanCraftSlots.invoke(menu, true);

            Method extractMethod = storage.getClass().getMethod("extract", iStackKeyClass, long.class, boolean.class, boolean.class);
            Method amountMethod = keyAmountClass.getMethod("amount");
            Method toStackMethod = keyAmountClass.getMethod("toStack");

            int limit = Math.min(craftSlots.getContainerSize(), inputKeys == null ? 0 : inputKeys.size());
            int filled = 0;
            long extractedTotal = 0;
            for (int i = 0; i < limit; i++) {
                Object key = inputKeys.get(i);
                long need = amountAt(amounts, i);
                if (key == null || !itemKeyClass.isInstance(key) || need <= 0) {
                    continue;
                }

                Object extracted = extractMethod.invoke(storage, key, need, false, false);
                long got = Math.max(0L, (long) amountMethod.invoke(extracted));
                if (got <= 0) {
                    continue;
                }

                Object stackObj = toStackMethod.invoke(extracted);
                if (stackObj instanceof ItemStack stack && !stack.isEmpty()) {
                    craftSlots.setItem(i, stack);
                    filled++;
                    extractedTotal += got;
                }
            }

            craftSlots.setChanged();
            if (menu instanceof AbstractContainerMenu abstractMenu) {
                abstractMenu.slotsChanged(craftSlots);
                abstractMenu.broadcastChanges();
            }
            ModLogger.debug("BD_QUICKCRAFT transferRecipe network-only filledSlots={} extracted={}", filled, extractedTotal);
            return true;
        } catch (Exception e) {
            ModLogger.warn("BD_QUICKCRAFT transferRecipe network-only failed: {}", e.getMessage());
            return true;
        }
    }

    private static long amountAt(List<?> amounts, int index) {
        if (amounts == null || index < 0 || index >= amounts.size()) {
            return 0;
        }
        Object value = amounts.get(index);
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        return 0;
    }

    public static boolean cleanCraftSlotsToNetwork(Player player) {
        if (!isLoaded() || player == null) {
            return false;
        }
        try {
            var menu = player.containerMenu;
            if (!craftMenuClass.isInstance(menu)) {
                return false;
            }
            Method cleanCraftSlots = craftMenuClass.getMethod("cleanCraftSlots", boolean.class);
            cleanCraftSlots.invoke(menu, true);
            player.getInventory().setChanged();
            menu.broadcastChanges();
            return true;
        } catch (Exception e) {
            ModLogger.warn("BDProxy cleanCraftSlotsToNetwork failed: {}", e.getMessage());
            return false;
        }
    }

    public static boolean depositMatchingToNetwork(Player player, ItemStack targetStack) {
        if (!isLoaded() || player == null || targetStack == null || targetStack.isEmpty()) {
            return false;
        }
        try {
            Object storage = getStorageForPlayer(player);
            if (storage == null) {
                return false;
            }

            var keyCtor = itemKeyClass.getConstructor(ItemStack.class);
            var insertMethod = storage.getClass().getMethod("insert", iStackKeyClass, long.class, boolean.class);
            var amountMethod = keyAmountClass.getMethod("amount");
            var inventory = player.getInventory();
            boolean changed = false;

            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, targetStack)) {
                    continue;
                }
                Object key = keyCtor.newInstance(stack);
                Object remaining = insertMethod.invoke(storage, key, (long) stack.getCount(), false);
                long remainingAmount = Math.max(0L, (long) amountMethod.invoke(remaining));
                if (remainingAmount != stack.getCount()) {
                    stack.setCount((int) Math.min(Integer.MAX_VALUE, remainingAmount));
                    if (stack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                    changed = true;
                }
            }

            if (player.containerMenu != null) {
                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, targetStack)) {
                    Object key = keyCtor.newInstance(carried);
                    Object remaining = insertMethod.invoke(storage, key, (long) carried.getCount(), false);
                    long remainingAmount = Math.max(0L, (long) amountMethod.invoke(remaining));
                    if (remainingAmount != carried.getCount()) {
                        carried.setCount((int) Math.min(Integer.MAX_VALUE, remainingAmount));
                        if (carried.isEmpty()) {
                            player.containerMenu.setCarried(ItemStack.EMPTY);
                        }
                        changed = true;
                    }
                }
            }

            if (changed) {
                inventory.setChanged();
                if (player.containerMenu != null) {
                    player.containerMenu.broadcastChanges();
                }
            }
            return changed;
        } catch (Exception e) {
            ModLogger.warn("BDProxy depositMatchingToNetwork failed: {}", e.getMessage());
            return false;
        }
    }

    public static boolean depositSlotToNetwork(Player player, int slotIndex) {
        if (!isLoaded() || player == null || slotIndex < 0 || slotIndex >= player.getInventory().items.size()) {
            return false;
        }
        try {
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (stack.isEmpty()) {
                return false;
            }

            Object storage = getStorageForPlayer(player);
            if (storage == null) {
                return false;
            }

            var keyCtor = itemKeyClass.getConstructor(ItemStack.class);
            var insertMethod = storage.getClass().getMethod("insert", iStackKeyClass, long.class, boolean.class);
            var amountMethod = keyAmountClass.getMethod("amount");
            Object key = keyCtor.newInstance(stack);
            Object remaining = insertMethod.invoke(storage, key, (long) stack.getCount(), false);
            long remainingAmount = Math.max(0L, (long) amountMethod.invoke(remaining));
            if (remainingAmount == stack.getCount()) {
                return false;
            }

            stack.setCount((int) Math.min(Integer.MAX_VALUE, remainingAmount));
            if (stack.isEmpty()) {
                player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
            }
            player.getInventory().setChanged();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
            return true;
        } catch (Exception e) {
            ModLogger.warn("BDProxy depositSlotToNetwork failed: {}", e.getMessage());
            return false;
        }
    }

    private static Object getStorageForPlayer(Player player) throws ReflectiveOperationException {
        var menu = player.containerMenu;
        if (menu != null && baseMenuClass.isInstance(menu)) {
            Object storage = getStorageFromMenu(menu);
            if (storage != null) {
                return storage;
            }
        }

        var getNet = netClass.getMethod("getPrimaryNetFromPlayer", Player.class);
        Object net = getNet.invoke(null, player);
        if (net == null) {
            return null;
        }
        var getStorage = netClass.getMethod("getUnifiedStorage");
        return getStorage.invoke(net);
    }

    private static Object getStorageFromMenu(Object menu) throws IllegalAccessException {
        if (menu == null) {
            return null;
        }
        Field storageField = findField(menu.getClass(), "storage");
        if (storageField == null) {
            return null;
        }
        storageField.setAccessible(true);
        return storageField.get(menu);
    }

    private static long getStoredAmountFromMenu(AbstractContainerMenu menu, ItemStack targetStack) {
        if (menu == null || targetStack == null || targetStack.isEmpty()) {
            return 0;
        }
        try {
            if (!baseMenuClass.isInstance(menu)) {
                return 0;
            }

            Field storageField = findField(menu.getClass(), "storage");
            if (storageField == null) {
                return 0;
            }
            storageField.setAccessible(true);
            Object storage = storageField.get(menu);
            if (storage == null) {
                return 0;
            }

            var keyCtor = itemKeyClass.getConstructor(ItemStack.class);
            Object key = keyCtor.newInstance(targetStack);

            try {
                Method getStackByKey = storage.getClass().getMethod("getStackByKey", iStackKeyClass);
                Object value = getStackByKey.invoke(storage, key);
                if (value != null && keyAmountClass.isInstance(value)) {
                    long amount = (long) keyAmountClass.getMethod("amount").invoke(value);
                    if (amount > 0) {
                        return amount;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method getStorage = storage.getClass().getMethod("getStorage");
                Object entries = getStorage.invoke(storage);
                if (entries instanceof Iterable<?> iterable) {
                    long total = 0;
                    Method keyMethod = keyAmountClass.getMethod("key");
                    Method amountMethod = keyAmountClass.getMethod("amount");
                    Method sameComponentsMethod = iStackKeyClass.getMethod("isSameTypeSameComponents", iStackKeyClass);
                    for (Object entry : iterable) {
                        if (entry == null || !keyAmountClass.isInstance(entry)) continue;
                        Object entryKey = keyMethod.invoke(entry);
                        if (entryKey == null) continue;
                        Object same = sameComponentsMethod.invoke(entryKey, key);
                        if (Boolean.TRUE.equals(same)) {
                            total += (long) amountMethod.invoke(entry);
                        }
                    }
                    return Math.max(0, total);
                }
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Exception e) {
            ModLogger.warn("BDProxy getStoredAmountFromMenu failed: {}", e.getMessage());
        }
        return 0;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
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

    // ---- Craft one item and move to inventory ----

    private static boolean craftOneResult(Player player, AbstractContainerMenu menu, int resultSlotIndex, boolean ejectRemaining) {
        var inventory = player.getInventory();
        refreshCraftingResult(menu);
        var resultSlot = menu.slots.get(resultSlotIndex);
        if (!resultSlot.hasItem()) {
            ModLogger.debug("BD_QUICKCRAFT single craft skipped: empty result slot after refresh");
            return false;
        }

        menu.clicked(resultSlotIndex, 0, ClickType.PICKUP, player);
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) return false;

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
            if (ejectRemaining) resultSlot.set(remaining);
            return false; // inventory full
        }
        return true;
    }

    // ---- Single craft: craft one item from BD network to inventory ----

    public static boolean singleCraft(Player player) {
        if (!isLoaded()) return false;
        try {
            var menu = player.containerMenu;
            if (!craftMenuClass.isInstance(menu)) return false;

            int resultSlotIndex = (int) craftMenuClass.getField("resultSlotIndex").get(menu);
            if (resultSlotIndex < 0 || resultSlotIndex >= menu.slots.size()) return false;

            boolean ok = craftOneResult(player, menu, resultSlotIndex, true);

            player.getInventory().setChanged();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Single craft: craft one item from BD crafting grid directly into BD network ----

    public static boolean singleCraftToNetwork(Player player) {
        if (!isLoaded() || player == null) return false;
        try {
            var menu = player.containerMenu;
            if (!craftMenuClass.isInstance(menu)) return false;

            int resultSlotIndex = (int) craftMenuClass.getField("resultSlotIndex").get(menu);
            if (resultSlotIndex < 0 || resultSlotIndex >= menu.slots.size()) return false;

            refreshCraftingResult(menu);
            var resultSlot = menu.slots.get(resultSlotIndex);
            if (!resultSlot.hasItem()) {
                ModLogger.debug("BD_QUICKCRAFT single craft to network skipped: empty result slot after refresh");
                return false;
            }

            ItemStack resultStack = resultSlot.getItem().copy();
            if (resultStack.isEmpty()) return false;

            Object storage = getStorageForPlayer(player);
            if (storage == null) return false;

            var keyCtor = itemKeyClass.getConstructor(ItemStack.class);
            Object key = keyCtor.newInstance(resultStack);
            var insertMethod = storage.getClass().getMethod("insert", iStackKeyClass, long.class, boolean.class);
            var amountMethod = keyAmountClass.getMethod("amount");

            // Do not consume ingredients unless the BD network can accept the crafted output.
            Object simulatedRemaining = insertMethod.invoke(storage, key, (long) resultStack.getCount(), true);
            long simulatedRemainingAmount = Math.max(0L, (long) amountMethod.invoke(simulatedRemaining));
            if (simulatedRemainingAmount > 0) {
                return false;
            }

            menu.clicked(resultSlotIndex, 0, ClickType.PICKUP, player);
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) return false;

            Object carriedKey = keyCtor.newInstance(carried);
            Object remaining = insertMethod.invoke(storage, carriedKey, (long) carried.getCount(), false);
            long remainingAmount = Math.max(0L, (long) amountMethod.invoke(remaining));
            if (remainingAmount > 0) {
                carried.setCount((int) Math.min(Integer.MAX_VALUE, remainingAmount));
                menu.setCarried(carried);
                return false;
            }

            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().setChanged();
            menu.broadcastChanges();
            return true;
        } catch (Exception e) {
            ModLogger.warn("BDProxy singleCraftToNetwork failed: {}", e.getMessage());
            return false;
        }
    }

    private static void refreshCraftingResult(AbstractContainerMenu menu) {
        if (menu == null || !craftMenuClass.isInstance(menu)) {
            return;
        }
        try {
            Field craftSlotsField = findField(menu.getClass(), "craftSlots");
            if (craftSlotsField == null) {
                return;
            }
            craftSlotsField.setAccessible(true);
            Object craftSlotsObj = craftSlotsField.get(menu);
            if (craftSlotsObj instanceof Container craftSlots) {
                menu.slotsChanged(craftSlots);
                menu.broadcastChanges();
            }
        } catch (Exception e) {
            ModLogger.warn("BD_QUICKCRAFT refresh crafting result failed: {}", e.getMessage());
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

            for (int c = 0; c < 512; c++) {
                if (!craftOneResult(player, menu, resultSlotIndex, false)) break;
            }

            player.getInventory().setChanged();
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
