package org.chatterjay.emiextend.util;

import net.minecraft.world.item.crafting.Recipe;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Method;

public final class ProviderSearchHelper {
    private static boolean checked = false;
    private static boolean available = false;
    private static Method setLastProcessingName;
    private static Method presetCraftingProviderSearchKey;
    private static Method mapRecipeTypeToSearchKey;

    private ProviderSearchHelper() {}

    private static void init() {
        if (checked) return;
        checked = true;
        try {
            Class<?> clazz = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
            setLastProcessingName = clazz.getMethod("setLastProcessingName", String.class);
            presetCraftingProviderSearchKey = clazz.getMethod("presetCraftingProviderSearchKey");
            mapRecipeTypeToSearchKey = clazz.getMethod("mapRecipeTypeToSearchKey", Recipe.class);
            available = true;
            ModLogger.debug("ProviderSearchHelper: ExtendedAE_Plus ExtendedAEPatternUploadUtil found");
        } catch (Throwable t) {
            ModLogger.debug("ProviderSearchHelper: ExtendedAE_Plus not available ({})", t.getMessage());
        }
    }

    public static void setLastProcessingName(String name) {
        init();
        if (available && name != null) {
            try { setLastProcessingName.invoke(null, name); } catch (Throwable ignored) {}
        }
    }

    public static void presetCraftingProviderSearchKey() {
        init();
        if (available) {
            try { presetCraftingProviderSearchKey.invoke(null); } catch (Throwable ignored) {}
        }
    }

    public static String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        init();
        if (available && recipe != null) {
            try { return (String) mapRecipeTypeToSearchKey.invoke(null, recipe); } catch (Throwable ignored) {}
        }
        return null;
    }
}
