package org.chatterjay.emilink.client;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Exposes runtime-created LDLib recipe slots to EMI without making LDLib a hard dependency.
 * GTCEu's multiblock preview creates these slots after ModularEmiRecipe has registered its
 * normal EMI widgets, so EMI cannot otherwise resolve their hovered ingredient.
 */
public final class LDLibEmiHoverAdapter {
    private static final String MODULAR_RECIPE = "com.lowdragmc.lowdraglib.emi.ModularEmiRecipe";

    private static boolean initialized;
    private static boolean available;
    private static Field openedRecipesField;
    private static Method getWidgetMethod;
    private static Method getContainedWidgetsMethod;
    private static Method getIngredientOverMouseMethod;

    private LDLibEmiHoverAdapter() {}

    public static EmiStackInteraction getHoveredStack(int mouseX, int mouseY) {
        if (!initialize()) return null;

        try {
            Object opened = openedRecipesField.get(null);
            if (!(opened instanceof Collection<?> recipes) || recipes.isEmpty()) return null;

            // The last wrapper is the active top-most LDLib recipe page.
            List<?> snapshot = new ArrayList<>(recipes);
            for (int i = snapshot.size() - 1; i >= 0; i--) {
                Object wrapper = snapshot.get(i);
                if (wrapper == null) continue;

                Object root = getWidgetMethod.invoke(wrapper);
                EmiIngredient ingredient = findIngredient(root, mouseX, mouseY);
                if (ingredient != null && !ingredient.isEmpty()) {
                    return new EmiStackInteraction(ingredient);
                }
            }
        } catch (Throwable error) {
            ModLogger.debug("LDLib EMI hover lookup failed: {}", error.toString());
        }
        return null;
    }

    private static EmiIngredient findIngredient(Object root, int mouseX, int mouseY) throws ReflectiveOperationException {
        if (root == null) return null;
        Object widgets = getContainedWidgetsMethod.invoke(root, true);
        if (!(widgets instanceof Iterable<?> iterable)) return null;

        for (Object widget : iterable) {
            if (widget == null) continue;
            Method method = resolveIngredientMethod(widget.getClass());
            if (method == null) continue;

            EmiIngredient ingredient = toIngredient(method.invoke(widget, (double) mouseX, (double) mouseY));
            if (ingredient != null && !ingredient.isEmpty()) return ingredient;
        }
        return null;
    }

    private static Method resolveIngredientMethod(Class<?> type) {
        if (getIngredientOverMouseMethod != null
                && getIngredientOverMouseMethod.getDeclaringClass().isAssignableFrom(type)) {
            return getIngredientOverMouseMethod;
        }
        try {
            Method method = type.getMethod("getXEIIngredientOverMouse", double.class, double.class);
            method.setAccessible(true);
            getIngredientOverMouseMethod = method;
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static EmiIngredient toIngredient(Object value) {
        if (value instanceof EmiIngredient ingredient) return ingredient;
        if (value instanceof ItemStack stack && !stack.isEmpty()) return EmiStack.of(stack);
        if (value instanceof Optional<?> optional) return optional.map(LDLibEmiHoverAdapter::toIngredient).orElse(null);
        if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                EmiIngredient ingredient = toIngredient(entry);
                if (ingredient != null && !ingredient.isEmpty()) return ingredient;
            }
        }
        if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                EmiIngredient ingredient = toIngredient(Array.get(value, i));
                if (ingredient != null && !ingredient.isEmpty()) return ingredient;
            }
        }
        return null;
    }

    private static boolean initialize() {
        if (initialized) return available;
        initialized = true;
        try {
            Class<?> recipeClass = Class.forName(MODULAR_RECIPE);
            openedRecipesField = recipeClass.getField("CACHE_OPENED");
            Class<?> wrapperClass = Class.forName("com.lowdragmc.lowdraglib.jei.ModularWrapper");
            getWidgetMethod = wrapperClass.getMethod("getWidget");
            Class<?> widgetGroupClass = Class.forName("com.lowdragmc.lowdraglib.gui.widget.WidgetGroup");
            getContainedWidgetsMethod = widgetGroupClass.getMethod("getContainedWidgets", boolean.class);
            available = true;
            ModLogger.debug("LDLib EMI hover adapter initialized");
        } catch (Throwable error) {
            available = false;
            ModLogger.debug("LDLib EMI hover adapter unavailable: {}", error.toString());
        }
        return available;
    }
}
