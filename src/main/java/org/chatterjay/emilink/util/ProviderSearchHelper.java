package org.chatterjay.emilink.util;

import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;

public final class ProviderSearchHelper {
    private static final String[] PATTERN_UPLOAD_UTIL_CLASSES = {
            // ExtendedAE Plus 1.4.x, bundled by GregTech Leisure 2
            "com.extendedae_plus.util.ExtendedAEPatternUploadUtil",
            // ExtendedAE Plus 1.5.x and later
            "com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil"
    };

    private static boolean checked = false;
    private static boolean available = false;
    private static String resolvedUtilClass;
    private static Method setLastProcessingName;
    private static Method presetCraftingProviderSearchKey;
    private static Method mapRecipeTypeToSearchKey;
    private static Method resolveKeyToAlias;

    /** Tracks the last non-"jemi" category from RecipeScreen's getFocusedCategory() */
    private static String lastRecipeCategory;

    private ProviderSearchHelper() {}

    private static void init() {
        if (checked) return;
        checked = true;
        for (String className : PATTERN_UPLOAD_UTIL_CLASSES) {
            try {
                Class<?> clazz = Class.forName(className);
                setLastProcessingName = clazz.getMethod("setLastProcessingName", String.class);
                mapRecipeTypeToSearchKey = clazz.getMethod("mapRecipeTypeToSearchKey", Recipe.class);
                // presetCraftingProviderSearchKey is only available in EAEP 1.5.4+
                try {
                    presetCraftingProviderSearchKey = clazz.getMethod("presetCraftingProviderSearchKey");
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    resolveKeyToAlias = clazz.getMethod("resolveKeyToAlias", String.class);
                } catch (NoSuchMethodException ignored) {
                }
                resolvedUtilClass = className;
                available = true;
                ModLogger.debug("ProviderSearchHelper: loaded ExtendedAE Plus upload API from {}", className);
                return;
            } catch (Throwable t) {
                ModLogger.debug("ProviderSearchHelper: upload API {} unavailable: {}",
                        className, t.getClass().getSimpleName());
            }
        }
        ModLogger.debug("ProviderSearchHelper: no compatible ExtendedAE Plus upload API was found");
    }

    public static void setLastProcessingName(String name) {
        init();
        if (available && name != null) {
            try {
                ModLogger.debug("ProviderSearchHelper: setting EAEP upload search key '{}' through {}",
                        name, resolvedUtilClass);
                setLastProcessingName.invoke(null, name);
            } catch (Throwable e) {
                ModLogger.warn("ProviderSearchHelper: setLastProcessingName failed: {}", e.getMessage());
            }
        } else {
            ModLogger.debug("ProviderSearchHelper: setLastProcessingName skipped (available={}, name={})", available, name);
        }
    }

    public static void presetCraftingProviderSearchKey() {
        init();
        if (presetCraftingProviderSearchKey != null) {
            try { presetCraftingProviderSearchKey.invoke(null); } catch (Throwable t) {
            }
        }
    }

    /**
     * Resolve a search key to a human-friendly alias using EAEP if available.
     */
    public static String resolveKeyToAlias(String rawKey) {
        init();
        if (available && resolveKeyToAlias != null && rawKey != null) {
            try {
                return (String) resolveKeyToAlias.invoke(null, rawKey);
            } catch (Throwable e) {
                ModLogger.warn("ProviderSearchHelper: resolveKeyToAlias failed: {}", e.getMessage());
            }
        }
        // Fallback: return raw key as-is
        return rawKey;
    }

    public static String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        init();

        if (recipe == null) {
            ModLogger.debug("ProviderSearchHelper: mapRecipeTypeToSearchKey called with null recipe");
            return null;
        }

        ModLogger.debug("ProviderSearchHelper: mapping recipe type to search key, recipe={}", recipe.getId());

        // Try EAEP's mapping first
        if (available && mapRecipeTypeToSearchKey != null) {
            try {
                String result = (String) mapRecipeTypeToSearchKey.invoke(null, recipe);
                ModLogger.debug("ProviderSearchHelper: EAEP mapRecipeTypeToSearchKey returned '{}' for recipe {}",
                        result, recipe.getId());
                if (result != null) return result;
            } catch (Throwable e) {
                ModLogger.warn("ProviderSearchHelper: EAEP mapRecipeTypeToSearchKey failed: {}", e.getMessage());
            }
        }

        // Fallback: derive search key from recipe class name
        String fallback = deriveSearchKey(recipe.getClass());
        ModLogger.debug("ProviderSearchHelper: fallback derived search key '{}' from recipe class {}",
                fallback, recipe.getClass().getSimpleName());
        return fallback;
    }

    /**
     * Saves the last focused recipe category from EMI's RecipeScreen.
     * This is a separate field from RecipeTypeNameConfig's lastProviderSearchKey
     * so it won't be consumed by the ProviderSelectScreen constructor.
     */
    public static void setLastFocusedRecipeCategory(String category) {
        lastRecipeCategory = category;
        ModLogger.debug("ProviderSearchHelper: saved last recipe category '{}'", category);
    }

    /**
     * Returns and clears the last recipe category.
     * Called by ProviderSelectHandler to set the search key directly.
     */
    public static String consumeLastRecipeCategory() {
        String val = lastRecipeCategory;
        lastRecipeCategory = null;
        if (val != null) {
            ModLogger.debug("ProviderSearchHelper: consumed last recipe category '{}'", val);
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
