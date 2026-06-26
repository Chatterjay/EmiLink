package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
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

        // 1. Focused EditBox (most precise, works universally)
        if (screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox focusedEb) {
            focusedEb.setValue(text);
            focusedEb.setCursorPosition(text.length());
            fillSearchHandled = true;
            event.setCanceled(true);
            return;
        }

        // 2. BD-specific (non-standard search API)
        if (BDProxy.isBDNetGUI(screen) && BDProxy.setSearchText(screen, text)) {
            fillSearchHandled = true;
            event.setCanceled(true);
            return;
        }

        // 3. Any EditBox child (catches most mod search fields)
        for (var child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox eb) {
                eb.setValue(text);
                eb.setCursorPosition(text.length());
                fillSearchHandled = true;
                event.setCanceled(true);
                return;
            }
        }

        // 4. Common search field field names via reflection
        // Catches mods whose search widget isn't in screen.children() (e.g. Sophisticated Storage)
        for (var fieldName : new String[]{"searchBox", "searchField", "search"}) {
            var field = findScreenField(screen, fieldName);
            if (field == null) continue;
            try {
                field.setAccessible(true);
                Object widget = field.get(screen);
                if (widget == null) continue;
                var setValue = widget.getClass().getMethod("setValue", String.class);
                setValue.invoke(widget, text);
                fillSearchHandled = true;
                event.setCanceled(true);
                return;
            } catch (Exception ignored) {}
        }

        // 5. EMI search fallback
        EmiApi.setSearchText(text);
        fillSearchHandled = true;
        event.setCanceled(true);
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

    /** Draw deposit hint tooltip when cursor has an item over the EMI sidebar. */
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!org.chatterjay.emiextend.config.EmiLinkConfig.ENABLE_AE_DEPOSIT.get()) return;
        var mc = Minecraft.getInstance();
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> cs)) return;
        var carried = cs.getMenu().getCarried();
        if (carried.isEmpty()) return;

        var space = dev.emi.emi.screen.EmiScreenManager.getHoveredSpace(event.getMouseX(), event.getMouseY());
        if (space == null) return;
        if (mc.player == null || !org.chatterjay.emiextend.client.handler.EmiInteractionHandler.hasWirelessTerminal(mc.player)) return;
        // Don't show hint when EMI would delete the item (cheat mode on)
        boolean emiWouldDelete = dev.emi.emi.config.EmiConfig.cheatMode == dev.emi.emi.config.CheatMode.TRUE
                || (dev.emi.emi.config.EmiConfig.cheatMode == dev.emi.emi.config.CheatMode.CREATIVE && mc.player.isCreative());
        if (emiWouldDelete) return;

        var text = Component.translatable(
                org.chatterjay.emiextend.client.handler.EmiInteractionHandler.matchesDepositBatchModifier()
                        ? "emilink.tooltip.deposit_all"
                        : "emilink.tooltip.deposit");
        var pose = event.getGuiGraphics().pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        event.getGuiGraphics().renderTooltip(mc.font, text, event.getMouseX(), event.getMouseY());
        pose.popPose();
    }

    @javax.annotation.Nullable
    private static java.lang.reflect.Field findScreenField(Screen screen, String name) {
        Class<?> cls = screen.getClass();
        while (cls != null && cls != Screen.class) {
            try {
                var field = cls.getDeclaredField(name);
                if (field != null) return field;
            } catch (NoSuchFieldException ignored) {}
            cls = cls.getSuperclass();
        }
        return null;
    }
}
