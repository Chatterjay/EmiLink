package org.chatterjay.emiextend.client;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;

import java.util.ArrayList;
import java.util.List;

public final class BookmarkPageHelper {
    public static final int MIN_FAVORITE_PAGES = 5;

    private static int lastPageSize = 0;

    private BookmarkPageHelper() {
    }

    public static void rememberPageSize(int pageSize) {
        if (pageSize > 0) {
            lastPageSize = pageSize;
        }
    }

    public static int getLastPageSize() {
        return lastPageSize;
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

    public static int compactLocalInsertIndex(int page, int pageSize, int localIndex) {
        int items = countRealItemsInPage(page, pageSize);
        if (localIndex < 0) {
            return 0;
        }
        return Math.min(localIndex, items);
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
