package org.chatterjay.emiextend.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.EmiScreenManager;
import net.neoforged.fml.loading.FMLPaths;
import org.chatterjay.emiextend.util.ModLogger;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class BomTreePageHelper {
    public static final int MAX_TREES_PER_PAGE = 10;

    private static final Map<Integer, List<TreeEntry>> PAGE_TREES = new HashMap<>();
    private static final Map<Integer, Integer> ACTIVE_INDEXES = new HashMap<>();
    private static final Map<EmiFavorite.Synthetic, TreeEntry> SYNTHETIC_OWNERS = new IdentityHashMap<>();
    private static final Map<String, List<TreeEntry>> SYNTHETIC_OWNER_KEYS = new HashMap<>();
    private static final List<List<EmiFavorite.Synthetic>> ACTIVE_SYNTHETIC_GROUPS = new ArrayList<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static int activeFavoritePage = 0;
    private static boolean applying;
    private static boolean refreshingSynthetic;
    private static boolean buildingPageSynthetic;
    private static boolean loadedPersisted;

    private BomTreePageHelper() {
    }

    public static int getActiveFavoritePage() {
        return activeFavoritePage;
    }

    public static boolean hasActiveTree() {
        return BoM.tree != null && BoM.tree.goal != null;
    }

    public static boolean isBuildingPageSynthetic() {
        return buildingPageSynthetic;
    }

    public static List<List<EmiFavorite.Synthetic>> getActiveSyntheticGroups() {
        return ACTIVE_SYNTHETIC_GROUPS;
    }

    public static boolean isRecipeTreeUiOpen() {
        var screen = net.minecraft.client.Minecraft.getInstance().screen;
        return screen instanceof dev.emi.emi.screen.BoMScreen
                || screen instanceof dev.emi.emi.screen.RecipeScreen;
    }

    public static void activateFavoritePage(int page, int pageSize) {
        activateFavoritePage(page, pageSize, null);
    }

    public static void activateFavoritePage(int page, int pageSize, int[] rowWidths) {
        loadPersistedIfNeeded();
        if (page < 0) {
            page = 0;
        }
        BookmarkPageHelper.rememberPageSize(pageSize);
        page = realPageForDisplayPage(page, pageSize, rowWidths);
        if (pageSize > 0) {
            page = Math.min(page, Math.max(0, BookmarkPageHelper.realFavoritePageCount(pageSize) - 1));
        }
        if (page == activeFavoritePage && applying) {
            return;
        }
        if (page == activeFavoritePage) {
            if (!isRecipeTreeUiOpen()) {
                applyActiveToBoM();
            }
            return;
        }

        boolean preserveVisibleTree = isRecipeTreeUiOpen();
        if (!preserveVisibleTree) {
            storeActiveFromBoM();
        }
        activeFavoritePage = page;
        if (!preserveVisibleTree) {
            applyActiveToBoM();
            refreshActiveSynthetic();
        }
        ModLogger.info("BOM_TREE_PAGE activate page={} pageSize={} entries={} activeIndex={} preserveVisibleTree={} current={}",
                activeFavoritePage, pageSize, entriesFor(activeFavoritePage).size(),
                activeIndex(activeFavoritePage), preserveVisibleTree, describeCurrentBoM());
    }

    public static void storeActiveFromBoM() {
        if (applying) {
            return;
        }
        List<TreeEntry> entries = entriesFor(activeFavoritePage);
        int index = activeIndex(activeFavoritePage);
        if (BoM.tree == null || BoM.tree.goal == null) {
            if (index >= 0 && index < entries.size()) {
                entries.remove(index);
                if (entries.isEmpty()) {
                    PAGE_TREES.remove(activeFavoritePage);
                    ACTIVE_INDEXES.remove(activeFavoritePage);
                } else {
                    ACTIVE_INDEXES.put(activeFavoritePage, Math.min(index, entries.size() - 1));
                }
                savePersisted();
            }
        } else {
            if (index >= 0 && index < entries.size()) {
                TreeEntry old = entries.get(index);
                if (!old.sameState(BoM.tree, BoM.craftingMode)) {
                    entries.set(index, new TreeEntry(BoM.tree, BoM.craftingMode));
                    savePersisted();
                }
            } else {
                addTreeToPage(activeFavoritePage, BoM.tree, BoM.craftingMode);
            }
        }
    }

    public static void applyActiveToBoM() {
        applying = true;
        try {
            TreeEntry entry = activeEntry(activeFavoritePage);
            BoM.tree = entry == null ? null : entry.tree;
            BoM.craftingMode = entry != null && entry.craftingMode;
        } finally {
            applying = false;
        }
    }

    public static boolean activateSynthetic(EmiFavorite.Synthetic synthetic) {
        if (synthetic == null) {
            return false;
        }
        TreeEntry owner = SYNTHETIC_OWNERS.get(synthetic);
        if (owner == null) {
            owner = lookupSyntheticOwnerByStableKey(synthetic);
        }
        if (owner == null) {
            return false;
        }
        List<TreeEntry> entries = entriesFor(activeFavoritePage);
        int index = entries.indexOf(owner);
        if (index < 0) {
            int ownerPage = pageOf(owner);
            if (ownerPage < 0) {
                return false;
            }
            activeFavoritePage = ownerPage;
            entries = entriesFor(activeFavoritePage);
            index = entries.indexOf(owner);
            if (index < 0) {
                return false;
            }
        }
        ACTIVE_INDEXES.put(activeFavoritePage, index);
        applyActiveToBoM();
        return true;
    }

    public static String describeSyntheticActivationFailure(EmiFavorite.Synthetic synthetic) {
        if (synthetic == null) {
            return "synthetic=null activePage=" + activeFavoritePage;
        }
        String key = syntheticKey(synthetic);
        TreeEntry identityOwner = SYNTHETIC_OWNERS.get(synthetic);
        TreeEntry keyOwner = lookupSyntheticOwnerByStableKey(synthetic);
        return "activePage=" + activeFavoritePage
                + " activeEntries=" + entriesFor(activeFavoritePage).size()
                + " syntheticGroups=" + ACTIVE_SYNTHETIC_GROUPS.size()
                + " identityOwner=" + (identityOwner != null)
                + " key=" + key
                + " keyOwner=" + (keyOwner != null)
                + " keyOwnerPage=" + pageOf(keyOwner)
                + " recipe=" + (synthetic.getRecipe() == null || synthetic.getRecipe().getId() == null ? "null" : synthetic.getRecipe().getId())
                + " batches=" + synthetic.batches
                + " amount=" + synthetic.amount
                + " total=" + synthetic.total
                + " state=" + synthetic.state;
    }

    public static void storeNewActiveTree() {
        if (BoM.tree == null || BoM.tree.goal == null) {
            storeActiveFromBoM();
        } else {
            addTreeToPage(activeFavoritePage, BoM.tree, BoM.craftingMode);
        }
        ModLogger.info("BOM_TREE_PAGE storeNew page={} entries={} activeIndex={} current={}",
                activeFavoritePage, entriesFor(activeFavoritePage).size(),
                activeIndex(activeFavoritePage), describeCurrentBoM());
        refreshActiveSynthetic();
    }

    public static void syncActiveModeFromBoM() {
        storeActiveFromBoM();
    }

    public static void logCurrentState(String reason) {
        ModLogger.info("BOM_TREE_PAGE state reason={} page={} entries={} activeIndex={} groups={} synthetic={} current={}",
                reason,
                activeFavoritePage,
                entriesFor(activeFavoritePage).size(),
                activeIndex(activeFavoritePage),
                ACTIVE_SYNTHETIC_GROUPS.size(),
                EmiFavorites.syntheticFavorites.size(),
                describeCurrentBoM());
    }

    public static void refreshActiveSynthetic() {
        if (refreshingSynthetic) {
            return;
        }
        EmiPlayerInventory inv = EmiScreenManager.lastPlayerInventory;
        if (inv == null) {
            EmiFavorites.syntheticFavorites.clear();
            ACTIVE_SYNTHETIC_GROUPS.clear();
            SYNTHETIC_OWNERS.clear();
            SYNTHETIC_OWNER_KEYS.clear();
            return;
        }
        refreshingSynthetic = true;
        try {
            rebuildPageSynthetic(inv);
        } finally {
            refreshingSynthetic = false;
        }
    }

    private static List<TreeEntry> entriesFor(int page) {
        return PAGE_TREES.computeIfAbsent(page, ignored -> new ArrayList<>());
    }

    private static int activeIndex(int page) {
        List<TreeEntry> entries = entriesFor(page);
        if (entries.isEmpty()) {
            return -1;
        }
        int index = ACTIVE_INDEXES.getOrDefault(page, entries.size() - 1);
        if (index < 0 || index >= entries.size()) {
            index = entries.size() - 1;
            ACTIVE_INDEXES.put(page, index);
        }
        return index;
    }

    private static TreeEntry activeEntry(int page) {
        List<TreeEntry> entries = entriesFor(page);
        int index = activeIndex(page);
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    private static String describeCurrentBoM() {
        if (BoM.tree == null || BoM.tree.goal == null || BoM.tree.goal.recipe == null) {
            return "tree=null craftingMode=" + BoM.craftingMode;
        }
        return "recipe=" + BoM.tree.goal.recipe.getId()
                + " batches=" + BoM.tree.batches
                + " craftingMode=" + BoM.craftingMode;
    }

    private static int pageOf(TreeEntry owner) {
        if (owner == null) {
            return -1;
        }
        for (var pageEntry : PAGE_TREES.entrySet()) {
            List<TreeEntry> entries = pageEntry.getValue();
            if (entries != null && entries.contains(owner)) {
                return pageEntry.getKey();
            }
        }
        return -1;
    }

    private static void addTreeToPage(int page, MaterialTree tree, boolean craftingMode) {
        String recipeId = treeRecipeId(tree);
        if (recipeId != null) {
            removeRecipeFromAllPages(recipeId);
        }

        List<TreeEntry> entries = entriesFor(page);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).tree == tree) {
                entries.set(i, new TreeEntry(tree, craftingMode));
                ACTIVE_INDEXES.put(page, i);
                savePersisted();
                return;
            }
        }
        entries.add(new TreeEntry(tree, craftingMode));
        while (entries.size() > MAX_TREES_PER_PAGE) {
            entries.remove(0);
        }
        ACTIVE_INDEXES.put(page, entries.size() - 1);
        savePersisted();
    }

    private static void removeRecipeFromAllPages(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        var pageIterator = PAGE_TREES.entrySet().iterator();
        while (pageIterator.hasNext()) {
            var pageEntry = pageIterator.next();
            int page = pageEntry.getKey();
            List<TreeEntry> entries = pageEntry.getValue();
            if (entries == null || entries.isEmpty()) {
                pageIterator.remove();
                ACTIVE_INDEXES.remove(page);
                continue;
            }
            int active = activeIndex(page);
            boolean removed = false;
            for (int i = entries.size() - 1; i >= 0; i--) {
                TreeEntry entry = entries.get(i);
                if (recipeId.equals(treeRecipeId(entry == null ? null : entry.tree))) {
                    entries.remove(i);
                    removed = true;
                    if (i < active) {
                        active--;
                    } else if (i == active) {
                        active = Math.min(i, entries.size() - 1);
                    }
                }
            }
            if (entries.isEmpty()) {
                pageIterator.remove();
                ACTIVE_INDEXES.remove(page);
            } else if (removed) {
                ACTIVE_INDEXES.put(page, Math.max(0, Math.min(active, entries.size() - 1)));
            }
        }
    }

    private static String treeRecipeId(MaterialTree tree) {
        if (tree == null || tree.goal == null || tree.goal.recipe == null || tree.goal.recipe.getId() == null) {
            return null;
        }
        return tree.goal.recipe.getId().toString();
    }

    public static int realPageForDisplayPage(int displayPage, int pageSize, int[] rowWidths) {
        if (displayPage <= 0 || pageSize <= 0) {
            return 0;
        }
        int realActive = Math.max(0, activeFavoritePage);
        int extra = activeOverflowExtraPages(pageSize, rowWidths);
        if (extra <= 0) {
            return displayPage;
        }
        if (displayPage < realActive) {
            return displayPage;
        }
        if (displayPage <= realActive + extra) {
            return realActive;
        }
        return displayPage - extra;
    }

    public static int virtualFavoriteSize(int pageSize, int[] rowWidths) {
        if (pageSize <= 0) {
            return 0;
        }
        int pages = BookmarkPageHelper.realFavoritePageCount(pageSize) + activeOverflowExtraPages(pageSize, rowWidths);
        return Math.max(BookmarkPageHelper.MIN_FAVORITE_PAGES, pages) * pageSize;
    }

    public static List<EmiIngredient> buildVirtualFavoriteStacks(int pageSize, int[] rowWidths) {
        loadPersistedIfNeeded();
        List<EmiIngredient> result = new ArrayList<>();
        if (pageSize <= 0) {
            result.addAll(EmiFavorites.favorites);
            return result;
        }

        int realPages = BookmarkPageHelper.realFavoritePageCount(pageSize);
        int activePage = Math.max(0, activeFavoritePage);
        int activePages = activePageViewPageCount(pageSize, rowWidths);
        for (int realPage = 0; realPage < realPages; realPage++) {
            if (realPage == activePage) {
                List<EmiIngredient> pageView = buildActivePageView(pageSize, rowWidths);
                int needed = Math.max(pageSize, activePages * pageSize);
                for (int i = 0; i < needed; i++) {
                    result.add(i < pageView.size() ? pageView.get(i) : EmiStack.EMPTY);
                }
            } else {
                List<EmiFavorite> real = BookmarkPageHelper.realPageContents(realPage, pageSize);
                for (int i = 0; i < pageSize; i++) {
                    result.add(i < real.size() ? real.get(i) : EmiStack.EMPTY);
                }
            }
        }

        int minSize = virtualFavoriteSize(pageSize, rowWidths);
        while (result.size() < minSize) {
            result.add(EmiStack.EMPTY);
        }
        return result;
    }

    private static int activeOverflowExtraPages(int pageSize, int[] rowWidths) {
        return Math.max(0, activePageViewPageCount(pageSize, rowWidths) - 1);
    }

    private static int activePageViewPageCount(int pageSize, int[] rowWidths) {
        if (pageSize <= 0) {
            return 1;
        }
        int size = buildActivePageView(pageSize, rowWidths).size();
        return Math.max(1, (size + pageSize - 1) / pageSize);
    }

    private static List<EmiIngredient> buildActivePageView(int pageSize, int[] rowWidths) {
        List<EmiIngredient> pageView = new ArrayList<>(pageSize + EmiFavorites.syntheticFavorites.size());
        for (EmiFavorite favorite : BookmarkPageHelper.realPageContents(activeFavoritePage, pageSize)) {
            pageView.add(favorite);
        }

        int cursor = pageView.size();
        var groups = getActiveSyntheticGroups();
        if (groups == null || groups.isEmpty()) {
            appendPaddingToNextRow(pageView, cursor, pageSize, rowWidths);
            pageView.addAll(EmiFavorites.syntheticFavorites);
            return pageView;
        }

        for (var group : groups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            cursor = pageView.size();
            appendPaddingToNextRow(pageView, cursor, pageSize, rowWidths);
            pageView.addAll(group);
        }
        return pageView;
    }

    private static void appendPaddingToNextRow(List<EmiIngredient> list, int cursor, int pageSize, int[] rowWidths) {
        int padding = paddingToNextRow(cursor, pageSize, rowWidths);
        for (int i = 0; i < padding; i++) {
            list.add(EmiStack.EMPTY);
        }
    }

    private static int paddingToNextRow(int cursor, int pageSize, int[] rowWidths) {
        if (cursor <= 0 || pageSize <= 0 || rowWidths == null || rowWidths.length == 0) {
            return 0;
        }
        int local = cursor % pageSize;
        if (local == 0) {
            return 0;
        }
        int rowStart = 0;
        for (int width : rowWidths) {
            if (width <= 0) {
                continue;
            }
            int rowEnd = rowStart + width;
            if (local == rowStart) {
                return 0;
            }
            if (local < rowEnd) {
                return rowEnd - local;
            }
            rowStart = rowEnd;
        }
        return Math.max(0, pageSize - local);
    }

    private static void rebuildPageSynthetic(EmiPlayerInventory inv) {
        List<TreeEntry> entries = entriesFor(activeFavoritePage);
        EmiFavorites.syntheticFavorites.clear();
        SYNTHETIC_OWNERS.clear();
        SYNTHETIC_OWNER_KEYS.clear();
        ACTIVE_SYNTHETIC_GROUPS.clear();
        if (entries.isEmpty()) {
            return;
        }

        TreeEntry activeBefore = activeEntry(activeFavoritePage);
        List<EmiFavorite.Synthetic> combined = new ArrayList<>();
        buildingPageSynthetic = true;
        applying = true;
        try {
            for (TreeEntry entry : entries) {
                if (entry.tree == null || entry.tree.goal == null || !entry.craftingMode) {
                    continue;
                }
                BoM.tree = entry.tree;
                BoM.craftingMode = true;
                EmiFavorites.updateSynthetic(inv);
                if (!EmiFavorites.syntheticFavorites.isEmpty()) {
                    List<EmiFavorite.Synthetic> group = new ArrayList<>(EmiFavorites.syntheticFavorites);
                    for (EmiFavorite.Synthetic synthetic : group) {
                        SYNTHETIC_OWNERS.put(synthetic, entry);
                        putSyntheticOwnerKey(synthetic, entry);
                    }
                    ACTIVE_SYNTHETIC_GROUPS.add(group);
                    combined.addAll(group);
                }
            }
            EmiFavorites.syntheticFavorites.clear();
            EmiFavorites.syntheticFavorites.addAll(combined);
        } finally {
            buildingPageSynthetic = false;
            if (activeBefore == null) {
                BoM.tree = null;
                BoM.craftingMode = false;
            } else {
                BoM.tree = activeBefore.tree;
                BoM.craftingMode = activeBefore.craftingMode;
            }
            applying = false;
        }
    }

    private static void putSyntheticOwnerKey(EmiFavorite.Synthetic synthetic, TreeEntry entry) {
        String key = syntheticKey(synthetic);
        if (key == null) {
            return;
        }
        SYNTHETIC_OWNER_KEYS.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
    }

    private static TreeEntry lookupSyntheticOwnerByStableKey(EmiFavorite.Synthetic synthetic) {
        String key = syntheticKey(synthetic);
        if (key == null) {
            return null;
        }
        List<TreeEntry> owners = SYNTHETIC_OWNER_KEYS.get(key);
        if (owners == null || owners.isEmpty()) {
            return null;
        }
        List<TreeEntry> activeEntries = entriesFor(activeFavoritePage);
        for (TreeEntry owner : owners) {
            if (activeEntries.contains(owner)) {
                return owner;
            }
        }
        return owners.get(0);
    }

    private static String syntheticKey(EmiFavorite.Synthetic synthetic) {
        if (synthetic == null) {
            return null;
        }
        if (synthetic.getRecipe() != null && synthetic.getRecipe().getId() != null) {
            return "recipe:" + synthetic.getRecipe().getId();
        }
        var stacks = synthetic.getEmiStacks();
        if (stacks == null || stacks.isEmpty()) {
            return null;
        }
        var first = stacks.getFirst();
        if (first == null || first.getId() == null) {
            return null;
        }
        return "ingredient:" + first.getId();
    }

    private static void loadPersistedIfNeeded() {
        if (loadedPersisted) {
            return;
        }
        loadedPersisted = true;
        Path file = persistFile();
        if (!Files.exists(file)) {
            return;
        }
        try (FileReader reader = new FileReader(file.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("pages") || !root.get("pages").isJsonArray()) {
                return;
            }
            PAGE_TREES.clear();
            ACTIVE_INDEXES.clear();
            JsonArray pages = root.getAsJsonArray("pages");
            for (JsonElement pageElement : pages) {
                if (!pageElement.isJsonObject()) {
                    continue;
                }
                JsonObject pageObject = pageElement.getAsJsonObject();
                if (!pageObject.has("page") || !pageObject.has("trees") || !pageObject.get("trees").isJsonArray()) {
                    continue;
                }
                int page = pageObject.get("page").getAsInt();
                List<TreeEntry> entries = entriesFor(page);
                for (JsonElement treeElement : pageObject.getAsJsonArray("trees")) {
                    if (entries.size() >= MAX_TREES_PER_PAGE || !treeElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject treeObject = treeElement.getAsJsonObject();
                    if (!treeObject.has("recipe")) {
                        continue;
                    }
                    var recipe = EmiApi.getRecipeManager().getRecipe(EmiPort.id(treeObject.get("recipe").getAsString()));
                    if (recipe == null || recipe.getOutputs().isEmpty()) {
                        continue;
                    }
                    MaterialTree tree = new MaterialTree(recipe);
                    if (treeObject.has("batches")) {
                        tree.batches = Math.max(1, treeObject.get("batches").getAsLong());
                    }
                    boolean craftingMode = !treeObject.has("craftingMode") || treeObject.get("craftingMode").getAsBoolean();
                    entries.add(new TreeEntry(tree, craftingMode));
                }
                if (entries.isEmpty()) {
                    PAGE_TREES.remove(page);
                    continue;
                }
                int active = pageObject.has("active") ? pageObject.get("active").getAsInt() : entries.size() - 1;
                ACTIVE_INDEXES.put(page, Math.max(0, Math.min(active, entries.size() - 1)));
            }
            ModLogger.info("BOM_TREE_STATE loaded pages={} file={}", PAGE_TREES.size(), file);
        } catch (Exception e) {
            ModLogger.warn("BOM_TREE_STATE load failed file={} error={}", file, e.toString());
        }
    }

    private static void savePersisted() {
        if (!loadedPersisted) {
            return;
        }
        try {
            Files.createDirectories(persistFile().getParent());
            JsonObject root = new JsonObject();
            JsonArray pages = new JsonArray();
            for (var pageEntry : PAGE_TREES.entrySet()) {
                List<TreeEntry> entries = pageEntry.getValue();
                if (entries == null || entries.isEmpty()) {
                    continue;
                }
                JsonObject pageObject = new JsonObject();
                pageObject.addProperty("page", pageEntry.getKey());
                pageObject.addProperty("active", activeIndex(pageEntry.getKey()));
                JsonArray trees = new JsonArray();
                for (TreeEntry entry : entries) {
                    if (entry == null || entry.tree == null || entry.tree.goal == null
                            || entry.tree.goal.recipe == null || entry.tree.goal.recipe.getId() == null) {
                        continue;
                    }
                    JsonObject treeObject = new JsonObject();
                    treeObject.addProperty("recipe", entry.tree.goal.recipe.getId().toString());
                    treeObject.addProperty("batches", entry.tree.batches);
                    treeObject.addProperty("craftingMode", entry.craftingMode);
                    trees.add(treeObject);
                }
                if (!trees.isEmpty()) {
                    pageObject.add("trees", trees);
                    pages.add(pageObject);
                }
            }
            root.add("pages", pages);
            try (FileWriter writer = new FileWriter(persistFile().toFile())) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            ModLogger.warn("BOM_TREE_STATE save failed file={} error={}", persistFile(), e.toString());
        }
    }

    private static Path persistFile() {
        return FMLPaths.GAMEDIR.get().resolve("emilink").resolve("bom_trees.json");
    }

    private static final class TreeEntry {
        private final MaterialTree tree;
        private boolean craftingMode;
        private final long persistedBatches;

        private TreeEntry(MaterialTree tree, boolean craftingMode) {
            this.tree = tree;
            this.craftingMode = craftingMode;
            this.persistedBatches = tree == null ? 0 : tree.batches;
        }

        private boolean sameState(MaterialTree otherTree, boolean otherCraftingMode) {
            if (tree != otherTree || craftingMode != otherCraftingMode) {
                return false;
            }
            return tree == null || otherTree == null || persistedBatches == otherTree.batches;
        }
    }
}
