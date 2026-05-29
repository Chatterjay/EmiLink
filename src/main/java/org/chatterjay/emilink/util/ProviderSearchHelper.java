package org.chatterjay.emilink.util;

import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Method;

public final class ProviderSearchHelper {
    private static boolean checked = false;
    private static boolean available = false;
    private static Method setLastProcessingName;
    private static Method presetCraftingProviderSearchKey;
    private static Method mapRecipeTypeToSearchKey;

    /** Tracks the last non-"jemi" category from RecipeScreen's getFocusedCategory() */
    private static String lastRecipeCategory;

    private ProviderSearchHelper() {}

    private static void init() {
        if (checked) return;
        checked = true;
        try {
            Class<?> clazz = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
            setLastProcessingName = clazz.getMethod("setLastProcessingName", String.class);
            mapRecipeTypeToSearchKey = clazz.getMethod("mapRecipeTypeToSearchKey", Recipe.class);
            // presetCraftingProviderSearchKey is only available in EAEP 1.5.4+
            try {
                presetCraftingProviderSearchKey = clazz.getMethod("presetCraftingProviderSearchKey");
            } catch (NoSuchMethodException ignored) {
            }
            available = true;
        } catch (Throwable t) {
        }
    }

    public static void setLastProcessingName(String name) {
        init();
        if (available && name != null) {
            try {
                ModLogger.info("ProviderSearchHelper: setting EAEP RecipeTypeNameConfig to '{}'", name);
                setLastProcessingName.invoke(null, name);
            } catch (Throwable e) {
                ModLogger.warn("ProviderSearchHelper: setLastProcessingName failed: {}", e.getMessage());
            }
        } else {
            ModLogger.info("ProviderSearchHelper: setLastProcessingName skipped (available={}, name={})", available, name);
        }
    }

    public static void presetCraftingProviderSearchKey() {
        init();
        if (presetCraftingProviderSearchKey != null) {
            try { presetCraftingProviderSearchKey.invoke(null); } catch (Throwable t) {
            }
        }
    }

    public static String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        init();

        if (recipe == null) {
            ModLogger.info("ProviderSearchHelper: mapRecipeTypeToSearchKey called with null recipe");
            return null;
        }

        ModLogger.info("ProviderSearchHelper: mapping recipe type to search key, recipe={}", recipe.getId());

        // Try EAEP's mapping first
        if (available) {
            try {
                String result = (String) mapRecipeTypeToSearchKey.invoke(null, recipe);
                ModLogger.info("ProviderSearchHelper: EAEP mapRecipeTypeToSearchKey returned '{}' for recipe {}",
                        result, recipe.getId());
                if (result != null) return result;
            } catch (Throwable e) {
                ModLogger.warn("ProviderSearchHelper: EAEP mapRecipeTypeToSearchKey failed: {}", e.getMessage());
            }
        }

        // Fallback: derive search key from recipe class name
        String fallback = deriveSearchKey(recipe.getClass());
        ModLogger.info("ProviderSearchHelper: fallback derived search key '{}' from recipe class {}",
                fallback, recipe.getClass().getSimpleName());
        return fallback;
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

    /**
     * Saves the last focused recipe category from EMI's RecipeScreen.
     * This is a separate field from RecipeTypeNameConfig's lastProviderSearchKey
     * so it won't be consumed by the ProviderSelectScreen constructor.
     */
    public static void setLastFocusedRecipeCategory(String category) {
        lastRecipeCategory = category;
        ModLogger.info("ProviderSearchHelper: saved last recipe category '{}'", category);
    }

    /**
     * Returns and clears the last recipe category.
     * Called by ProviderSelectHandler to set the search key directly.
     */
    public static String consumeLastRecipeCategory() {
        String val = lastRecipeCategory;
        lastRecipeCategory = null;
        if (val != null) {
            ModLogger.info("ProviderSearchHelper: consumed last recipe category '{}'", val);
        }
        return val;
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
