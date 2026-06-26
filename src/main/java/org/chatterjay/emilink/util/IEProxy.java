package org.chatterjay.emilink.util;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public final class IEProxy {
    private static boolean checked = false;
    private static boolean available = false;
    private static Method addIgnoredScreenClass;

    private static final String[] KNOWN_SCREENS = {
            "appeng.client.gui.me.common.MEStorageScreen",
            "appeng.client.gui.me.common.WCTScreen",
            "com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI"
    };

    private IEProxy() {}

    private static void init() {
        if (checked) return;
        checked = true;
        var modList = ModList.get();
        if (modList == null || !modList.isLoaded("inventoryessentials")) return;

        try {
            Class<?> clazz = Class.forName("net.blay09.mods.inventoryessentials.InventoryEssentialsIgnores");
            addIgnoredScreenClass = clazz.getMethod("addIgnoredScreenClass", String.class);
            available = true;
        } catch (Exception ignored) {}
    }

    public static void registerIgnoredScreens() {
        init();
        if (!available) return;

        for (String className : KNOWN_SCREENS) {
            try {
                addIgnoredScreenClass.invoke(null, className);
            } catch (Exception ignored) {}
        }
    }
}
