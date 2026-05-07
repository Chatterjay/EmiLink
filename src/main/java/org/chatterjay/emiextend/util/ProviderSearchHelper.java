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

        // Try EAEP's mapping first
        if (available && recipe != null) {
            try {
                String result = (String) mapRecipeTypeToSearchKey.invoke(null, recipe);
                if (result != null) return result;
            } catch (Throwable ignored) {}
        }

        // Fallback: derive search key from recipe class name
        if (recipe != null) {
            return deriveSearchKey(recipe.getClass());
        }

        return null;
    }

    /**
     * Derive a search key from the recipe class when EAEP has no mapping.
     * e.g. ReactionChamberRecipe → "reaction chamber"
     */
    private static String deriveSearchKey(Class<?> recipeClass) {
        String simpleName = recipeClass.getSimpleName();

        // Remove common suffixes
        String name = simpleName;
        if (name.endsWith("Recipe")) {
            name = name.substring(0, name.length() - 6);
        }
        if (name.isEmpty()) return null;

        // Split camelCase into words
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append(' ');
            }
            sb.append(Character.toLowerCase(c));
        }

        String key = sb.toString().trim();
        return key.isEmpty() ? null : key;
    }
}
