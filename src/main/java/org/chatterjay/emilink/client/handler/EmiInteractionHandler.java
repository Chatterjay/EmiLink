package org.chatterjay.emilink.client.handler;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.config.CheatMode;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
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
import org.chatterjay.emilink.network.packet.c2s.TransferFromContainerPacket;
import org.chatterjay.emilink.util.ModLogger;

public final class EmiInteractionHandler {
    private static final long SINGLE_CRAFT_SIGNAL = Long.MIN_VALUE;

    private EmiInteractionHandler() {}

    public static boolean onKeyPressed(int keyCode, int scanCode, int modifiers, int mouseX, int mouseY) {
        ModLogger.debug("EmiInteractionHandler: onKeyPressed keyCode={} scanCode={}", keyCode, scanCode);

        // Quick fill slot (N key) — check early before other screen checks
        if (ModKeybindings.QUICK_FILL_SLOT_KEY.matches(keyCode, scanCode)) {
            ModLogger.debug("EmiInteractionHandler: QUICK_FILL_SLOT_KEY matched via EmiScreenManager");
            if (InputEvents.handleQuickFillSlot()) {
                ModLogger.debug("EmiInteractionHandler: quickFillSlot succeeded");
                return true;
            }
            ModLogger.debug("EmiInteractionHandler: quickFillSlot returned false");
        }

        var mc = Minecraft.getInstance();
        if (AE2Proxy.isCraftConfirmScreen(mc.screen)) {
            ModLogger.debug("EmiInteractionHandler: CraftConfirmScreen detected");
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
                    ModLogger.debug("EmiInteractionHandler: AE2 terminal key handled via stackInteraction");
                    return true;
                }
            }
        }
        if (isCraftingCPUScreen(mc.screen)) {
            ModLogger.debug("EmiInteractionHandler: CraftingCPUScreen detected");
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
        ModLogger.debug("EmiInteractionHandler: onMouseReleased button={} at ({},{})", button, mouseX, mouseY);
        if (button != 2 && button != 0) return false;

        var mc = Minecraft.getInstance();
        boolean carryingItem = mc.screen instanceof AbstractContainerScreen<?> screen
                && !screen.getMenu().getCarried().isEmpty();
        if (!carryingItem && AE2Proxy.isLoaded() && button == 0 && Screen.hasControlDown() && tryCtrlLeftQuickCraft(mouseX, mouseY)) {
            return true;
        }

        if (button == 0 && Config.ENABLE_SERVER_PACKET_FEATURES.get() && handleAeDepositFromEmiSpace(mc, mouseX, mouseY)) {
            return true;
        }

        EmiStackInteraction hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
        ModLogger.debug("EmiInteractionHandler: hovered={} empty={}",
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

        if (button == 0 && isExtractModifierHeld()) {
            if (handleShiftClickBDEmi(itemStack)) return true;
            if (Config.ENABLE_SERVER_PACKET_FEATURES.get() && handleShiftClickContainer(itemStack)) return true;
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
        ModLogger.debug("EmiInteractionHandler: handleMiddleClick item={}",
                itemStack.getHoverName().getString());
        var mc = Minecraft.getInstance();

        // If we're on an AE2 terminal screen, use our own packet (works for wired + wireless)
        if (mc.screen != null && AE2Proxy.isMEStorageScreen(mc.screen)) {
            return Config.ENABLE_SERVER_PACKET_FEATURES.get() && sendOpenCraftAmountPacket(itemStack);
        }

        // Fallback: wireless terminal in inventory (EAEP wireless-only packet)
        var player = mc.player;
        if (player == null || !hasNetworkAccess(player)) {
            ModLogger.debug("EmiInteractionHandler: handleMiddleClick failed: no terminal access");
            return false;
        }
        return EAEPProxy.openCraftScreen(itemStack);
    }

    private static boolean handleShiftClickAE2(ItemStack itemStack) {
        ModLogger.debug("EmiInteractionHandler: handleShiftClickAE2 item={}",
                itemStack.getHoverName().getString());
        var mc = Minecraft.getInstance();

        // If we're on an AE2 terminal screen, use our own packet (works for wired + wireless)
        if (mc.screen != null && AE2Proxy.isMEStorageScreen(mc.screen)) {
            return Config.ENABLE_SERVER_PACKET_FEATURES.get() && sendPullFromNetworkPacket(itemStack);
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
        ModLogger.debug("EmiInteractionHandler: handleShiftClickBDEmi extracted {}", itemStack.getHoverName().getString());
        return true;
    }

    private static boolean handleShiftClickContainer(ItemStack itemStack) {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return false;
        if (AE2Proxy.isMEStorageScreen(screen) || BDProxy.isBDNetGUI(screen) || BDProxy.isBDCraftGUI(screen)) {
            return false;
        }
        if (!hasMatchingContainerSlot(screen, itemStack)) return false;

        boolean allMatching = switch (Config.getExtractModifier()) {
            case CONTROL -> Screen.hasShiftDown();
            case SHIFT, ALT -> Screen.hasControlDown();
            case OFF -> false;
        };
        boolean sent = NetworkHandler.sendToServer(new TransferFromContainerPacket(itemStack.copy(), allMatching));
        ModLogger.debug("EmiInteractionHandler: container extract item={} all={} sent={}",
                itemStack.getHoverName().getString(), allMatching, sent);
        return sent;
    }

    private static boolean hasMatchingContainerSlot(AbstractContainerScreen<?> screen, ItemStack template) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (slot.container instanceof Inventory) continue;
            if (ItemStack.isSameItemSameTags(slot.getItem(), template)) return true;
        }
        return false;
    }

    private static boolean handleAeDepositFromEmiSpace(Minecraft mc, double mouseX, double mouseY) {
        if (!Config.ENABLE_AE_DEPOSIT.get()) return false;
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)) return false;

        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty() || !hasNetworkAccess(mc.player)) return false;

        boolean wouldDelete = EmiConfig.cheatMode == CheatMode.TRUE
                || (EmiConfig.cheatMode == CheatMode.CREATIVE && mc.player.isCreative());
        if (wouldDelete) return false;

        var space = EmiScreenManager.getHoveredSpace((int) mouseX, (int) mouseY);
        if (space == null) return false;

        boolean batch = isDepositBatchModifierHeld();
        boolean sent = false;
        if (batch) {
            sent |= NetworkHandler.sendToServer(new AEDepositPacket(carried.copy(), -1));
            for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, carried)) {
                    sent |= NetworkHandler.sendToServer(new AEDepositPacket(stack.copy(), i));
                }
            }
        } else {
            sent = NetworkHandler.sendToServer(new AEDepositPacket(carried.copy(), -1));
        }

        if (sent) {
            screen.getMenu().setCarried(ItemStack.EMPTY);
            ModLogger.debug("EmiInteractionHandler: deposited {} from cursor batch={}",
                    carried.getHoverName().getString(), batch);
        }
        return sent;
    }

    public static List<ClientTooltipComponent> addAeTooltipInfo(
            EmiIngredient hovered, int mouseX, int mouseY, List<ClientTooltipComponent> original) {
        if (original == null || original.isEmpty()) return original;

        List<ClientTooltipComponent> result = original;
        var mc = Minecraft.getInstance();
        var space = EmiScreenManager.getHoveredSpace(mouseX, mouseY);
        if (space != null
                && Config.ENABLE_SERVER_PACKET_FEATURES.get()
                && Config.ENABLE_AE_DEPOSIT.get()
                && AE2Proxy.isLoaded()
                && mc.player != null
                && mc.screen instanceof AbstractContainerScreen<?> screen) {
            ItemStack carried = screen.getMenu().getCarried();
            boolean wouldDelete = EmiConfig.cheatMode == CheatMode.TRUE
                    || (EmiConfig.cheatMode == CheatMode.CREATIVE && mc.player.isCreative());
            if (!carried.isEmpty() && hasNetworkAccess(mc.player) && !wouldDelete) {
                if (result == original) {
                    result = new ArrayList<>(original);
                }
                String key = isDepositBatchModifierHeld()
                        ? "emilink.tooltip.deposit_all"
                        : "emilink.tooltip.deposit";
                result.add(EmiTooltipComponents.of(
                        Component.translatable(key).withStyle(ChatFormatting.GRAY)));
            }
        }

        if (space == null || !AE2Proxy.isLoaded() || !AENetworkCache.hasAEAccess()) {
            return result;
        }

        ItemStack stack = hovered.getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) return result;

        if (result == original) {
            result = new ArrayList<>(original);
        }
        AENetworkCache.addToTooltip(stack, result);
        return result;
    }

    private static boolean tryCtrlLeftQuickCraft(double mouseX, double mouseY) {
        var handled = EmiApi.getHandledScreen();
        if (handled == null && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen) {
            handled = screen;
        }
        if (handled == null || !isAeCraftingTermMenu(handled.getMenu())) return false;

        EmiStackInteraction hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
        if (hovered == null || hovered.isEmpty()) return false;

        EmiRecipe recipe = getRecipeForHovered(hovered);
        if (recipe == null) return false;

        boolean craftMax = Screen.hasShiftDown();
        int fillAmount = craftMax ? 64 : 1;
        String actionName = craftMax ? "CRAFT_ALL" : "CRAFT_SHIFT";
        long actionId = craftMax ? 0 : SINGLE_CRAFT_SIGNAL;

        boolean filled = EmiRecipeFiller.performFill(
                recipe,
                handled,
                EmiCraftContext.Type.CRAFTABLE,
                EmiCraftContext.Destination.INVENTORY,
                fillAmount);
        ModLogger.debug("AE_EMI_CTRL_CRAFT direct-fill recipe={} result={} menu={} action={} amount={}",
                recipe.getId(), filled, handled.getMenu().getClass().getName(), actionName, fillAmount);
        if (filled) {
            AEQuickCraftDelayHandler.schedule(handled.getMenu(), actionName, actionId, handled, recipe.getId());
            return true;
        }

        boolean fallbackFilled = tryAeFillPacketFallback(recipe);
        if (fallbackFilled) {
            AEQuickCraftDelayHandler.schedule(handled.getMenu(), actionName, actionId, handled, recipe.getId());
        }
        return fallbackFilled;
    }

    private static EmiRecipe getRecipeForHovered(EmiStackInteraction hovered) {
        EmiRecipe recipe = hovered.getRecipeContext();
        if (recipe != null) return recipe;

        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) return null;
        var stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) return null;

        var recipes = EmiApi.getRecipeManager().getRecipesByOutput(stacks.get(0));
        return recipes == null || recipes.isEmpty() ? null : recipes.get(0);
    }

    private static boolean tryAeFillPacketFallback(EmiRecipe recipe) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        Recipe<?> backingRecipe = recipe.getBackingRecipe();
        ResourceLocation recipeId = recipe.getId();
        if (backingRecipe == null && recipeId != null) {
            backingRecipe = mc.level.getRecipeManager().byKey(recipeId).orElse(null);
        }
        if (backingRecipe == null || !(backingRecipe instanceof CraftingRecipe)) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT fallback skip recipe={} holder={}",
                    recipeId, backingRecipe == null ? "null" : backingRecipe.getClass().getName());
            return false;
        }
        if (recipeId == null) return false;

        NonNullList<ItemStack> templates = NonNullList.create();
        for (var input : recipe.getInputs()) {
            ItemStack stack = input == null || input.isEmpty()
                    ? ItemStack.EMPTY
                    : input.getEmiStacks().stream()
                            .map(EmiStack::getItemStack)
                            .filter(s -> !s.isEmpty())
                            .findFirst()
                            .orElse(ItemStack.EMPTY);
            templates.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }

        if (templates.isEmpty()) return false;
        try {
            Class<?> packetClass = Class.forName("appeng.core.sync.packets.FillCraftingGridFromRecipePacket");
            Object packet = packetClass
                    .getConstructor(ResourceLocation.class, NonNullList.class, boolean.class)
                    .newInstance(recipeId, templates, true);
            sendAe2PacketToServer(packet);
            ModLogger.debug("AE_EMI_CTRL_CRAFT fallback-fill sent recipe={} templates={}",
                    recipeId, templates.size());
            return true;
        } catch (Throwable t) {
            ModLogger.warn("AE_EMI_CTRL_CRAFT fallback-fill failed: {}", t.toString());
            return false;
        }
    }

    private static void sendAe2PacketToServer(Object packet) throws ReflectiveOperationException {
        Class<?> networkHandlerClass = Class.forName("appeng.core.sync.network.NetworkHandler");
        Object handler = networkHandlerClass.getMethod("instance").invoke(null);
        Class<?> basePacketClass = Class.forName("appeng.core.sync.BasePacket");
        networkHandlerClass.getMethod("sendToServer", basePacketClass).invoke(handler, packet);
    }

    private static boolean isAeCraftingTermMenu(Object menu) {
        if (menu == null || !AE2Proxy.isLoaded()) return false;
        try {
            return Class.forName("appeng.menu.me.items.CraftingTermMenu").isInstance(menu);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean sendOpenCraftAmountPacket(ItemStack itemStack) {
        try {
            Class<?> packetClass = Class.forName("org.chatterjay.emilink.network.packet.c2s.OpenCraftAmountC2SPacket");
            var packetCtor = packetClass.getConstructor(ItemStack.class);
            Object packet = packetCtor.newInstance(itemStack.copy());

            Class.forName("org.chatterjay.emilink.network.NetworkHandler")
                    .getMethod("sendToServer", Object.class)
                    .invoke(null, packet);

            ModLogger.debug("EmiInteractionHandler: sent OpenCraftAmount packet");
            return true;
        } catch (Exception e) {
            ModLogger.debug("EmiInteractionHandler: sendOpenCraftAmountPacket error: {}: {}",
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

            ModLogger.debug("EmiInteractionHandler: sent PullFromNetwork packet");
            return true;
        } catch (Exception e) {
            ModLogger.debug("EmiInteractionHandler: sendPullFromNetworkPacket error: {}: {}",
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
                ModLogger.debug("EmiInteractionHandler: found wireless terminal in inventory slot {}", i);
                return true;
            }
        }
        if (AE2Proxy.isWirelessTerminal(player.getOffhandItem())) {
            ModLogger.debug("EmiInteractionHandler: found wireless terminal in offhand");
            return true;
        }
        // Curios bauble slot
        Class<?> wtClass = AE2Proxy.getWirelessTerminalClass();
        if (wtClass != null && CuriosProxy.hasWirelessTerminal(player, wtClass)) {
            ModLogger.debug("EmiInteractionHandler: found wireless terminal in curio slot");
            return true;
        }
        // Wired AE2 terminal screen open (MEStorageScreen covers all terminal types)
        var screen = Minecraft.getInstance().screen;
        if (screen != null && AE2Proxy.isMEStorageScreen(screen)) {
            ModLogger.debug("EmiInteractionHandler: found wired terminal (screen open)");
            return true;
        }
        ModLogger.debug("EmiInteractionHandler: no network access found");
        return false;
    }
}
