package org.chatterjay.emiextend.client;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.EmiScreenManager;

import java.util.HashMap;
import java.util.Map;

public final class BomTreePageHelper {
    private static final Map<Integer, MaterialTree> TREES = new HashMap<>();
    private static final Map<Integer, Boolean> CRAFTING_MODES = new HashMap<>();
    private static int activeFavoritePage = 0;
    private static boolean applying;
    private static boolean refreshingSynthetic;

    private BomTreePageHelper() {
    }

    public static int getActiveFavoritePage() {
        return activeFavoritePage;
    }

    public static boolean hasActiveTree() {
        return BoM.tree != null && BoM.tree.goal != null;
    }

    public static void activateFavoritePage(int page, int pageSize) {
        if (page < 0) {
            page = 0;
        }
        BookmarkPageHelper.rememberPageSize(pageSize);
        if (page == activeFavoritePage && applying) {
            return;
        }
        if (page == activeFavoritePage) {
            applyActiveToBoM();
            return;
        }

        storeActiveFromBoM();
        activeFavoritePage = page;
        applyActiveToBoM();
        refreshActiveSynthetic();
    }

    public static void storeActiveFromBoM() {
        if (applying) {
            return;
        }
        if (BoM.tree == null || BoM.tree.goal == null) {
            TREES.remove(activeFavoritePage);
            CRAFTING_MODES.remove(activeFavoritePage);
        } else {
            TREES.put(activeFavoritePage, BoM.tree);
            CRAFTING_MODES.put(activeFavoritePage, BoM.craftingMode);
        }
    }

    public static void applyActiveToBoM() {
        applying = true;
        try {
            MaterialTree tree = TREES.get(activeFavoritePage);
            BoM.tree = tree;
            BoM.craftingMode = tree != null && CRAFTING_MODES.getOrDefault(activeFavoritePage, false);
        } finally {
            applying = false;
        }
    }

    public static void storeNewActiveTree() {
        if (BoM.tree == null || BoM.tree.goal == null) {
            TREES.remove(activeFavoritePage);
            CRAFTING_MODES.remove(activeFavoritePage);
        } else {
            TREES.put(activeFavoritePage, BoM.tree);
            CRAFTING_MODES.put(activeFavoritePage, BoM.craftingMode);
        }
        refreshActiveSynthetic();
    }

    public static void syncActiveModeFromBoM() {
        if (BoM.tree == null || BoM.tree.goal == null) {
            TREES.remove(activeFavoritePage);
            CRAFTING_MODES.remove(activeFavoritePage);
        } else {
            TREES.put(activeFavoritePage, BoM.tree);
            CRAFTING_MODES.put(activeFavoritePage, BoM.craftingMode);
        }
    }

    public static void refreshActiveSynthetic() {
        if (refreshingSynthetic) {
            return;
        }
        EmiPlayerInventory inv = EmiScreenManager.lastPlayerInventory;
        if (inv == null) {
            EmiFavorites.syntheticFavorites.clear();
            return;
        }
        refreshingSynthetic = true;
        try {
            EmiFavorites.updateSynthetic(inv);
        } finally {
            refreshingSynthetic = false;
        }
    }
}
