package org.chatterjay.emiextend.client.handler;

import appeng.menu.me.items.CraftingTermMenu;
import appeng.integration.modules.itemlists.CraftingHelper;
import dev.emi.emi.config.CheatMode;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.network.packet.c2s.AEDepositPacket;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.client.AENetworkCache;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.integration.CuriosProxy;
import org.chatterjay.emiextend.integration.EAEPProxy;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles EMI interaction routing for AE2, BD (BeyondDimensions), and EAEP
 * (ExtendedAE Plus) integration. All business logic for mouse/key handling
 * in EMI screens is delegated here.
 */
public final class EmiInteractionHandler {

    private EmiInteractionHandler() {}

    /**
     * Handle key press in EMI screens. Returns true if handled.
     */
    public static boolean onKeyPressed(int keyCode, int scanCode, int modifiers, int mouseX, int mouseY) {
        var mc = Minecraft.getInstance();
        if (AE2Proxy.isCraftConfirmScreen(mc.screen)) {
            ItemStack stack = AE2Proxy.getStackUnderMouse(mc.screen, mouseX, mouseY);
            if (!stack.isEmpty()) {
                EmiStack emiStack = EmiStack.of(stack);
                if (EmiScreenManager.stackInteraction(
                        new EmiStackInteraction(emiStack),
                        bind -> bind.matchesKey(keyCode, scanCode))) {
                    return true;
                }
            }
        }
        if (isCraftingCPUScreen(mc.screen)) {
            EmiStack emiStack = getGenericStackUnderMouse(mc.screen, mouseX, mouseY);
            if (emiStack != null && !emiStack.isEmpty()) {
                if (EmiScreenManager.stackInteraction(
                        new EmiStackInteraction(emiStack),
                        bind -> bind.matchesKey(keyCode, scanCode))) {
                    return true;
                }
            }
        }
        // Ars Nouveau storage terminal: route hovered item to EMI stack interaction
        try {
            Class<?> arsScreenClass = Class.forName("com.hollingsworth.arsnouveau.client.container.AbstractStorageTerminalScreen");
            if (arsScreenClass.isInstance(mc.screen)) {
                var slotField = arsScreenClass.getDeclaredField("slotIDUnderMouse");
                slotField.setAccessible(true);
                int idx = slotField.getInt(mc.screen);
                if (idx >= 0) {
                    var itemsField = arsScreenClass.getDeclaredField("itemsSorted");
                    itemsField.setAccessible(true);
                    Object items = itemsField.get(mc.screen);
                    if (items instanceof List<?> list && idx < list.size()) {
                        Object entry = list.get(idx);
                        if (entry != null) {
                            ItemStack stack = (ItemStack) entry.getClass().getMethod("getStack").invoke(entry);
                            if (!stack.isEmpty()) {
                                EmiStack emiStack = EmiStack.of(stack);
                                if (EmiScreenManager.stackInteraction(
                                        new EmiStackInteraction(emiStack),
                                        bind -> bind.matchesKey(keyCode, scanCode))) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Check via reflection if the given screen is a CraftingCPUScreen (or subclass CraftingStatusScreen).
     */
    private static boolean isCraftingCPUScreen(Screen screen) {
        if (screen == null) return false;
        try {
            Class<?> clazz = Class.forName("appeng.client.gui.me.crafting.CraftingCPUScreen");
            return clazz.isInstance(screen);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the stack under mouse from a CraftingCPUScreen's crafting status table via reflection.
     * Converts GenericStack to EmiStack for EMI interaction routing.
     */
    private static EmiStack getGenericStackUnderMouse(Screen screen, int mouseX, int mouseY) {
        try {
            // getStackUnderMouse returns StackWithBounds wrapping a GenericStack
            var method = screen.getClass().getMethod("getStackUnderMouse", double.class, double.class);
            Object swb = method.invoke(screen, (double) mouseX, (double) mouseY);
            if (swb == null) return null;

            // StackWithBounds.stack() → GenericStack
            Object genericStack = swb.getClass().getMethod("stack").invoke(swb);
            if (genericStack == null) return null;

            // GenericStack.what() → AEKey
            Object what = genericStack.getClass().getMethod("what").invoke(genericStack);
            if (what == null) return null;

            // Try AEItemKey first → toStack() → EmiStack.of(ItemStack)
            try {
                Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
                if (aeItemKeyClass.isInstance(what)) {
                    ItemStack itemStack = (ItemStack) aeItemKeyClass.getMethod("toStack").invoke(what);
                    if (!itemStack.isEmpty()) {
                        return EmiStack.of(itemStack);
                    }
                }
            } catch (Exception ignored) {}

            // For other AEKey types (fluids, chemicals), scan EMI index
            try {
                var idMethod = what.getClass().getMethod("getId");
                Object id = idMethod.invoke(what);
                if (id instanceof net.minecraft.resources.ResourceLocation rl) {
                    for (var es : EmiApi.getIndexStacks()) {
                        if (rl.equals(es.getId())) {
                            return es;
                        }
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Handle mouse release in EMI screens. Returns true if handled.
     */
    public static boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (button != 2 && button != 0) return false;

        if (button == 0 && Screen.hasControlDown() && tryCtrlLeftQuickCraft(mouseX, mouseY)) {
            return true;
        }

        // Deposit: cursor has item → deposit into AE (only when EMI won't delete it)
        if (button == 0 && EmiLinkConfig.ENABLE_AE_DEPOSIT.get()) {
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs) {
                var carried = cs.getMenu().getCarried();
                boolean emiWouldDelete = EmiConfig.cheatMode == CheatMode.TRUE
                        || (EmiConfig.cheatMode == CheatMode.CREATIVE && mc.player.isCreative());
                if (!carried.isEmpty() && hasWirelessTerminal(mc.player) && !emiWouldDelete) {
                    var space = EmiScreenManager.getHoveredSpace((int) mouseX, (int) mouseY);
                    if (space != null) {
                        boolean matchAll = matchesDepositBatchModifier();
                        if (matchAll) {
                            PacketDistributor.sendToServer(new AEDepositPacket(carried.copy(), -1));
                            for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
                                var s = mc.player.getInventory().getItem(i);
                                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, carried)) {
                                    PacketDistributor.sendToServer(new AEDepositPacket(s.copy(), i));
                                }
                            }
                        } else {
                            PacketDistributor.sendToServer(new AEDepositPacket(carried.copy(), -1));
                        }
                        cs.getMenu().setCarried(ItemStack.EMPTY);
                        return true;
                    }
                }
            }
        }

        var itemStack = getItemStack(EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false));
        if (itemStack == null) return false;

        if (button == 2) {
            return handleMiddleClick(itemStack);
        }

        if (button == 0 && matchesExtractModifier()) {
            return doShiftClickExtract(itemStack);
        }

        return false;
    }

    private static boolean tryCtrlLeftQuickCraft(double mouseX, double mouseY) {
        var handled = EmiApi.getHandledScreen();
        if (handled == null) {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen<?> containerScreen) {
                handled = containerScreen;
            }
        }
        if (handled == null || !(handled.getMenu() instanceof CraftingTermMenu)) {
            return false;
        }

        var hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
        if (hovered == null || hovered.isEmpty()) {
            return false;
        }

        EmiRecipe recipe = getRecipeForHovered(hovered);
        if (recipe == null) {
            return false;
        }

        boolean filled = EmiRecipeFiller.performFill(
                recipe,
                handled,
                EmiCraftContext.Type.CRAFTABLE,
                EmiCraftContext.Destination.INVENTORY,
                1);
        org.chatterjay.emiextend.util.ModLogger.info(
                "AE_EMI_CTRL_CRAFT direct-fill recipe={} result={} handled={} menu={}",
                recipe.getId(),
                filled,
                handled.getClass().getName(),
                handled.getMenu().getClass().getName());
        if (filled) {
            return true;
        }

        return tryAeCraftingTransferFallback(recipe, (CraftingTermMenu) handled.getMenu());
    }

    private static EmiRecipe getRecipeForHovered(EmiStackInteraction hovered) {
        EmiRecipe recipe = hovered.getRecipeContext();
        if (recipe == null) {
            recipe = EmiApi.getRecipeContext(hovered.getStack());
        }
        if (recipe != null) {
            return recipe;
        }

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        var stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) {
            return null;
        }
        var recipes = EmiApi.getRecipeManager().getRecipesByOutput(stacks.getFirst());
        return recipes == null || recipes.isEmpty() ? null : recipes.getFirst();
    }

    private static boolean tryAeCraftingTransferFallback(EmiRecipe emiRecipe, CraftingTermMenu menu) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        RecipeHolder<?> holder = emiRecipe.getBackingRecipe();
        if (holder == null && emiRecipe.getId() != null) {
            holder = mc.level.getRecipeManager().byKey(emiRecipe.getId()).orElse(null);
        }
        if (holder == null || !(holder.value() instanceof CraftingRecipe)) {
            org.chatterjay.emiextend.util.ModLogger.info(
                    "AE_EMI_CTRL_CRAFT direct-fill-fallback skip recipe={} reason=not_crafting_recipe holder={}",
                    emiRecipe.getId(),
                    holder == null ? "null" : holder.value().getClass().getName());
            return false;
        }

        CraftingHelper.performTransfer(menu, holder.id(), holder.value(), true);
        org.chatterjay.emiextend.util.ModLogger.info(
                "AE_EMI_CTRL_CRAFT direct-fill-fallback sent recipe={} holder={} menu={}",
                emiRecipe.getId(),
                holder.id(),
                menu.getClass().getName());
        return true;
    }

    public static boolean matchesExtractModifier() {
        return switch (EmiLinkConfig.EXTRACT_MODIFIER.get()) {
            case SHIFT -> Screen.hasShiftDown();
            case CONTROL -> Screen.hasControlDown();
            case ALT -> Screen.hasAltDown();
            case OFF -> false;
        };
    }

    public static boolean matchesDepositBatchModifier() {
        return switch (EmiLinkConfig.DEPOSIT_BATCH_MODIFIER.get()) {
            case SHIFT -> Screen.hasShiftDown();
            case CONTROL -> Screen.hasControlDown();
            case ALT -> Screen.hasAltDown();
            case OFF -> false;
        };
    }

    /**
     * Public entry point for keyboard-triggered extraction (e.g. from InputEvents).
     * Uses EMI's last-known mouse position to resolve the hovered stack.
     */
    public static boolean tryExtractFromHovered() {
        var itemStack = getItemStack(EmiApi.getHoveredStack(
                EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY, false));
        if (itemStack == null) return false;
        return doShiftClickExtract(itemStack);
    }

    /**
     * Extract the first non-empty ItemStack from an EmiStackInteraction, or null.
     */
    @javax.annotation.Nullable
    private static ItemStack getItemStack(EmiStackInteraction hovered) {
        if (hovered == null || hovered.isEmpty()) return null;
        return hovered.getStack().getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
    }

    private static boolean doShiftClickExtract(ItemStack itemStack) {
        var mc = Minecraft.getInstance();
        boolean isBDScreen = mc.screen != null
                && (BDProxy.isBDNetGUI(mc.screen) || BDProxy.isBDCraftGUI(mc.screen));

        if (isBDScreen) {
            if (handleShiftClickBD(itemStack)) return true;
            return handleShiftClickAE2(itemStack);
        } else {
            if (handleShiftClickAE2(itemStack)) return true;
            return handleShiftClickBD(itemStack);
        }
    }

    /**
     * Inject AE network info tooltip. Returns the modified tooltip list.
     */
    public static List<ClientTooltipComponent> addAeTooltipInfo(
            EmiIngredient hovered, int mouseX, int mouseY, List<ClientTooltipComponent> original) {
        if (original == null || original.isEmpty()) return original;

        var space = EmiScreenManager.getHoveredSpace(mouseX, mouseY);
        if (space == null) return original;
        if (!AENetworkCache.hasAEAccess()) return original;

        var stack = hovered.getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) return original;

        var result = new ArrayList<>(original);
        AENetworkCache.addToTooltip(stack, result);
        return result;
    }

    // ---- AE2 / EAEP handlers ----

    private static boolean handleMiddleClick(ItemStack itemStack) {
        var player = Minecraft.getInstance().player;
        if (player == null || !hasWirelessTerminal(player)) return false;
        return EAEPProxy.openCraftScreen(itemStack);
    }

    private static boolean handleShiftClickAE2(ItemStack itemStack) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (!hasWirelessTerminal(player)) return false;
        return EAEPProxy.pullFromNetwork(itemStack);
    }

    // ---- BD handler ----

    private static boolean handleShiftClickBD(ItemStack itemStack) {
        var mc = Minecraft.getInstance();
        var screen = mc.screen;
        if (screen == null) return false;
        if (!BDProxy.isBDNetGUI(screen) && !BDProxy.isBDCraftGUI(screen)) return false;

        var player = mc.player;
        if (player == null) return false;

        BDProxy.pullFromNetwork(itemStack);
        return true;
    }

    public static boolean hasWirelessTerminal(Player player) {
        if (!AE2Proxy.isLoaded()) return false;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (AE2Proxy.isWirelessTerminal(inventory.items.get(i))) {
                return true;
            }
        }
        if (AE2Proxy.isWirelessTerminal(player.getOffhandItem())) {
            return true;
        }
        Class<?> wtClass = AE2Proxy.getWirelessTerminalClass();
        return wtClass != null && CuriosProxy.hasWirelessTerminal(player, wtClass);
    }
}
