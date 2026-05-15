package org.chatterjay.emiextend.client.handler;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

        EmiStackInteraction hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
        if (hovered == null || hovered.isEmpty()) return false;

        var itemStack = hovered.getStack().getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
        if (itemStack == null) return false;

        if (button == 2) {
            return handleMiddleClick(itemStack);
        }

        if (button == 0 && Screen.hasShiftDown()) {
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

        return false;
    }

    /**
     * Inject AE network info tooltip. Returns the modified tooltip list.
     */
    public static List<ClientTooltipComponent> addAeTooltipInfo(
            EmiIngredient hovered, int mouseX, int mouseY, List<ClientTooltipComponent> original) {
        if (original == null || original.isEmpty()) return original;

        var space = EmiScreenManager.getHoveredSpace(mouseX, mouseY);
        if (space == null) return original;

        var tooltip = new ArrayList<ClientTooltipComponent>(original);
        if (!hovered.isEmpty()) {
            var stack = hovered.getEmiStacks().stream()
                    .map(EmiStack::getItemStack)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse(ItemStack.EMPTY);
            if (!stack.isEmpty() && AENetworkCache.hasAEAccess()) {
                AENetworkCache.addToTooltip(stack, tooltip);
            }
        }
        return tooltip;
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

    private static boolean hasWirelessTerminal(Player player) {
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
