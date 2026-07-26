package org.chatterjay.emilink.client.handler;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.config.CheatMode;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.client.InputEvents;
import org.chatterjay.emilink.client.ModKeybindings;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.integration.BDProxy;
import org.chatterjay.emilink.integration.CuriosProxy;
import org.chatterjay.emilink.integration.EAEPProxy;
import org.chatterjay.emilink.network.NetworkHandler;
import org.chatterjay.emilink.network.packet.c2s.AEDepositPacket;
import org.chatterjay.emilink.util.ModLogger;

public final class EmiInteractionHandler {

    private EmiInteractionHandler() {}

    public static boolean onKeyPressed(int keyCode, int scanCode, int modifiers, int mouseX, int mouseY) {
        ModLogger.info("EmiInteractionHandler: onKeyPressed keyCode={} scanCode={}", keyCode, scanCode);

        // Quick fill slot (N key) — check early before other screen checks
        if (ModKeybindings.QUICK_FILL_SLOT_KEY.matches(keyCode, scanCode)) {
            ModLogger.info("EmiInteractionHandler: QUICK_FILL_SLOT_KEY matched via EmiScreenManager");
            if (InputEvents.handleQuickFillSlot()) {
                ModLogger.info("EmiInteractionHandler: quickFillSlot succeeded");
                return true;
            }
            ModLogger.info("EmiInteractionHandler: quickFillSlot returned false");
        }

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
        // AE2 terminal screen: forward unhandled key to EMI stack interaction
        if (AE2Proxy.isMEStorageScreen(mc.screen)) {
            EmiStackInteraction hovered = EmiApi.getHoveredStack(mouseX, mouseY, false);
            if (hovered != null && !hovered.isEmpty()) {
                if (EmiScreenManager.stackInteraction(hovered, bind -> bind.matchesKey(keyCode, scanCode))) {
                    ModLogger.info("EmiInteractionHandler: AE2 terminal key handled via stackInteraction");
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

        // Deposit: cursor item → AE network (matching 1.21.1 behavior)
        if (button == 0 && Config.ENABLE_AE_DEPOSIT.get()) {
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen instanceof AbstractContainerScreen<?> cs) {
                var carried = cs.getMenu().getCarried();
                if (!carried.isEmpty() && hasNetworkAccess(mc.player)) {
                    boolean wouldDelete = EmiConfig.cheatMode == CheatMode.TRUE
                            || (EmiConfig.cheatMode == CheatMode.CREATIVE && mc.player.isCreative());
                    if (!wouldDelete) {
                        var space = EmiScreenManager.getHoveredSpace((int) mouseX, (int) mouseY);
                        if (space != null) {
                            boolean batch = isDepositBatchModifierHeld();
                            if (batch) {
                                NetworkHandler.sendToServer(new AEDepositPacket(carried.copy(), -1));
                                for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
                                    var s = mc.player.getInventory().getItem(i);
                                    if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, carried)) {
                                        NetworkHandler.sendToServer(new AEDepositPacket(s.copy(), i));
                                    }
                                }
                            } else {
                                NetworkHandler.sendToServer(new AEDepositPacket(carried.copy(), -1));
                            }
                            cs.getMenu().setCarried(ItemStack.EMPTY);
                            ModLogger.info("EmiInteractionHandler: deposited {} from cursor", carried.getHoverName().getString());
                            return true;
                        }
                    }
                }
            }
        }

        if (button == 2) {
            return handleMiddleClick(itemStack);
        }

        if (button == 0 && isExtractModifierHeld()) {
            if (handleShiftClickBDEmi(itemStack)) return true;
            return handleShiftClickAE2(itemStack);
        }

        return false;
    }

    private static boolean isExtractModifierHeld() {
        return switch (Config.getExtractModifier()) {
            case SHIFT -> Screen.hasShiftDown();
            case CONTROL -> Screen.hasControlDown();
            case ALT -> Screen.hasAltDown();
            case OFF -> false;
        };
    }

    private static boolean isDepositBatchModifierHeld() {
        return switch (Config.getDepositBatchModifier()) {
            case SHIFT -> Screen.hasShiftDown();
            case CONTROL -> Screen.hasControlDown();
            case ALT -> Screen.hasAltDown();
            case OFF -> false;
        };
    }

    private static boolean handleMiddleClick(ItemStack itemStack) {
        ModLogger.info("EmiInteractionHandler: handleMiddleClick item={}",
                itemStack.getHoverName().getString());
        var mc = Minecraft.getInstance();

        // If we're on an AE2 terminal screen, use our own packet (works for wired + wireless)
        if (mc.screen != null && AE2Proxy.isMEStorageScreen(mc.screen)) {
            return sendOpenCraftAmountPacket(itemStack);
        }

        // Fallback: wireless terminal in inventory (EAEP wireless-only packet)
        var player = mc.player;
        if (player == null || !hasNetworkAccess(player)) {
            ModLogger.info("EmiInteractionHandler: handleMiddleClick failed: no terminal access");
            return false;
        }
        return EAEPProxy.openCraftScreen(itemStack);
    }

    private static boolean handleShiftClickAE2(ItemStack itemStack) {
        ModLogger.info("EmiInteractionHandler: handleShiftClickAE2 item={}",
                itemStack.getHoverName().getString());
        var mc = Minecraft.getInstance();

        // If we're on an AE2 terminal screen, use our own packet (works for wired + wireless)
        if (mc.screen != null && AE2Proxy.isMEStorageScreen(mc.screen)) {
            return sendPullFromNetworkPacket(itemStack);
        }

        // Fallback: wireless terminal in inventory
        var player = mc.player;
        if (player == null) return false;
        if (!hasNetworkAccess(player)) return false;
        return EAEPProxy.pullFromNetwork(itemStack);
    }

    private static boolean handleShiftClickBDEmi(ItemStack itemStack) {
        var mc = Minecraft.getInstance();
        var screen = mc.screen;
        if (screen == null) return false;
        if (!BDProxy.isBDNetGUI(screen) && !BDProxy.isBDCraftGUI(screen)) return false;
        ItemStack sendStack = itemStack.copy();
        sendStack.setCount(1);
        if (org.chatterjay.emilink.network.packet.s2c.ServerHasModPacket.serverHasMod) {
            BDProxy.pullFromNetwork(sendStack);
        } else {
            BDProxy.clientExtract(sendStack);
        }
        ModLogger.info("EmiInteractionHandler: handleShiftClickBDEmi extracted {}", itemStack.getHoverName().getString());
        return true;
    }

    private static boolean sendOpenCraftAmountPacket(ItemStack itemStack) {
        try {
            Class<?> packetClass = Class.forName("org.chatterjay.emilink.network.packet.c2s.OpenCraftAmountC2SPacket");
            var packetCtor = packetClass.getConstructor(ItemStack.class);
            Object packet = packetCtor.newInstance(itemStack.copy());

            Class.forName("org.chatterjay.emilink.network.NetworkHandler")
                    .getMethod("sendToServer", Object.class)
                    .invoke(null, packet);

            ModLogger.info("EmiInteractionHandler: sent OpenCraftAmount packet");
            return true;
        } catch (Exception e) {
            ModLogger.info("EmiInteractionHandler: sendOpenCraftAmountPacket error: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private static boolean sendPullFromNetworkPacket(ItemStack itemStack) {
        try {
            Class<?> packetClass = Class.forName("org.chatterjay.emilink.network.packet.c2s.PullFromNetworkC2SPacket");
            var packetCtor = packetClass.getConstructor(ItemStack.class);
            Object packet = packetCtor.newInstance(itemStack.copy());

            Class.forName("org.chatterjay.emilink.network.NetworkHandler")
                    .getMethod("sendToServer", Object.class)
                    .invoke(null, packet);

            ModLogger.info("EmiInteractionHandler: sent PullFromNetwork packet");
            return true;
        } catch (Exception e) {
            ModLogger.info("EmiInteractionHandler: sendPullFromNetworkPacket error: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private static boolean hasNetworkAccess(Player player) {
        if (!AE2Proxy.isLoaded()) return false;
        // Wireless terminal in inventory
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (AE2Proxy.isWirelessTerminal(inventory.items.get(i))) {
                ModLogger.info("EmiInteractionHandler: found wireless terminal in inventory slot {}", i);
                return true;
            }
        }
        if (AE2Proxy.isWirelessTerminal(player.getOffhandItem())) {
            ModLogger.info("EmiInteractionHandler: found wireless terminal in offhand");
            return true;
        }
        // Curios bauble slot
        Class<?> wtClass = AE2Proxy.getWirelessTerminalClass();
        if (wtClass != null && CuriosProxy.hasWirelessTerminal(player, wtClass)) {
            ModLogger.info("EmiInteractionHandler: found wireless terminal in curio slot");
            return true;
        }
        // Wired AE2 terminal screen open (MEStorageScreen covers all terminal types)
        var screen = Minecraft.getInstance().screen;
        if (screen != null && AE2Proxy.isMEStorageScreen(screen)) {
            ModLogger.info("EmiInteractionHandler: found wired terminal (screen open)");
            return true;
        }
        ModLogger.info("EmiInteractionHandler: no network access found");
        return false;
    }
}
