package org.chatterjay.emiextend.client;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Identifies items from creative tabs with "mob" in their name, and reorders the
 * global EMI stack list so mob items are grouped at the end.
 */
public class MobSeparator {
    private static final Set<EmiStack> MOB_ITEMS = new ObjectOpenCustomHashSet<>(new EmiStackList.StrictHashStrategy());
    private static boolean initialized = false;

    /** Called after EmiStackList.reload() to capture mob creative tab items. */
    public static void init() {
        MOB_ITEMS.clear();
        var allTabs = CreativeModeTabs.allTabs();
        ModLogger.info("MobSeparator: scanning {} creative tabs", allTabs.size());
        for (CreativeModeTab tab : allTabs) {
            ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            String name = tab.getDisplayName().getString();
            if (key != null) {
                ModLogger.info("MobSeparator:   tab '{}' key '{}'", name, key);
                String path = key.getPath();
                if (path.contains("spawn_egg") || path.contains("mob") || path.contains("creature")) {
                    int count = 0;
                    for (ItemStack stack : tab.getSearchTabDisplayItems()) {
                        MOB_ITEMS.add(EmiStack.of(stack));
                        count++;
                    }
                    ModLogger.info("MobSeparator: captured {} items from tab '{}' (key: {})", count, name, key);
                }
            } else {
                ModLogger.info("MobSeparator:   tab '{}' (no registry key)", name);
            }
        }
        initialized = true;
    }

    public static boolean hasMobItems() {
        return initialized && !MOB_ITEMS.isEmpty();
    }

    public static boolean isMobStack(EmiStack stack) {
        return initialized && MOB_ITEMS.contains(stack);
    }

    public static boolean isMobIngredient(EmiIngredient ingredient) {
        if (!initialized) return false;
        for (var es : ingredient.getEmiStacks()) {
            if (MOB_ITEMS.contains(es)) return true;
        }
        return false;
    }

    /** Move mob items to the end of the given list. Returns a new mutable ArrayList. */
    public static <T extends EmiIngredient> List<T> reorderToEnd(List<T> input) {
        List<T> nonMob = new ArrayList<>();
        List<T> mob = new ArrayList<>();
        for (T item : input) {
            if (isMobIngredient(item)) mob.add(item);
            else nonMob.add(item);
        }
        if (mob.isEmpty()) return null; // no change needed
        nonMob.addAll(mob);
        return nonMob;
    }

    /**
     * Reorder and pad so mob items start on a new row.
     * Non-mob items are padded with EmiStack.EMPTY to fill the current row.
     * Only for use in ScreenSpace.getStacks() where pageWidth is known (tw).
     */
    @SuppressWarnings("unchecked")
    public static List<? extends EmiIngredient> separateStacks(List<? extends EmiIngredient> stacks, int pageWidth) {
        if (!initialized || pageWidth <= 0 || stacks.isEmpty() || MOB_ITEMS.isEmpty()) return stacks;

        List<EmiIngredient> nonMob = new ArrayList<>();
        List<EmiIngredient> mob = new ArrayList<>();

        for (var stack : stacks) {
            if (isMobIngredient(stack)) mob.add(stack);
            else nonMob.add(stack);
        }

        if (mob.isEmpty()) return stacks;

        // Pad non-mob items to fill the last row so mob items start fresh
        int remainder = nonMob.size() % pageWidth;
        if (remainder > 0) {
            for (int i = 0; i < pageWidth - remainder; i++) {
                nonMob.add(EmiStack.EMPTY);
            }
        }

        nonMob.addAll(mob);
        return nonMob;
    }
}
