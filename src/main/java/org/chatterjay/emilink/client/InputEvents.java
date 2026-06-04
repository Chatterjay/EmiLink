package org.chatterjay.emilink.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.client.handler.BookmarkPriorityHandler;
import org.chatterjay.emilink.client.handler.WrapAsBookHandler;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.integration.BDProxy;
import org.chatterjay.emilink.network.packet.c2s.BDActionPacket;
import org.chatterjay.emilink.util.ModLogger;
import org.chatterjay.emilink.util.ProviderSearchHelper;

import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import appeng.parts.encoding.EncodingMode;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = Emilink.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class InputEvents {
    private InputEvents() {}

    private static boolean fillSearchHandled = false;

    @SubscribeEvent
    public static void onCharTypedPre(ScreenEvent.CharacterTyped.Pre event) {
        if (fillSearchHandled) {
            fillSearchHandled = false;
            if (event.getCodePoint() == 'f' || event.getCodePoint() == 'F') {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();

        if (ModKeybindings.FILL_SEARCH_KEY.matches(keyCode, scanCode)) {
            onFillSearchKey(event);
            return;
        }

        if (ModKeybindings.QUICK_PATTERN_KEY.matches(keyCode, scanCode)) {
            if (handleQuickCraft()) {
                event.setCanceled(true);
            }
            return;
        }

        if (ModKeybindings.QUICK_FILL_SLOT_KEY.matches(keyCode, scanCode)) {
            if (handleQuickFillSlot()) {
                event.setCanceled(true);
            }
            return;
        }

        if (ModKeybindings.TOGGLE_WRAP_BOOK_KEY.matches(keyCode, scanCode)) {
            WrapAsBookHandler.toggle();
            ModLogger.info("InputEvents: TOGGLE_WRAP_BOOK_KEY pressed, active={}", WrapAsBookHandler.isActive());
            event.setCanceled(true);
        }
    }

    /**
     * Quick pattern encode — called from both InputEvents (Forge event) and
     * EmiInteractionHandler (mixin). Returns true if handled.
     */
    public static boolean handleQuickCraft() {
        return quickCraft();
    }

    /**
     * Quick fill to first empty FakeSlot — called from both InputEvents (Forge event)
     * and EmiInteractionHandler (mixin). Returns true if handled.
     */
    public static boolean handleQuickFillSlot() {
        return quickFillSlot();
    }

    private static void onFillSearchKey(ScreenEvent.KeyPressed.Pre event) {
        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return;

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) return;

        var emiStacks = ingredient.getEmiStacks();
        if (emiStacks.isEmpty()) return;

        var first = emiStacks.get(0);

        boolean alt = Screen.hasAltDown();
        var text = alt ? "@" + first.getId().getNamespace() : first.getName().getString();
        if (text.isEmpty()) return;

        var screen = Minecraft.getInstance().screen;
        if (screen == null) return;

        // AE2: PatternAccessTermScreen
        try {
            var patClass = Class.forName("appeng.client.gui.me.patternaccess.PatternAccessTermScreen");
            if (patClass.isInstance(screen)) {
                var searchField = patClass.getDeclaredField("searchField");
                searchField.setAccessible(true);
                Object fieldObj = searchField.get(screen);
                if (fieldObj != null) {
                    fieldObj.getClass().getMethod("setValue", String.class).invoke(fieldObj, text);
                }
                fillSearchHandled = true;
                event.setCanceled(true);
                return;
            }
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: PatternAccessTermScreen exception: {}", e.getMessage());
        }

        // EAEP: GuiWirelessExPAT
        try {
            var eaeClass = Class.forName("com.glodblock.github.extendedae.xmod.wt.GuiWirelessExPAT");
            if (eaeClass.isInstance(screen)) {
                java.lang.reflect.Field searchField = null;
                Class<?> cls = screen.getClass();
                while (cls != null && searchField == null) {
                    try {
                        searchField = cls.getDeclaredField("searchField");
                    } catch (NoSuchFieldException ignored) {}
                    cls = cls.getSuperclass();
                }
                if (searchField != null) {
                    searchField.setAccessible(true);
                    Object fieldObj = searchField.get(screen);
                    if (fieldObj != null) {
                        fieldObj.getClass().getMethod("setValue", String.class).invoke(fieldObj, text);
                        fillSearchHandled = true;
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: EAEP GuiWirelessExPAT exception: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }

        // AE2 terminal search field
        try {
            var meStorageClass = Class.forName("appeng.client.gui.me.common.MEStorageScreen");
            if (meStorageClass.isInstance(screen)) {
                var searchField = meStorageClass.getDeclaredField("searchField");
                searchField.setAccessible(true);
                Object fieldObj = searchField.get(screen);
                if (fieldObj != null) {
                    fieldObj.getClass().getMethod("setValue", String.class).invoke(fieldObj, text);
                }
                var setSearch = meStorageClass.getDeclaredMethod("setSearchText", String.class);
                setSearch.setAccessible(true);
                setSearch.invoke(screen, text);
                fillSearchHandled = true;
                event.setCanceled(true);
                return;
            }
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: AE2 terminal search exception: {}", e.getMessage());
        }

        // EMI search fallback
        try {
            EmiApi.setSearchText(text);
            fillSearchHandled = true;
            event.setCanceled(true);
        } catch (Throwable e) {
            ModLogger.warn("FILL_SEARCH_KEY: EMI setSearchText failed: {}", e.getMessage());
        }
    }

    private static boolean isPatternEncodingTerminal(Screen screen) {
        if (AE2Proxy.isPatternEncodingTermScreen(screen)) return true;
        try {
            return Class.forName("com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal").isInstance(screen);
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean fillPatternTerminal(EmiRecipe recipe, AbstractContainerScreen<?> handled) {
        // Expand each recipe input to a single ItemStack (tag → first item)
        var inputs = recipe.getInputs();
        var items = new ArrayList<ItemStack>();
        for (var input : inputs) {
            var stacks = input.getEmiStacks();
            if (!stacks.isEmpty()) {
                var stack = stacks.get(0).getItemStack();
                items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            } else {
                items.add(ItemStack.EMPTY);
            }
        }

        var outputs = recipe.getOutputs();
        var outputItems = new ArrayList<ItemStack>();
        for (var out : outputs) {
            var stack = out.getItemStack();
            outputItems.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }

        var menu = handled.getMenu();
        if (menu instanceof PatternEncodingTermMenu encodingMenu) {
            if (encodingMenu.getMode() == EncodingMode.CRAFTING) {
                // Crafting mode: fill the 3x3 grid only; result is computed by AE2
                FakeSlot[] targetSlots = encodingMenu.getCraftingGridSlots();

                for (FakeSlot slot : targetSlots) {
                    NetworkHandler.instance().sendToServer(
                            new InventoryActionPacket(InventoryAction.SET_FILTER, slot.index, ItemStack.EMPTY));
                }

                int count = Math.min(items.size(), targetSlots.length);
                for (int i = 0; i < count; i++) {
                    if (!items.get(i).isEmpty()) {
                        NetworkHandler.instance().sendToServer(
                                new InventoryActionPacket(InventoryAction.SET_FILTER,
                                        targetSlots[i].index, items.get(i)));
                    }
                }

                BookmarkPriorityHandler.applyBookmarkPriority(
                        encodingMenu.getCraftingGridSlots(), recipe.getInputs());

                ModLogger.info("QuickPattern: encoded crafting pattern {} ({} inputs)", recipe.getId(), count);
            } else {
                ModLogger.info("QuickPattern: processing recipe id={}, category={}",
                        recipe.getId(), recipe.getCategory().getId());
                // Processing mode: fill both input and output slots
                FakeSlot[] inputSlots = encodingMenu.getProcessingInputSlots();
                FakeSlot[] outputSlots = encodingMenu.getProcessingOutputSlots();

                for (FakeSlot slot : inputSlots) {
                    NetworkHandler.instance().sendToServer(
                            new InventoryActionPacket(InventoryAction.SET_FILTER, slot.index, ItemStack.EMPTY));
                }
                for (FakeSlot slot : outputSlots) {
                    NetworkHandler.instance().sendToServer(
                            new InventoryActionPacket(InventoryAction.SET_FILTER, slot.index, ItemStack.EMPTY));
                }

                int inputCount = Math.min(items.size(), inputSlots.length);
                for (int i = 0; i < inputCount; i++) {
                    if (!items.get(i).isEmpty()) {
                        NetworkHandler.instance().sendToServer(
                                new InventoryActionPacket(InventoryAction.SET_FILTER,
                                        inputSlots[i].index, items.get(i)));
                    }
                }

                int outputCount = Math.min(outputItems.size(), outputSlots.length);
                for (int i = 0; i < outputCount; i++) {
                    if (!outputItems.get(i).isEmpty()) {
                        NetworkHandler.instance().sendToServer(
                                new InventoryActionPacket(InventoryAction.SET_FILTER,
                                        outputSlots[i].index, outputItems.get(i)));
                    }
                }

                BookmarkPriorityHandler.applyBookmarkPriority(inputSlots, recipe.getInputs());

                ModLogger.info("QuickPattern: encoded processing pattern {} ({} inputs, {} outputs)",
                        recipe.getId(), inputCount, outputCount);
            }

            // Update EAEP provider search key so the recipe ID field shows the correct type
            var categoryId = recipe.getCategory().getId();
            if (categoryId != null) {
                String searchKey = categoryId.getPath();
                ModLogger.info("QuickPattern: setting EAEP provider search key to '{}' (from category {})",
                        searchKey, categoryId);
                ProviderSearchHelper.setLastProcessingName(searchKey);
                // Also directly update the ExPatternTerminal search field if open
                trySetExPatternTerminalSearchField(handled, searchKey);
            } else {
                ModLogger.info("QuickPattern: recipe {} has no category ID", recipe.getId());
            }

            return true;
        }
        return false;
    }

    private static void trySetExPatternTerminalSearchField(Screen screen, String searchKey) {
        try {
            Class<?> guiClass = Class.forName("com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal");
            if (!guiClass.isInstance(screen)) return;

            var field = guiClass.getDeclaredField("searchOutField");
            field.setAccessible(true);
            Object textField = field.get(screen);
            if (textField == null) return;

            ModLogger.info("QuickPattern: setting ExPatternTerminal searchOutField to '{}'", searchKey);
            textField.getClass().getMethod("setValue", String.class).invoke(textField, searchKey);
        } catch (Throwable e) {
            ModLogger.debug("QuickPattern: trySetExPatternTerminalSearchField failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static boolean quickCraft() {
        ModLogger.info("InputEvents: onQuickCraftKey called");
        var handled = EmiApi.getHandledScreen();
        if (handled == null) {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen<?> container) {
                handled = container;
            }
        }
        if (handled == null) return false;

        // BD Craft GUI → single craft to inventory (requires EmiLink server)
        if (BDProxy.isBDCraftGUI(handled)) {
            if (org.chatterjay.emilink.network.packet.s2c.ServerHasModPacket.serverHasMod) {
                org.chatterjay.emilink.network.NetworkHandler.sendToServer(new BDActionPacket(ItemStack.EMPTY, 2));
                ModLogger.info("B key: BD single craft triggered");
            } else {
                ModLogger.info("B key: BD single craft skipped (no EmiLink server)");
            }
            return true;
        }

        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return false;

        EmiRecipe recipe = hovered.getRecipeContext();
        if (recipe == null) recipe = EmiApi.getRecipeContext(hovered.getStack());
        if (recipe == null) {
            var ing = hovered.getStack();
            if (ing != null && !ing.isEmpty()) {
                var stacks = ing.getEmiStacks();
                if (!stacks.isEmpty()) {
                    var allRecipes = EmiApi.getRecipeManager().getRecipesByOutput(stacks.get(0));
                    if (allRecipes != null && !allRecipes.isEmpty()) {
                        recipe = allRecipes.stream()
                                .filter(r -> r.getCategory() == VanillaEmiRecipeCategories.CRAFTING)
                                .findFirst()
                                .orElse(allRecipes.get(0));
                    }
                }
            }
        }

        if (recipe == null) {
            ModLogger.info("B key: no recipe found for hovered stack");
            return false;
        }

        ModLogger.info("B key: recipe id = {}", recipe.getId());
        var catId = recipe.getCategory().getId();
        ModLogger.info("B key: recipe category = {} (path={})", catId, catId == null ? "null" : catId.getPath());

        // Strategy 1: Pattern Encoding Terminal → direct FakeSlot filling
        if (isPatternEncodingTerminal(handled)) {
            return fillPatternTerminal(recipe, handled);
        }

        // Strategy 2: EMI generic recipe filler (crafting tables, etc.)
        ModLogger.info("B key: trying EmiRecipeFiller, screen={}", handled.getClass().getName());
        try {
            boolean filled = EmiRecipeFiller.performFill(recipe, handled,
                    EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.NONE, 1);
            if (filled) {
                ModLogger.info("B key: EmiRecipeFiller filled recipe {}", recipe.getId());
                return true;
            }
        } catch (Exception e) {
            ModLogger.info("B key: EmiRecipeFiller exception: {}", e.getMessage());
        }

        ModLogger.info("B key: no handler available for screen {}", handled.getClass().getName());
        return false;
    }

    private static boolean quickFillSlot() {
        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return false;

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) return false;

        var emiStacks = ingredient.getEmiStacks();
        if (emiStacks.isEmpty()) return false;

        var emiStack = emiStacks.get(0);

        var itemStack = emiStack.getItemStack();
        if (itemStack.isEmpty()) return false;

        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> containerScreen)) return false;

        for (var slot : containerScreen.getMenu().slots) {
            if (slot instanceof FakeSlot fakeSlot && slot.getItem().isEmpty()) {
                NetworkHandler.instance().sendToServer(
                        new InventoryActionPacket(InventoryAction.SET_FILTER, fakeSlot.index, itemStack.copy()));
                ModLogger.info("QuickFillSlot: set slot {} with {}", fakeSlot.index, emiStack.getId());
                return true;
            }
        }
        return false;
    }
}
