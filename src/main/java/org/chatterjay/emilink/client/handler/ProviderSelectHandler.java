package org.chatterjay.emilink.client.handler;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.util.ModLogger;
import org.chatterjay.emilink.util.ProviderSearchHelper;

@Mod.EventBusSubscriber(modid = Emilink.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProviderSelectHandler {

    private static final String PROVIDER_SCREEN_CLASS = "com.extendedae_plus.client.screen.ProviderSelectScreen";

    private ProviderSelectHandler() {}

    @SubscribeEvent
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        if (!screen.getClass().getName().equals(PROVIDER_SCREEN_CLASS)) return;

        ModLogger.info("ProviderSelectHandler: ProviderSelectScreen opened, class={}", screen.getClass().getName());

        try {
            tryFillSearchKey(screen);
        } catch (Throwable e) {
            ModLogger.debug("ProviderSelectHandler: auto-fill failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static void tryFillSearchKey(Screen screen) throws Exception {
        // Read the search box via reflection
        ModLogger.info("ProviderSelectHandler: reading searchBox field");
        var searchBoxField = screen.getClass().getDeclaredField("searchBox");
        searchBoxField.setAccessible(true);
        Object searchBox = searchBoxField.get(screen);
        if (searchBox == null) {
            ModLogger.info("ProviderSelectHandler: searchBox is null, skipping");
            return;
        }

        String currentText = (String) searchBox.getClass().getMethod("getValue").invoke(searchBox);
        ModLogger.info("ProviderSelectHandler: current search text='{}'", currentText == null ? "null" : currentText);
        if (currentText != null && !currentText.isEmpty() && !currentText.equals("jemi")) {
            ModLogger.info("ProviderSelectHandler: search already filled with '{}', skipping", currentText);
            return;
        }
        if ("jemi".equals(currentText)) {
            ModLogger.info("ProviderSelectHandler: search was set to 'jemi' meta-category, overriding");
        }

        // First priority: use the last focused category from EMI RecipeScreen (player's chosen tab)
        String lastCategory = ProviderSearchHelper.consumeLastRecipeCategory();
        if (lastCategory != null && !lastCategory.isEmpty()) {
            ModLogger.info("ProviderSelectHandler: using saved recipe category '{}' from RecipeScreen", lastCategory);
            searchBox.getClass().getMethod("setValue", String.class).invoke(searchBox, lastCategory);
            ProviderSearchHelper.setLastProcessingName(lastCategory);
            return;
        }
        ModLogger.info("ProviderSelectHandler: no saved recipe category from RecipeScreen, falling back to item-based derivation");

        // Read parent screen
        ModLogger.info("ProviderSelectHandler: reading parent field");
        var parentField = screen.getClass().getDeclaredField("parent");
        parentField.setAccessible(true);
        Screen parent = (Screen) parentField.get(screen);
        if (parent == null) {
            ModLogger.info("ProviderSelectHandler: parent is null, skipping");
            return;
        }
        ModLogger.info("ProviderSelectHandler: parent screen class={}", parent.getClass().getName());

        String key = deriveSearchKey(parent);
        if (key == null || key.isEmpty()) {
            ModLogger.info("ProviderSelectHandler: no search key could be derived from parent");
            return;
        }

        ModLogger.info("ProviderSelectHandler: auto-filled empty search key to '{}'", key);
        searchBox.getClass().getMethod("setValue", String.class).invoke(searchBox, key);
        ProviderSearchHelper.setLastProcessingName(key);
    }

    private static String deriveSearchKey(Screen parent) {
        ModLogger.info("ProviderSelectHandler: deriving search key from parent");

        if (!(parent instanceof AbstractContainerScreen<?> container)) {
            ModLogger.info("ProviderSelectHandler: parent is not AbstractContainerScreen, cannot derive key");
            return null;
        }

        ModLogger.info("ProviderSelectHandler: parent menu class={}", container.getMenu().getClass().getName());

        // PatternEncodingTermScreen → menu has the encoding slots
        if (container.getMenu() instanceof PatternEncodingTermMenu encodingMenu) {
            ModLogger.info("ProviderSelectHandler: parent menu is PatternEncodingTermMenu, checking slots");
            return deriveFromEncodingMenu(encodingMenu);
        }

        ModLogger.info("ProviderSelectHandler: parent menu is not PatternEncodingTermMenu, cannot access encoding slots");
        return null;
    }

    private static String deriveFromEncodingMenu(PatternEncodingTermMenu menu) {
        if (menu.getMode() == EncodingMode.CRAFTING) {
            ModLogger.info("ProviderSelectHandler: encoding mode=CRAFTING, checking crafting grid");

            // Check if crafting grid has items
            var craftingSlots = menu.getCraftingGridSlots();
            boolean hasItems = false;
            for (int i = 0; i < craftingSlots.length; i++) {
                var stack = craftingSlots[i].getItem();
                if (!stack.isEmpty()) {
                    ModLogger.info("ProviderSelectHandler: crafting slot[{}] has item: {}", i, stack.getHoverName().getString());
                    hasItems = true;
                }
            }
            if (hasItems) {
                ModLogger.info("ProviderSelectHandler: crafting grid has items, returning 'crafting'");
                return "crafting";
            }
            ModLogger.info("ProviderSelectHandler: crafting grid is empty");
        } else {
            ModLogger.info("ProviderSelectHandler: encoding mode=PROCESSING, checking output slots");
        }

        // Processing mode: check output slots for items, then match to EMI recipe
        ModLogger.info("ProviderSelectHandler: checking processing output slots");
        var outSlots = menu.getProcessingOutputSlots();
        for (int i = 0; i < outSlots.length; i++) {
            var stack = outSlots[i].getItem();
            if (stack.isEmpty()) {
                ModLogger.info("ProviderSelectHandler: output slot[{}] is empty", i);
                continue;
            }
            ModLogger.info("ProviderSelectHandler: output slot[{}] has item: {}", i, stack.getHoverName().getString());

            // Direct item in processing output: match to EMI recipe
            String key = matchEmiRecipeByOutput(stack);
            if (key != null) return key;
        }
        ModLogger.info("ProviderSelectHandler: no match from output slots, checking input slots as fallback");

        // Also check input slots as fallback
        var inSlots = menu.getProcessingInputSlots();
        for (int i = 0; i < inSlots.length; i++) {
            var stack = inSlots[i].getItem();
            if (stack.isEmpty()) {
                ModLogger.info("ProviderSelectHandler: input slot[{}] is empty", i);
                continue;
            }
            ModLogger.info("ProviderSelectHandler: input slot[{}] has item: {}", i, stack.getHoverName().getString());

            String key = matchEmiRecipeByOutput(stack);
            if (key != null) return key;
        }

        ModLogger.info("ProviderSelectHandler: all slots checked, no match found");
        return null;
    }

    private static String matchEmiRecipeByOutput(ItemStack stack) {
        ModLogger.info("ProviderSelectHandler: matching item '{}' to EMI recipes", stack.getHoverName().getString());
        try {
            var emiStack = EmiStack.of(stack);
            if (emiStack.isEmpty()) {
                ModLogger.info("ProviderSelectHandler: EmiStack is empty for {}", stack.getHoverName().getString());
                return null;
            }

            ModLogger.info("ProviderSelectHandler: querying EMI recipe manager for output {}", stack.getHoverName().getString());
            var recipes = EmiApi.getRecipeManager().getRecipesByOutput(emiStack);
            if (recipes == null || recipes.isEmpty()) {
                ModLogger.info("ProviderSelectHandler: no EMI recipes found for output {}", stack.getHoverName().getString());
                return null;
            }

            ModLogger.info("ProviderSelectHandler: found {} EMI recipes for output {}, searching for best match", recipes.size(), stack.getHoverName().getString());
            String fallback = null;
            for (var recipe : recipes) {
                var catId = recipe.getCategory().getId();
                if (catId == null) continue;
                String path = catId.getPath();
                if (path.startsWith("tag_recipes")) {
                    if (fallback == null) fallback = path;
                    ModLogger.info("ProviderSelectHandler: skipping generic category '{}' for {}", path, stack.getHoverName().getString());
                    continue;
                }
                ModLogger.info("ProviderSelectHandler: matched {} to specific category '{}'", stack.getHoverName().getString(), path);
                return path;
            }
            if (fallback != null) {
                ModLogger.info("ProviderSelectHandler: no specific category found, falling back to '{}'", fallback);
                return fallback;
            }
            ModLogger.info("ProviderSelectHandler: no valid recipe category found for {}", stack.getHoverName().getString());
        } catch (Throwable e) {
            ModLogger.info("ProviderSelectHandler: EMI match failed for {}: {}: {}",
                    stack.getHoverName().getString(), e.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }
}
