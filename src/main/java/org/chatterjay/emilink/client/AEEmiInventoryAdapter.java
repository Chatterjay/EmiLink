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
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class AEEmiInventoryAdapter {
    private static final long CACHE_TTL_MS = 250L;

    private static Boolean reflectionReady;
    private static Class<?> aeItemKeyClass;
    private static Method getClientRepoMethod;
    private static Method getAllEntriesMethod;
    private static Field rawEntriesField;
    private static Method getWhatMethod;
    private static Method getStoredAmountMethod;
    private static Method isMeaningfulMethod;
    private static Method toStackMethod;

    private static AbstractContainerMenu cachedMenu;
    private static Object cachedRepo;
    private static int cachedEntryCount = -1;
    private static long cachedAt;
    private static EmiPlayerInventory cachedInventory;

    private AEEmiInventoryAdapter() {
    }

    public static EmiPlayerInventory createInventoryForCurrentAeContext() {
        AbstractContainerScreen<?> screen = unwrapHandledScreen(Minecraft.getInstance().screen);
        if (screen == null) return null;
        AbstractContainerMenu menu = screen.getMenu();
        if (!AE2Proxy.isLoaded() || !initReflection(menu)) return null;

        try {
            Object repo = getClientRepoMethod.invoke(menu);
            if (repo == null) return null;

            EntrySource entrySource = getAllEntries(repo);
            Collection<?> entries = entrySource.entries();
            if (entries == null || entries.isEmpty()) return null;

            long now = System.currentTimeMillis();
            int entryCount = entries.size();
            if (cachedInventory != null
                    && cachedMenu == menu
                    && cachedRepo == repo
                    && cachedEntryCount == entryCount
                    && now - cachedAt <= CACHE_TTL_MS) {
                return cachedInventory;
            }

            List<EmiStack> stacks = new ArrayList<>();
            int menuStacks = addMenuStacks(menu, stacks);
            int networkStacks = addNetworkStacks(entries, stacks);
            if (menuStacks == 0 && networkStacks == 0) return null;

            EmiPlayerInventory inventory = new EmiPlayerInventory(stacks);
            cachedMenu = menu;
            cachedRepo = repo;
            cachedEntryCount = entryCount;
            cachedAt = now;
            cachedInventory = inventory;

            ModLogger.debug("AE EMI inventory injected: screen={} menuStacks={} networkEntries={} repoEntries={} source={} total={}",
                    screen.getClass().getName(), menuStacks, networkStacks, entryCount, entrySource.source(), stacks.size());
            return inventory;
        } catch (Throwable t) {
            ModLogger.warn("AE EMI inventory injection failed: {}: {}",
                    t.getClass().getSimpleName(), t.getMessage());
            return null;
        }
    }

    private static boolean initReflection(AbstractContainerMenu menu) {
        if (!AE2Proxy.isLoaded() || menu == null) {
            reflectionReady = false;
            return false;
        }

        try {
            if (getClientRepoMethod == null || !getClientRepoMethod.getDeclaringClass().isInstance(menu)) {
                getClientRepoMethod = findMethod(menu.getClass(), "getClientRepo");
                if (getClientRepoMethod == null) {
                    reflectionReady = false;
                    return false;
                }
                getClientRepoMethod.setAccessible(true);
            }

            if (aeItemKeyClass == null) {
                aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            }
            if (getWhatMethod == null || getStoredAmountMethod == null || isMeaningfulMethod == null) {
                Class<?> entryClass = Class.forName("appeng.menu.me.common.GridInventoryEntry");
                getWhatMethod = entryClass.getMethod("getWhat");
                getStoredAmountMethod = entryClass.getMethod("getStoredAmount");
                isMeaningfulMethod = entryClass.getMethod("isMeaningful");
            }
            if (toStackMethod == null) {
                toStackMethod = aeItemKeyClass.getMethod("toStack");
            }

            reflectionReady = true;
        } catch (Throwable t) {
            ModLogger.warn("AE EMI inventory reflection init failed: {}: {}",
                    t.getClass().getSimpleName(), t.getMessage());
            reflectionReady = false;
        }
        return reflectionReady;
    }

    private static EntrySource getAllEntries(Object repo) throws ReflectiveOperationException {
        if (getAllEntriesMethod == null || !getAllEntriesMethod.getDeclaringClass().isInstance(repo)) {
            getAllEntriesMethod = findMethod(repo.getClass(), "getAllEntries");
            if (getAllEntriesMethod != null) {
                getAllEntriesMethod.setAccessible(true);
            }
        }

        Collection<?> publicEntries = null;
        if (getAllEntriesMethod != null) {
            Object entries = getAllEntriesMethod.invoke(repo);
            if (entries instanceof Collection<?> collection) {
                publicEntries = collection;
            }
        }

        Collection<?> rawEntries = getRawEntries(repo);
        if (rawEntries != null && (publicEntries == null || rawEntries.size() > publicEntries.size())) {
            ModLogger.debug("AE EMI inventory repo detected: repo={} publicEntries={} rawEntries={}",
                    repo.getClass().getName(), publicEntries == null ? -1 : publicEntries.size(), rawEntries.size());
            return new EntrySource(rawEntries, "raw");
        }
        if (publicEntries != null) {
            ModLogger.debug("AE EMI inventory repo detected: repo={} publicEntries={} rawEntries={}",
                    repo.getClass().getName(), publicEntries.size(), rawEntries == null ? -1 : rawEntries.size());
            return new EntrySource(publicEntries, "public");
        }
        return new EntrySource(null, "none");
    }

    /**
     * Some GTL AE add-ons expose a filtered page through getAllEntries(). The vanilla Repo keeps
     * the synchronized network state in this map, independently from its scrollable view.
     */
    private static Collection<?> getRawEntries(Object repo) throws IllegalAccessException {
        if (rawEntriesField == null || !rawEntriesField.getDeclaringClass().isInstance(repo)) {
            rawEntriesField = findField(repo.getClass(), "entries");
            if (rawEntriesField != null) {
                rawEntriesField.setAccessible(true);
            }
        }
        if (rawEntriesField == null) return null;

        Object rawEntries = rawEntriesField.get(repo);
        if (rawEntries instanceof Map<?, ?> map) return map.values();
        return rawEntries instanceof Collection<?> collection ? collection : null;
    }

    private static int addMenuStacks(AbstractContainerMenu menu, List<EmiStack> stacks) {
        int added = 0;
        for (Slot slot : menu.slots) {
            if (slot == null || slot instanceof ResultSlot) continue;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                stacks.add(EmiStack.of(stack));
                added++;
            }
        }
        return added;
    }

    private static int addNetworkStacks(Collection<?> entries, List<EmiStack> stacks) throws ReflectiveOperationException {
        int added = 0;
        for (Object entry : entries) {
            try {
                if (entry == null) continue;
                if (isMeaningfulMethod != null && Boolean.FALSE.equals(isMeaningfulMethod.invoke(entry))) continue;

                long stored = Math.max(0L, (long) getStoredAmountMethod.invoke(entry));
                if (stored <= 0) continue;

                Object what = getWhatMethod.invoke(entry);
                if (!aeItemKeyClass.isInstance(what)) continue;

                Object stackObject = toStackMethod.invoke(what);
                if (!(stackObject instanceof ItemStack itemStack) || itemStack.isEmpty()) continue;

                stacks.add(EmiStack.of(itemStack, stored));
                added++;
            } catch (ReflectiveOperationException | ClassCastException error) {
                ModLogger.debug("AE EMI inventory skipped malformed network entry: {}", error.toString());
            }
        }
        return added;
    }

    private record EntrySource(Collection<?> entries, String source) {
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

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
        }
        return null;
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
