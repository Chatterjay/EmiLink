package org.chatterjay.emiextend.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import org.chatterjay.emiextend.client.BookmarkPageHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = EmiFavorites.class, remap = false)
public abstract class EmiFavoritesMixin {
    private static final String EMILINK_EMPTY_FAVORITE = "emilink_empty";

    @Shadow
    public static List<EmiFavorite> favorites;

    @Inject(method = "removeFavorite", at = @At("HEAD"), cancellable = true)
    private static void emilink$removeBookmarkAsGap(EmiIngredient stack, CallbackInfoReturnable<Boolean> cir) {
        for (int i = 0; i < favorites.size(); i++) {
            EmiFavorite favorite = favorites.get(i);
            if (favorite.strictEquals(stack) && favorite.getRecipe() == EmiApi.getRecipeContext(stack)) {
                favorites.set(i, BookmarkPageHelper.emptyPlaceholder());
                BookmarkPageHelper.compactAllPages(BookmarkPageHelper.getLastPageSize());
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "save", at = @At("RETURN"), cancellable = true)
    private static void emilink$saveBookmarkPageGaps(CallbackInfoReturnable<JsonArray> cir) {
        BookmarkPageHelper.compactAllPages(BookmarkPageHelper.getLastPageSize());
        JsonArray arr = new JsonArray();
        int last = favorites.size() - 1;
        while (last >= 0 && BookmarkPageHelper.isEmptyPlaceholder(favorites.get(last))) {
            last--;
        }

        for (int i = 0; i <= last; i++) {
            EmiFavorite fav = favorites.get(i);
            if (BookmarkPageHelper.isEmptyPlaceholder(fav)) {
                JsonObject obj = new JsonObject();
                obj.addProperty(EMILINK_EMPTY_FAVORITE, true);
                arr.add(obj);
                continue;
            }

            JsonElement stack = EmiIngredientSerializer.getSerialized(fav.getStack());
            if (stack != null) {
                JsonObject obj = new JsonObject();
                obj.add("stack", stack);
                if (fav.getRecipe() != null && fav.getRecipe().getId() != null) {
                    obj.addProperty("recipe", fav.getRecipe().getId().toString());
                }
                arr.add(obj);
            } else {
                JsonObject obj = new JsonObject();
                obj.addProperty(EMILINK_EMPTY_FAVORITE, true);
                arr.add(obj);
            }
        }
        cir.setReturnValue(arr);
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void emilink$loadBookmarkPageGaps(JsonArray arr, CallbackInfo ci) {
        boolean hasEmilinkGaps = false;
        for (JsonElement el : arr) {
            if (el.isJsonObject() && el.getAsJsonObject().has(EMILINK_EMPTY_FAVORITE)) {
                hasEmilinkGaps = true;
                break;
            }
        }
        if (!hasEmilinkGaps) {
            return;
        }

        List<EmiFavorite> loadedFavorites = new ArrayList<>(favorites);
        List<EmiFavorite> rebuilt = new ArrayList<>();
        int realIndex = 0;

        for (JsonElement el : arr) {
            if (el.isJsonObject() && el.getAsJsonObject().has(EMILINK_EMPTY_FAVORITE)) {
                rebuilt.add(BookmarkPageHelper.emptyPlaceholder());
            } else if (realIndex < loadedFavorites.size()) {
                rebuilt.add(loadedFavorites.get(realIndex++));
            }
        }
        while (realIndex < loadedFavorites.size()) {
            rebuilt.add(loadedFavorites.get(realIndex++));
        }

        favorites.clear();
        favorites.addAll(rebuilt);
    }
}
