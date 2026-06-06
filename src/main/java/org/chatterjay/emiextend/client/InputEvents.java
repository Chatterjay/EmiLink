package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.util.ModLogger;

import appeng.api.stacks.GenericStack;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.menu.slot.FakeSlot;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
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

        var first = emiStacks.getFirst();

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

        // ExtendedAE: GuiExPatternTerminal
        try {
            var exTermClass = Class.forName("com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal");
            if (exTermClass.isInstance(screen)) {
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
            ModLogger.warn("FILL_SEARCH_KEY: ExtendedAE terminal exception: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }

        // AdvancedAE: QuantumCrafterTermScreen / QuantumCrafterWirelessTermScreen
        try {
            boolean isQuantum = false;
            try {
                isQuantum = Class.forName("net.pedroksl.advanced_ae.client.gui.QuantumCrafterTermScreen").isInstance(screen);
            } catch (Throwable ignored) {}
            if (!isQuantum) {
                try {
                    isQuantum = Class.forName("net.pedroksl.advanced_ae.client.gui.QuantumCrafterWirelessTermScreen").isInstance(screen);
                } catch (Throwable ignored) {}
            }
            if (isQuantum) {
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
            ModLogger.warn("FILL_SEARCH_KEY: AdvancedAE quantum terminal exception: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }

        // RefinedStorage: AbstractGridScreen (GridScreen, PatternGridScreen, etc.)
        try {
            var rsGridClass = Class.forName("com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen");
            if (rsGridClass.isInstance(screen)) {
                var searchField = rsGridClass.getDeclaredField("searchField");
                searchField.setAccessible(true);
                Object fieldObj = searchField.get(screen);
                if (fieldObj != null) {
                    fieldObj.getClass().getMethod("setValue", String.class).invoke(fieldObj, text);
                    fillSearchHandled = true;
                    event.setCanceled(true);
                    return;
                }
            }
        } catch (Throwable e) {
            // RS not installed or class mismatch
        }

        // RefinedStorage: AutocrafterManagerScreen
        try {
            var rsManagerClass = Class.forName("com.refinedmods.refinedstorage.common.autocrafting.autocraftermanager.AutocrafterManagerScreen");
            if (rsManagerClass.isInstance(screen)) {
                var searchField = rsManagerClass.getDeclaredField("searchField");
                searchField.setAccessible(true);
                Object fieldObj = searchField.get(screen);
                if (fieldObj != null) {
                    fieldObj.getClass().getMethod("setValue", String.class).invoke(fieldObj, text);
                    fillSearchHandled = true;
                    event.setCanceled(true);
                    return;
                }
            }
        } catch (Throwable e) {
            // RS not installed or class mismatch
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

        // BD search field
        if (BDProxy.isBDNetGUI(screen)) {
            if (BDProxy.setSearchText(screen, text)) {
                fillSearchHandled = true;
                event.setCanceled(true);
            } else {
                ModLogger.warn("FILL_SEARCH_KEY: BD setSearchText failed");
            }
            return;
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

    /** Check if screen is any supported pattern encoding terminal (AE2, ExtendedAE, or RS) */
    private static boolean isPatternEncodingTerminal(Screen screen) {
        if (AE2Proxy.isPatternEncodingTermScreen(screen)) return true;
        try {
            if (Class.forName("com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal").isInstance(screen)) return true;
        } catch (Throwable e) { /* not ExtendedAE */ }
        try {
            if (Class.forName("com.refinedmods.refinedstorage.common.autocrafting.patterngrid.PatternGridScreen").isInstance(screen)) return true;
        } catch (Throwable e) { /* not RS */ }
        return false;
    }

    /** B key — encode pattern in PatternEncodingTermScreen */
    private static void onQuickCraftKey(ScreenEvent.KeyPressed.Pre event) {
        var handled = EmiApi.getHandledScreen();
        if (handled == null) {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen<?> container) {
                handled = container;
            }
        }
        if (handled == null) return;

        // BD Craft GUI → single craft to inventory
        if (BDProxy.isBDCraftGUI(handled)) {
            PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, 2));
            event.setCanceled(true);
            ModLogger.info("B key: BD single craft triggered");
            return;
        }

        if (!isPatternEncodingTerminal(handled)) {
            ModLogger.info("B key: not a pattern terminal or BD craft, screen={}", handled.getClass().getName());
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
                    var list = EmiApi.getRecipeManager().getRecipesByOutput(stacks.getFirst());
                    if (list != null && !list.isEmpty()) recipe = list.get(0);
                }
            }
        }

        if (recipe == null) {
            ModLogger.info("B key: no recipe found for hovered stack");
            return;
        }

        if (EmiRecipeFiller.performFill(recipe, handled,
                EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.NONE, 1)) {
            ModLogger.info("QuickPattern: encoded {}", recipe.getId());
            event.setCanceled(true);
        } else {
            ModLogger.info("QuickPattern: performFill returned false for {}", recipe.getId());
        }
    }

    /** N key (configurable) — fill the first empty FakeSlot with the hovered item */
    private static void onQuickFillSlotKey(ScreenEvent.KeyPressed.Pre event) {
        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) return;

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) return;

        var emiStacks = ingredient.getEmiStacks();
        if (emiStacks.isEmpty()) return;

        var emiStack = emiStacks.getFirst();

        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        for (var slot : containerScreen.getMenu().slots) {
            if (!slot.getItem().isEmpty()) continue;

            // AE2 FakeSlot — try with ItemStack, fallback to GenericStack for fluids/chemicals
            if (slot instanceof FakeSlot fakeSlot) {
                var itemStack = emiStack.getItemStack();
                if (itemStack.isEmpty()) {
                    var genericStack = EmiStackHelper.toGenericStack(emiStack);
                    if (genericStack == null) continue;
                    itemStack = GenericStack.wrapInItemStack(genericStack);
                    if (itemStack.isEmpty()) continue;
                }
                PacketDistributor.sendToServer(
                        new InventoryActionPacket(InventoryAction.SET_FILTER, fakeSlot.index, itemStack.copy()));
                ModLogger.info("QuickFillSlot: set slot {} with {}", fakeSlot.index, emiStack.getId());
                event.setCanceled(true);
                return;
            }

        }
    }
}
