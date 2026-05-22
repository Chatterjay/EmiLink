package org.chatterjay.emilink.client.handler;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.integration.EAEPProxy;
import org.chatterjay.emilink.util.ModLogger;

import java.util.List;

public final class EmiInteractionHandler {

    private EmiInteractionHandler() {}

    public static boolean onKeyPressed(int keyCode, int scanCode, int modifiers, int mouseX, int mouseY) {
        ModLogger.info("EmiInteractionHandler: onKeyPressed keyCode={} scanCode={}", keyCode, scanCode);
        var mc = Minecraft.getInstance();
        if (AE2Proxy.isCraftConfirmScreen(mc.screen)) {
            ModLogger.info("EmiInteractionHandler: CraftConfirmScreen detected");
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
            ModLogger.info("EmiInteractionHandler: CraftingCPUScreen detected");
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

    private static boolean isCraftingCPUScreen(Screen screen) {
        if (screen == null) return false;
        try {
            Class<?> clazz = Class.forName("appeng.client.gui.me.crafting.CraftingCPUScreen");
            return clazz.isInstance(screen);
        } catch (Exception e) {
            return false;
        }
    }

    private static EmiStack getGenericStackUnderMouse(Screen screen, int mouseX, int mouseY) {
        try {
            var method = screen.getClass().getMethod("getStackUnderMouse", double.class, double.class);
            Object swb = method.invoke(screen, (double) mouseX, (double) mouseY);
            if (swb == null) return null;

            Object genericStack = swb.getClass().getMethod("stack").invoke(swb);
            if (genericStack == null) return null;

            Object what = genericStack.getClass().getMethod("what").invoke(genericStack);
            if (what == null) return null;

            try {
                Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
                if (aeItemKeyClass.isInstance(what)) {
                    ItemStack itemStack = (ItemStack) aeItemKeyClass.getMethod("toStack").invoke(what);
                    if (!itemStack.isEmpty()) {
                        return EmiStack.of(itemStack);
                    }
                }
            } catch (Exception ignored) {}

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

    public static boolean onMouseReleased(double mouseX, double mouseY, int button) {
        ModLogger.info("EmiInteractionHandler: onMouseReleased button={} at ({},{})", button, mouseX, mouseY);
        if (button != 2 && button != 0) return false;

        EmiStackInteraction hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
        ModLogger.info("EmiInteractionHandler: hovered={} empty={}",
                hovered == null ? "null" : "found",
                hovered == null ? true : hovered.isEmpty());
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
            if (handleShiftClickAE2(itemStack)) return true;
        }

        return false;
    }

    private static boolean handleMiddleClick(ItemStack itemStack) {
        ModLogger.info("EmiInteractionHandler: handleMiddleClick item={}",
                itemStack.getHoverName().getString());
        var player = Minecraft.getInstance().player;
        if (player == null || !hasWirelessTerminal(player)) {
            ModLogger.info("EmiInteractionHandler: handleMiddleClick failed: player={} terminal={}",
                    player == null ? "null" : "ok",
                    player == null ? false : hasWirelessTerminal(player));
            return false;
        }
        return EAEPProxy.openCraftScreen(itemStack);
    }

    private static boolean handleShiftClickAE2(ItemStack itemStack) {
        ModLogger.info("EmiInteractionHandler: handleShiftClickAE2 item={}",
                itemStack.getHoverName().getString());
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (!hasWirelessTerminal(player)) return false;
        return EAEPProxy.pullFromNetwork(itemStack);
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
        return false;
    }
}
