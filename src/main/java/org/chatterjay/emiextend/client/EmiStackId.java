package org.chatterjay.emiextend.client;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiFavorite;

/**
 * Stable string identity for an EMI ingredient. Used by the sidebar selection
 * features, which persist plain ids instead of holding onto EMI objects.
 */
public final class EmiStackId {

    private EmiStackId() {}

    /**
     * Id of a single-stack ingredient, or null when the ingredient cannot be
     * identified (empty, or a tag/ingredient with several representations).
     */
    public static String of(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return null;
        if (ingredient instanceof EmiFavorite favorite) {
            ingredient = favorite.getStack();
        }
        var stacks = ingredient.getEmiStacks();
        if (stacks == null || stacks.size() != 1) return null;
        var id = stacks.get(0).getId();
        return id == null ? null : id.toString();
    }
}
