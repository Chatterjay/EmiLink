package org.chatterjay.emiextend.client.bookmark;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiPersistentData;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.List;

public final class BookmarkPageHelper {
    public static final int DEFAULT_FAVORITE_PAGES = 5;

    private static int lastPageSize = 0;

    private BookmarkPageHelper() {
    }

    public static void rememberPageSize(int pageSize) {
        if (pageSize > 0) {
            recoverSparsePageExplosion(pageSize);
            if (lastPageSize > 0 && lastPageSize != pageSize) {
                reflowPages(lastPageSize, pageSize);
            }
            lastPageSize = pageSize;
        }
    }

    public static int getLastPageSize() {
        return lastPageSize;
    }

    public static int configuredFavoritePageCount() {
        try {
            return Math.max(1, Math.min(50, EmiLinkConfig.FAVORITE_PAGE_COUNT.get()));
        } catch (Exception ignored) {
            return DEFAULT_FAVORITE_PAGES;
        }
    }

    public static boolean isEmptyPlaceholder(EmiFavorite favorite) {
        return favorite != null && favorite.getRecipe() == null && favorite.getStack().isEmpty();
    }

    public static EmiFavorite emptyPlaceholder() {
        return new EmiFavorite(EmiStack.EMPTY, null);
    }

    public static void ensureSize(int size) {
        while (EmiFavorites.favorites.size() < size) {
            EmiFavorites.favorites.add(emptyPlaceholder());
        }
    }

    public static int countRealItemsInPage(int page, int pageSize) {
        if (pageSize <= 0) {
            return 0;
        }
        int start = page * pageSize;
        int end = Math.min(start + pageSize, EmiFavorites.favorites.size());
        int count = 0;
        for (int i = start; i < end; i++) {
            if (!isEmptyPlaceholder(EmiFavorites.favorites.get(i))) {
                count++;
            }
        }
        return count;
    }

    public static int effectiveFavoriteSize() {
        int last = EmiFavorites.favorites.size() - 1;
        while (last >= 0 && isEmptyPlaceholder(EmiFavorites.favorites.get(last))) {
            last--;
        }
        return last + 1;
    }

    public static int realFavoritePageCount(int pageSize) {
        if (pageSize <= 0) {
            return configuredFavoritePageCount();
        }
        recoverSparsePageExplosion(pageSize);
        int effectiveSize = effectiveFavoriteSize();
        int pages = effectiveSize <= 0 ? 1 : (effectiveSize + pageSize - 1) / pageSize;
        return Math.max(configuredFavoritePageCount(), pages);
    }

    public static List<EmiFavorite> realPageContents(int page, int pageSize) {
        List<EmiFavorite> real = new ArrayList<>();
        if (page < 0 || pageSize <= 0) {
            return real;
        }
        int start = page * pageSize;
        int end = Math.min(start + pageSize, EmiFavorites.favorites.size());
        for (int i = start; i < end; i++) {
            EmiFavorite favorite = EmiFavorites.favorites.get(i);
            if (!isEmptyPlaceholder(favorite)) {
                real.add(favorite);
            }
        }
        return real;
    }

    public static boolean recoverSparsePageExplosion(int pageSize) {
        if (EmiFavorites.favorites.isEmpty()) {
            return false;
        }

        int total = EmiFavorites.favorites.size();
        List<EmiFavorite> real = new ArrayList<>();
        int lastRealIndex = -1;
        for (int i = 0; i < EmiFavorites.favorites.size(); i++) {
            EmiFavorite favorite = EmiFavorites.favorites.get(i);
            if (!isEmptyPlaceholder(favorite)) {
                real.add(favorite);
                lastRealIndex = i;
            }
        }
        int realCount = real.size();
        int emptyCount = total - realCount;
        int configuredPages = configuredFavoritePageCount();

        if (pageSize > 0 && realCount > 0) {
            int pages = (total + pageSize - 1) / pageSize;
            int lastRealPage = lastRealIndex < 0 ? 0 : (lastRealIndex / pageSize) + 1;
            boolean pollutedSparsePages = pages > configuredPages
                    && lastRealPage > configuredPages
                    && realCount <= configuredPages * pageSize
                    && emptyCount > realCount * 4;
            if (pollutedSparsePages) {
                EmiFavorites.favorites.clear();
                EmiFavorites.favorites.addAll(real);
                ModLogger.debug("BOOKMARK_PAGE recovered sparse placeholder pollution total={} real={} empty={} pageSize={} pages={} configuredPages={} lastRealPage={}",
                        total, realCount, emptyCount, pageSize, pages, configuredPages, lastRealPage);
                return true;
            }
        }

        if (pageSize <= 0 && total > 1024 && realCount > 0 && emptyCount > realCount * 8) {
            EmiFavorites.favorites.clear();
            EmiFavorites.favorites.addAll(real);
            ModLogger.debug("BOOKMARK_PAGE recovered sparse placeholder pollution before page size total={} real={} empty={}",
                    total, realCount, emptyCount);
            return true;
        }

        /*
         * Legacy safety net for very large sparse explosions. This is kept for
         * older polluted files where page size might not be known yet.
         */
        int pages = pageSize > 0 ? (total + pageSize - 1) / pageSize : total;
        boolean sparseExplosion = total > 4096
                && realCount > 0
                && emptyCount > realCount * 8
                && (pageSize <= 0 || pages > Math.max(configuredPages * 4, realCount + configuredPages));
        if (!sparseExplosion) {
            return false;
        }

        EmiFavorites.favorites.clear();
        EmiFavorites.favorites.addAll(real);
        ModLogger.debug("BOOKMARK_PAGE recovered sparse placeholder explosion total={} real={} empty={} pageSize={}",
                total, realCount, emptyCount, pageSize);
        return true;
    }

    public static int compactLocalInsertIndex(int page, int pageSize, int localIndex) {
        return countRealItemsInPage(page, pageSize);
    }

    public static boolean addOrMoveFavoriteToPage(EmiIngredient stack, EmiRecipe context, int page, int pageSize) {
        if (pageSize <= 0 || stack == null) {
            return false;
        }
        if (stack instanceof EmiFavorite.Synthetic) {
            return true;
        }
        if (page < 0) {
            page = 0;
        }

        rememberPageSize(pageSize);
        compactAllPages(pageSize);

        EmiFavorite favorite = createFavorite(stack, context);
        if (favorite == null || favorite.getStack().isEmpty()) {
            return true;
        }

        removeMatchingAsPlaceholders(favorite);
        compactAllPages(pageSize);

        int pageStart = page * pageSize;
        ensureSize(pageStart + pageSize);
        int localIndex = countRealItemsInPage(page, pageSize);
        int offset = pageStart + Math.min(localIndex, pageSize - 1);
        if (localIndex >= pageSize) {
            EmiFavorites.favorites.add(pageStart + pageSize, favorite);
        } else {
            EmiFavorites.favorites.set(offset, favorite);
        }

        compactAllPages(pageSize);
        EmiPersistentData.save();
        return true;
    }

    public static boolean removeFavoriteAsGap(EmiFavorite target, int pageSize) {
        if (target == null) {
            return false;
        }
        boolean removed = false;
        for (int i = 0; i < EmiFavorites.favorites.size(); i++) {
            EmiFavorite favorite = EmiFavorites.favorites.get(i);
            if (isEmptyPlaceholder(favorite)) {
                continue;
            }
            if (favorite.getRecipe() == target.getRecipe() && favorite.strictEquals(target)) {
                EmiFavorites.favorites.set(i, emptyPlaceholder());
                removed = true;
            }
        }
        if (removed) {
            compactAllPages(pageSize);
            EmiPersistentData.save();
        }
        return removed;
    }

    private static EmiFavorite createFavorite(EmiIngredient stack, EmiRecipe context) {
        if (stack instanceof EmiFavorite.Craftable craftable) {
            stack = craftable.getStack();
            if (context == null) {
                context = craftable.getRecipe();
            }
        }
        if (stack instanceof EmiFavorite favorite) {
            return favorite;
        }

        var serialized = EmiIngredientSerializer.getSerialized(stack);
        if (serialized == null) {
            return null;
        }
        stack = EmiIngredientSerializer.getDeserialized(serialized);
        if (stack.isEmpty()) {
            return null;
        }

        if (stack instanceof EmiStack es && context != null && context.getId() != null) {
            es = es.copy();
            if (es instanceof ItemEmiStack ies) {
                ies.getItemStack().setCount(1);
            }
            if (!es.isEmpty()) {
                return new EmiFavorite(es, context);
            }
            return null;
        }
        return new EmiFavorite(stack, null);
    }

    private static void removeMatchingAsPlaceholders(EmiFavorite target) {
        for (int i = 0; i < EmiFavorites.favorites.size(); i++) {
            EmiFavorite favorite = EmiFavorites.favorites.get(i);
            if (isEmptyPlaceholder(favorite)) {
                continue;
            }
            if (favorite.getRecipe() == target.getRecipe() && favorite.strictEquals(target.getStack())) {
                EmiFavorites.favorites.set(i, emptyPlaceholder());
            }
        }
    }

    public static void compactAllPages(int pageSize) {
        if (pageSize <= 0 || EmiFavorites.favorites.isEmpty()) {
            return;
        }
        rememberPageSize(pageSize);

        int pages = Math.max(1, (EmiFavorites.favorites.size() + pageSize - 1) / pageSize);
        ensureSize(pages * pageSize);

        for (int page = 0; page < pages; page++) {
            int start = page * pageSize;
            int end = start + pageSize;
            List<EmiFavorite> real = new ArrayList<>(pageSize);
            for (int i = start; i < end; i++) {
                EmiFavorite favorite = EmiFavorites.favorites.get(i);
                if (!isEmptyPlaceholder(favorite)) {
                    real.add(favorite);
                }
            }
            for (int i = 0; i < pageSize; i++) {
                EmiFavorites.favorites.set(start + i, i < real.size() ? real.get(i) : emptyPlaceholder());
            }
        }

        trimTrailingPlaceholders();
    }

    private static void reflowPages(int oldPageSize, int newPageSize) {
        if (oldPageSize <= 0 || newPageSize <= 0 || oldPageSize == newPageSize || EmiFavorites.favorites.isEmpty()) {
            return;
        }

        int oldPages = Math.max(1, (EmiFavorites.favorites.size() + oldPageSize - 1) / oldPageSize);
        List<List<EmiFavorite>> pages = new ArrayList<>(oldPages);
        for (int page = 0; page < oldPages; page++) {
            int start = page * oldPageSize;
            int end = Math.min(start + oldPageSize, EmiFavorites.favorites.size());
            List<EmiFavorite> real = new ArrayList<>();
            for (int i = start; i < end; i++) {
                EmiFavorite favorite = EmiFavorites.favorites.get(i);
                if (!isEmptyPlaceholder(favorite)) {
                    real.add(favorite);
                }
            }
            pages.add(real);
        }

        EmiFavorites.favorites.clear();
        ensureSize(oldPages * newPageSize);
        for (int page = 0; page < pages.size(); page++) {
            List<EmiFavorite> real = pages.get(page);
            int start = page * newPageSize;
            ensureSize(start + Math.max(newPageSize, real.size()));
            for (int i = 0; i < real.size(); i++) {
                int index = start + i;
                if (index < EmiFavorites.favorites.size()) {
                    EmiFavorites.favorites.set(index, real.get(i));
                } else {
                    EmiFavorites.favorites.add(real.get(i));
                }
            }
        }
    }

    public static void trimTrailingPlaceholders() {
        int last = EmiFavorites.favorites.size() - 1;
        while (last >= 0 && isEmptyPlaceholder(EmiFavorites.favorites.get(last))) {
            last--;
        }
        if (last < EmiFavorites.favorites.size() - 1) {
            EmiFavorites.favorites.subList(last + 1, EmiFavorites.favorites.size()).clear();
        }
    }
}
