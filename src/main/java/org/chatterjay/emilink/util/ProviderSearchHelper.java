package org.chatterjay.emilink.util;

import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

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
            Class<?> clazz = Class.forName("com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig");
            setLastProcessingName = clazz.getMethod("setLastProcessingName", String.class);
            presetCraftingProviderSearchKey = clazz.getMethod("presetCraftingProviderSearchKey");
            mapRecipeTypeToSearchKey = clazz.getMethod("mapRecipeTypeToSearchKey", Recipe.class);
            available = true;
        } catch (Throwable t) {
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
     * Set the last processing name (search key) from a custom EMI recipe
     * that has no corresponding Vanilla RecipeHolder.
     */
    public static void setFromEmiRecipe(EmiRecipe emiRecipe) {
        if (emiRecipe == null) return;
        init();
        if (!available) return;

        ResourceLocation categoryId = emiRecipe.getCategory().getId();
        if (categoryId == null) return;

        String searchKey = categoryId.getPath();
        if (searchKey != null && !searchKey.isBlank()) {
            setLastProcessingName(searchKey);
            ModLogger.info("ProviderSearch: set search key '{}' from EMI recipe category '{}' (recipe {})",
                    searchKey, categoryId, emiRecipe.getId());
        }
    }

    private static String deriveSearchKey(Class<?> recipeClass) {
        String simpleName = recipeClass.getSimpleName();

        String name = simpleName;
        if (name.endsWith("Recipe")) {
            name = name.substring(0, name.length() - 6);
        }
        if (name.isEmpty()) return null;

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
