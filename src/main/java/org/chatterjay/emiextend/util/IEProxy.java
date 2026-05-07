package org.chatterjay.emiextend.util;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * InventoryEssentials integration proxy via reflection.
 * Adds AE and BD screen classes to IE's ignore list so IE's Space+click
 * handler doesn't interfere with our custom handling.
 */
public final class IEProxy {
    private static boolean checked = false;
    private static boolean available = false;
    private static Method addIgnoredScreenClass;

    private static final Set<String> KNOWN_SCREENS = new LinkedHashSet<>();

    static {
        // AE2 terminal screens (MEStorageScreen and known subclasses)
        KNOWN_SCREENS.add("appeng.client.gui.me.common.MEStorageScreen");
        KNOWN_SCREENS.add("appeng.client.gui.me.common.WCTScreen");

        // BD network GUI
        KNOWN_SCREENS.add("com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI");
    }

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
        } catch (Exception e) {
            ModLogger.debug("IEProxy: init failed ({})", e.getMessage());
        }
    }

    /**
     * Register AE and BD screen classes with IE's ignore list so IE
     * skips its Space+click handlers on those screens. Safe to call
     * multiple times — only registers once.
     */
    public static void registerIgnoredScreens() {
        init();
        if (!available) return;

        for (String className : KNOWN_SCREENS) {
            try {
                addIgnoredScreenClass.invoke(null, className);
                ModLogger.debug("IEProxy: added {} to IE ignored screens", className);
            } catch (Exception e) {
                ModLogger.debug("IEProxy: failed to ignore {} ({})", className, e.getMessage());
            }
        }
    }
}
