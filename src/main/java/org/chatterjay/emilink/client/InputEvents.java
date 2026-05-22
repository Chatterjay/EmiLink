package org.chatterjay.emilink.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.util.ModLogger;

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
            onQuickCraftKey(event);
            return;
        }

        if (ModKeybindings.QUICK_FILL_SLOT_KEY.matches(keyCode, scanCode)) {
            onQuickFillSlotKey(event);
        }
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

    private static void onQuickCraftKey(ScreenEvent.KeyPressed.Pre event) {
        var handled = EmiApi.getHandledScreen();
        if (handled == null) {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen<?> container) {
                handled = container;
            }
        }
        if (handled == null) return;

        if (!isPatternEncodingTerminal(handled)) {
            ModLogger.info("B key: not a pattern terminal, screen={}", handled.getClass().getName());
            return;
        }

        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return;

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
            return;
        }

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

        var menu = handled.getMenu();
        if (menu instanceof PatternEncodingTermMenu encodingMenu) {
            FakeSlot[] targetSlots = encodingMenu.getMode() == EncodingMode.CRAFTING
                    ? encodingMenu.getCraftingGridSlots()
                    : encodingMenu.getProcessingInputSlots();

            // Clear all target slots first
            for (FakeSlot slot : targetSlots) {
                NetworkHandler.instance().sendToServer(
                        new InventoryActionPacket(InventoryAction.SET_FILTER, slot.index, ItemStack.EMPTY));
            }

            // Fill with expanded items (no tag expansion — first item only)
            int count = Math.min(items.size(), targetSlots.length);
            for (int i = 0; i < count; i++) {
                if (!items.get(i).isEmpty()) {
                    NetworkHandler.instance().sendToServer(
                            new InventoryActionPacket(InventoryAction.SET_FILTER,
                                    targetSlots[i].index, items.get(i)));
                }
            }

            ModLogger.info("QuickPattern: encoded {} ({} inputs)", recipe.getId(), count);
            event.setCanceled(true);
        } else {
            ModLogger.info("QuickPattern: not a PatternEncodingTermMenu, screen={}", handled.getClass().getName());
        }
    }

    private static void onQuickFillSlotKey(ScreenEvent.KeyPressed.Pre event) {
        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return;

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) return;

        var emiStacks = ingredient.getEmiStacks();
        if (emiStacks.isEmpty()) return;

        var emiStack = emiStacks.get(0);

        var itemStack = emiStack.getItemStack();
        if (itemStack.isEmpty()) return;

        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        for (var slot : containerScreen.getMenu().slots) {
            if (slot instanceof FakeSlot fakeSlot && slot.getItem().isEmpty()) {
                NetworkHandler.instance().sendToServer(
                        new InventoryActionPacket(InventoryAction.SET_FILTER, fakeSlot.index, itemStack.copy()));
                ModLogger.info("QuickFillSlot: set slot {} with {}", fakeSlot.index, emiStack.getId());
                event.setCanceled(true);
                return;
            }
        }
    }
}
