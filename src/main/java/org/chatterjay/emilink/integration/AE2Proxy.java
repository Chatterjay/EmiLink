package org.chatterjay.emilink.integration;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public class AE2Proxy {
    private static Boolean loaded;

    private static Class<?> craftConfirmScreenClass;
    private static Class<?> meStorageScreenClass;
    private static Class<?> patternEncodingTermScreenClass;
    private static Class<?> wirelessTerminalItemClass;
    private static Class<?> aeItemKeyClass;

    public static boolean isLoaded() {
        if (loaded == null) {
            var modList = ModList.get();
            loaded = modList != null && modList.isLoaded("ae2");
        }
        return loaded;
    }

    public static boolean isCraftConfirmScreen(Screen screen) {
        if (!isLoaded() || screen == null) return false;
        try {
            if (craftConfirmScreenClass == null)
                craftConfirmScreenClass = Class.forName("appeng.client.gui.me.crafting.CraftConfirmScreen");
            return craftConfirmScreenClass.isInstance(screen);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMEStorageScreen(Screen screen) {
        if (!isLoaded() || screen == null) return false;
        try {
            if (meStorageScreenClass == null)
                meStorageScreenClass = Class.forName("appeng.client.gui.me.common.MEStorageScreen");
            return meStorageScreenClass.isInstance(screen);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPatternEncodingTermScreen(Screen screen) {
        if (!isLoaded() || screen == null) return false;
        try {
            if (patternEncodingTermScreenClass == null)
                patternEncodingTermScreenClass = Class.forName("appeng.client.gui.me.items.PatternEncodingTermScreen");
            return patternEncodingTermScreenClass.isInstance(screen);
        } catch (Exception e) {
            return false;
        }
    }

    private static Class<?> getAEItemKeyClass() {
        try {
            if (aeItemKeyClass == null)
                aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            return aeItemKeyClass;
        } catch (Exception e) {
            return null;
        }
    }

    public static ItemStack getStackUnderMouse(Screen screen, int mouseX, int mouseY) {
        if (!isLoaded() || screen == null) return ItemStack.EMPTY;
        try {
            if (craftConfirmScreenClass == null)
                craftConfirmScreenClass = Class.forName("appeng.client.gui.me.crafting.CraftConfirmScreen");
            if (!craftConfirmScreenClass.isInstance(screen)) return ItemStack.EMPTY;

            Method getStackMethod = craftConfirmScreenClass.getMethod("getStackUnderMouse", double.class, double.class);
            Object swb = getStackMethod.invoke(screen, mouseX, mouseY);
            if (swb == null) return ItemStack.EMPTY;

            Object genericStack = swb.getClass().getMethod("stack").invoke(swb);
            if (genericStack == null) return ItemStack.EMPTY;

            Object what = genericStack.getClass().getMethod("what").invoke(genericStack);
            if (what == null) return ItemStack.EMPTY;

            Class<?> keyClass = getAEItemKeyClass();
            if (keyClass == null || !keyClass.isInstance(what)) return ItemStack.EMPTY;
            return (ItemStack) keyClass.getMethod("toStack").invoke(what);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static boolean isWirelessTerminal(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return false;
        try {
            if (wirelessTerminalItemClass == null)
                wirelessTerminalItemClass = Class.forName("appeng.items.tools.powered.WirelessTerminalItem");
            return wirelessTerminalItemClass.isInstance(stack.getItem());
        } catch (Exception e) {
            return false;
        }
    }

    public static Class<?> getWirelessTerminalClass() {
        if (!isLoaded()) return null;
        try {
            if (wirelessTerminalItemClass == null)
                wirelessTerminalItemClass = Class.forName("appeng.items.tools.powered.WirelessTerminalItem");
            return wirelessTerminalItemClass;
        } catch (Exception e) {
            return null;
        }
    }
}
