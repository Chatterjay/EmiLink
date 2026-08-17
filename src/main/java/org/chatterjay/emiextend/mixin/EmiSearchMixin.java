package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.search.EmiSearch;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(value = EmiSearch.class, remap = false)
public class EmiSearchMixin {
    @Unique
    private static final Object EMILINK_SEARCH_LOCK = new Object();
    @Unique
    private static final AtomicLong EMILINK_SEARCH_SEQUENCE = new AtomicLong();

    @Inject(method = "search", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$serializeSearch(String query, CallbackInfo ci) {
        ci.cancel();

        String safeQuery = query == null ? "" : query;
        List<? extends EmiIngredient> source = dev.emi.emi.screen.EmiScreenManager.getSearchSource();
        if (source == null) {
            source = EmiStackList.stacks;
        }

        long token = EMILINK_SEARCH_SEQUENCE.incrementAndGet();
        List<? extends EmiIngredient> safeSource = source;
        Thread thread = new Thread(() -> emilink$runSerializedSearch(token, safeQuery, safeSource),
                "EmiLink EMI Search");
        thread.setDaemon(true);
        EmiSearch.searchThread = thread;
        thread.start();
    }

    @Unique
    private static void emilink$runSerializedSearch(long token, String query, List<? extends EmiIngredient> source) {
        synchronized (EMILINK_SEARCH_LOCK) {
            if (token != EMILINK_SEARCH_SEQUENCE.get()) {
                return;
            }

            try {
                EmiSearch.CompiledQuery compiled = new EmiSearch.CompiledQuery(query);
                if (token != EMILINK_SEARCH_SEQUENCE.get()) {
                    return;
                }
                EmiSearch.compiledQuery = compiled;
                if (compiled.isEmpty()) {
                    EmiSearch.stacks = source;
                    emilink$clearSearchThread(token);
                    return;
                }

                List<EmiIngredient> results = new ArrayList<>();
                for (EmiIngredient stack : source) {
                    if (token != EMILINK_SEARCH_SEQUENCE.get()) {
                        return;
                    }
                    List<EmiStack> emiStacks = stack.getEmiStacks();
                    if (emiStacks.size() == 1 && compiled.test(emiStacks.getFirst())) {
                        results.add(stack);
                    }
                }
                if (token == EMILINK_SEARCH_SEQUENCE.get()) {
                    EmiSearch.stacks = List.copyOf(results);
                    emilink$clearSearchThread(token);
                }
            } catch (Throwable t) {
                ModLogger.warn("EMI_SEARCH serialized search failed for '{}': {}: {}",
                        query, t.getClass().getName(), t.getMessage());
                if (token == EMILINK_SEARCH_SEQUENCE.get()) {
                    EmiSearch.stacks = List.copyOf(emilink$fallbackNameSearch(query, source));
                    emilink$clearSearchThread(token);
                }
            }
        }
    }

    @Unique
    private static void emilink$clearSearchThread(long token) {
        if (token == EMILINK_SEARCH_SEQUENCE.get()) {
            EmiSearch.searchThread = null;
        }
    }

    @Unique
    private static List<EmiIngredient> emilink$fallbackNameSearch(String query, List<? extends EmiIngredient> source) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return new ArrayList<>(source);
        }
        String[] terms = normalized.split("\\s+");
        List<EmiIngredient> results = new ArrayList<>();
        for (EmiIngredient ingredient : source) {
            List<EmiStack> stacks = ingredient.getEmiStacks();
            if (stacks.size() != 1) {
                continue;
            }
            EmiStack stack = stacks.getFirst();
            String haystack = (stack.getName().getString() + " " + stack.getId()).toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String term : terms) {
                if (!haystack.contains(term)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                results.add(ingredient);
            }
        }
        return results;
    }
}
