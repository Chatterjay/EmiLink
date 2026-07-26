package org.chatterjay.emilink.client;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emilink.integration.BDProxy;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class BDEmiInventoryAdapter {
    private static Boolean reflectionReady;
    private static Class<?> keyAmountClass;
    private static Class<?> itemStackKeyClass;
    private static Class<?> abstractStackTypedSlotClass;

    private BDEmiInventoryAdapter() {
    }

    public static EmiPlayerInventory createInventoryForCurrentBdContext() {
        AbstractContainerScreen<?> screen = unwrapHandledScreen(Minecraft.getInstance().screen);
        if (screen == null) return null;
        AbstractContainerMenu menu = screen.getMenu();
        if (!BDProxy.isBDBaseMenu(menu) || !initReflection()) return null;

        try {
            List<EmiStack> stacks = new ArrayList<>();
            int menuStacks = addMenuInputStacks(menu, stacks);
            int storageStacks = addNetworkStorageStacks(menu, stacks);
            if (menuStacks == 0 && storageStacks == 0) return null;

            ModLogger.debug("BD EMI inventory injected: screen={} menuStacks={} storageEntries={} total={}",
                    screen.getClass().getName(), menuStacks, storageStacks, stacks.size());
            return new EmiPlayerInventory(stacks);
        } catch (Throwable t) {
            ModLogger.warn("BD EMI inventory injection failed: {}: {}",
                    t.getClass().getSimpleName(), t.getMessage());
            return null;
        }
    }

    private static AbstractContainerScreen<?> unwrapHandledScreen(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> handled) {
            return handled;
        }
        if (screen == null) return null;

        try {
            Field oldField = findField(screen.getClass(), "old");
            if (oldField == null) return null;
            oldField.setAccessible(true);
            Object old = oldField.get(screen);
            return old instanceof AbstractContainerScreen<?> handled ? handled : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean initReflection() {
        if (reflectionReady != null) return reflectionReady;
        if (!BDProxy.isLoaded()) {
            reflectionReady = false;
            return false;
        }
        try {
            keyAmountClass = Class.forName("com.wintercogs.beyonddimensions.api.storage.key.KeyAmount");
            itemStackKeyClass = Class.forName("com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey");
            abstractStackTypedSlotClass = Class.forName("com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot");
            reflectionReady = true;
        } catch (Throwable t) {
            ModLogger.warn("BD EMI inventory reflection init failed: {}: {}",
                    t.getClass().getSimpleName(), t.getMessage());
            reflectionReady = false;
        }
        return reflectionReady;
    }

    private static int addMenuInputStacks(AbstractContainerMenu menu, List<EmiStack> stacks) {
        int added = 0;
        int resultSlotIndex = BDProxy.getResultSlotIndex(menu);
        for (Slot slot : menu.slots) {
            if (slot == null) continue;
            if (slot.index == resultSlotIndex || slot instanceof ResultSlot) continue;
            if (abstractStackTypedSlotClass != null && abstractStackTypedSlotClass.isInstance(slot)) continue;

            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                stacks.add(EmiStack.of(stack));
                added++;
            }
        }
        return added;
    }

    private static int addNetworkStorageStacks(AbstractContainerMenu menu, List<EmiStack> stacks) throws ReflectiveOperationException {
        Object storage = getMenuStorage(menu);
        if (storage == null) return 0;

        Method getStorage = storage.getClass().getMethod("getStorage");
        Object entries = getStorage.invoke(storage);
        if (!(entries instanceof Iterable<?> iterable)) return 0;

        Method isEmpty = keyAmountClass.getMethod("isEmpty");
        Method key = keyAmountClass.getMethod("key");
        Method amount = keyAmountClass.getMethod("amount");
        Method getReadOnlyStack = itemStackKeyClass.getMethod("getReadOnlyStack");

        int added = 0;
        for (Object entry : iterable) {
            if (entry == null || !keyAmountClass.isInstance(entry)) continue;
            if (Boolean.TRUE.equals(isEmpty.invoke(entry))) continue;

            Object stackKey = key.invoke(entry);
            if (!itemStackKeyClass.isInstance(stackKey)) continue;

            Object readOnly = getReadOnlyStack.invoke(stackKey);
            if (!(readOnly instanceof ItemStack itemStack) || itemStack.isEmpty()) continue;

            long count = Math.max(0L, (long) amount.invoke(entry));
            if (count <= 0) continue;
            stacks.add(EmiStack.of(itemStack, count));
            added++;
        }
        return added;
    }

    private static Object getMenuStorage(AbstractContainerMenu menu) throws IllegalAccessException {
        Field storageField = findField(menu.getClass(), "storage");
        if (storageField == null) return null;
        storageField.setAccessible(true);
        return storageField.get(menu);
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
}
