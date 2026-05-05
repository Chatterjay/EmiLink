package org.chatterjay.emiextend.integration;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public class AE2Proxy {
    private static Boolean loaded;

    public static boolean isLoaded() {
        if (loaded == null) {
            var modList = ModList.get();
            loaded = modList != null && modList.isLoaded("ae2");
        }
        return loaded;
    }

    // ---- Screen type checks ----

    public static boolean isCraftConfirmScreen(Screen screen) {
        if (!isLoaded() || screen == null) return false;
        try {
            return Class.forName("appeng.client.gui.me.crafting.CraftConfirmScreen").isInstance(screen);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMEStorageScreen(Screen screen) {
        if (!isLoaded() || screen == null) return false;
        try {
            return Class.forName("appeng.client.gui.me.common.MEStorageScreen").isInstance(screen);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- AEItemKey helpers ----

    private static Class<?> getAEItemKeyClass() {
        try {
            return Class.forName("appeng.api.stacks.AEItemKey");
        } catch (Exception e) {
            return null;
        }
    }

    /** Convert AEItemKey under cursor in CraftConfirmScreen to an ItemStack. */
    public static ItemStack getStackUnderMouse(Screen screen, int mouseX, int mouseY) {
        if (!isLoaded() || screen == null) return ItemStack.EMPTY;
        try {
            Class<?> ccsClass = Class.forName("appeng.client.gui.me.crafting.CraftConfirmScreen");
            if (!ccsClass.isInstance(screen)) return ItemStack.EMPTY;

            Method getStackMethod = ccsClass.getMethod("getStackUnderMouse", int.class, int.class);
            Object swb = getStackMethod.invoke(screen, mouseX, mouseY);
            if (swb == null) return ItemStack.EMPTY;

            // StackWithBounds.stack() → GenericStack
            Object genericStack = swb.getClass().getMethod("stack").invoke(swb);
            if (genericStack == null) return ItemStack.EMPTY;

            // GenericStack.what() → AEKey
            Object what = genericStack.getClass().getMethod("what").invoke(genericStack);
            if (what == null) return ItemStack.EMPTY;

            // Check if AEItemKey → toStack()
            Class<?> aeItemKeyClass = getAEItemKeyClass();
            if (aeItemKeyClass == null || !aeItemKeyClass.isInstance(what)) return ItemStack.EMPTY;
            return (ItemStack) aeItemKeyClass.getMethod("toStack").invoke(what);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ---- Wireless terminal ----

    public static boolean isWirelessTerminal(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return false;
        try {
            Class<?> clazz = Class.forName("appeng.items.tools.powered.WirelessTerminalItem");
            return clazz.isInstance(stack.getItem());
        } catch (Exception e) {
            return false;
        }
    }

    public static Class<?> getWirelessTerminalClass() {
        if (!isLoaded()) return null;
        try {
            return Class.forName("appeng.items.tools.powered.WirelessTerminalItem");
        } catch (Exception e) {
            return null;
        }
    }
}
