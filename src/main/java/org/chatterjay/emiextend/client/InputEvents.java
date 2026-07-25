package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.client.bookmark.BomTreePageHelper;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.util.IPNProxy;
import org.chatterjay.emiextend.util.ModLogger;

import appeng.api.stacks.GenericStack;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.menu.slot.FakeSlot;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.network.packet.c2s.BDDepositSlotPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEDepositPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEBatchQueryPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEAutocraftRequestPacket;
import org.lwjgl.glfw.GLFW;

public final class InputEvents {
    private InputEvents() {}

    private static final String CRAFTING_TRACKER_LOCATOR_SCREEN =
            "org.chatterjay.crafting_tracker.client.screen.NetworkLocatorScreen";
    private static final String CRAFTING_TRACKER_APPLY_GHOST_FILTER = "applyGhostFilter";
    private static final String CRAFTING_TRACKER_IS_FILTER_SLOT = "isFilterSlot";
    private static final int CRAFTING_TRACKER_FALLBACK_FILTER_SLOTS = 9;

    private static boolean fillSearchHandled = false;

    /** Active multi-tick crafting job (one batch per game tick to avoid freezing). */
    private static CraftingJob currentJob = null;

    /** Tracks successfully crafted batches per recipe ID across job restarts. */
    private static final java.util.Map<String, Long> completedBatches = new java.util.HashMap<>();
    private static String lastGoalRecipeId = null;
    private static long lastBoMBatches = -1;
    private static String lastPlanSignature = null;

    /** Preflight: batch-query AE cache for all node outputs before starting the job. */
    private static java.util.List<MaterialNode> preflightNodes;
    private static MaterialNode preflightGoalNode;
    private static java.util.Map<MaterialNode, Long> preflightNeededAmounts;
    private static AbstractContainerScreen<?> preflightScreen;
    private static QuickCraftStorageMode preflightStorageMode = QuickCraftStorageMode.LOCAL_INVENTORY;
    private static long preflightStartTime;
    private static long preflightQueryStartedAt;
    private static long preflightRunId;
    private static long quickCraftRunSeq = 0;
    private static final long PREFLIGHT_TIMEOUT_MS = 3000;

    private enum QuickCraftStorageMode {
        AE_EXTERNAL,
        BD_EXTERNAL,
        LOCAL_INVENTORY,
        UNSAFE_EXTERNAL_GRID
    }

    private static class CraftingJob {
        final AbstractContainerScreen<?> screen;
        final MaterialNode goalNode;
        final java.util.List<MaterialNode> nodes;
        final java.util.Map<MaterialNode, Long> neededAmounts;
        final long runId;
        final long goalOutputDeduction;
        final QuickCraftStorageMode storageMode;
        int nodeIndex;
        long remainingBatches;
        long nodeSucceeded;
        long nodeFailed;
        int consecutiveFails;
        boolean hadFailures;
        MaterialNode pendingBdCraftNode;
        int pendingBdCraftTicks;
        long pendingBdCraftBeforeTotal;
        MaterialNode pendingVerifyNode;
        int pendingVerifyTicks;
        long pendingVerifyBeforeTotal;
        MaterialNode pendingDepositNode;
        int pendingDepositTicks;

        CraftingJob(AbstractContainerScreen<?> screen, MaterialNode goalNode,
                    java.util.List<MaterialNode> nodes, java.util.Map<MaterialNode, Long> neededAmounts,
                    long runId, long goalOutputDeduction, QuickCraftStorageMode storageMode) {
            this.screen = screen;
            this.goalNode = goalNode;
            this.nodes = nodes;
            this.neededAmounts = neededAmounts;
            this.runId = runId;
            this.goalOutputDeduction = goalOutputDeduction;
            this.storageMode = storageMode;
            this.nodeIndex = 0;
            this.remainingBatches = 0;
            this.nodeSucceeded = 0;
            this.nodeFailed = 0;
            this.consecutiveFails = 0;
            this.hadFailures = false;
            this.pendingBdCraftNode = null;
            this.pendingBdCraftTicks = 0;
            this.pendingBdCraftBeforeTotal = 0;
            this.pendingVerifyNode = null;
            this.pendingVerifyTicks = 0;
            this.pendingVerifyBeforeTotal = 0;
            this.pendingDepositNode = null;
            this.pendingDepositTicks = 0;
        }
    }

    private record OutputAvailability(ItemStack stack, long ae, long inventory, long carried) {
        long total() {
            return ae + inventory + carried;
        }
    }

    private record NodeAvailability(long ae, long inventory, long carried, long total, String detail) {}

    private record AeCraftableShortage(MaterialNode node, ItemStack stack, long needed, long available, int missing) {}

    private record AeInputNeed(ItemStack stack, long needed) {}

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
        boolean quickCraftKey = EmiLinkConfig.ENABLE_QUICK_CRAFT_TAB.get()
                && matchesConfiguredQuickCraftKey(keyCode);

        if (ModLogger.isDebugEnabled() && (keyCode == GLFW.GLFW_KEY_A || quickCraftKey)) {
            logBomKeyPress("pre", keyCode, scanCode, quickCraftKey);
        }

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
            return;
        }

        if (EmiLinkConfig.ENABLE_DISCARD_MATCHING_KEY.get()
                && matchesDiscardMatchingKeyCombo(keyCode, scanCode)) {
            tryHandleDiscardMatchingKey(keyCode, scanCode);
            event.setCanceled(true);
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_A) {
            boolean canceled = tryCancelHoveredFinalBomTree();
            ModLogger.debug("BOM_KEY A result canceled={} {}", canceled, BomTreePageHelper.describeActiveState());
            if (canceled) {
                event.setCanceled(true);
            }
            return;
        }

        if (quickCraftKey) {
            EmiFavorite.Synthetic synthetic = getHoveredFinalSynthetic();
            ModLogger.debug("BOM_KEY quick-craft matched bind={} finalSynthetic={} synthetic={} {}",
                    quickCraftBindText(),
                    synthetic != null,
                    synthetic == null ? "null" : BomTreePageHelper.describeSyntheticDebug(synthetic),
                    BomTreePageHelper.describeActiveState());
            event.setCanceled(true);
            if (synthetic == null) {
                var mouse = getCurrentGuiMousePosition();
                ModLogger.debug("BOM_KEY quick-craft ignored: hovered target is not a final synthetic preciseFavorite={}",
                        describePreciseFavoriteHit((int) mouse.x(), (int) mouse.y()));
                return;
            }
            onQuickCraftTabKey(event);
        }
    }

    public static boolean tryHandleBomKeyFromEmi(int keyCode, int scanCode) {
        boolean quickCraftKey = EmiLinkConfig.ENABLE_QUICK_CRAFT_TAB.get()
                && matchesConfiguredQuickCraftKey(keyCode);

        if (keyCode != GLFW.GLFW_KEY_A && !quickCraftKey) {
            return false;
        }

        if (ModLogger.isDebugEnabled()) {
            logBomKeyPress("emi-head", keyCode, scanCode, quickCraftKey);
        }

        if (keyCode == GLFW.GLFW_KEY_A) {
            boolean canceled = tryCancelHoveredFinalBomTree();
            ModLogger.debug("BOM_KEY A emi result canceled={} {}", canceled, BomTreePageHelper.describeActiveState());
            return canceled;
        }

        EmiFavorite.Synthetic synthetic = getHoveredFinalSynthetic();
        ModLogger.debug("BOM_KEY quick-craft emi matched bind={} finalSynthetic={} synthetic={} {}",
                quickCraftBindText(),
                synthetic != null,
                synthetic == null ? "null" : BomTreePageHelper.describeSyntheticDebug(synthetic),
                BomTreePageHelper.describeActiveState());
        if (synthetic == null) {
            var mouse = getCurrentGuiMousePosition();
            ModLogger.debug("BOM_KEY quick-craft emi ignored: hovered target is not a final synthetic preciseFavorite={}",
                    describePreciseFavoriteHit((int) mouse.x(), (int) mouse.y()));
            return false;
        }
        onQuickCraftTabKey(null);
        return true;
    }

    @SubscribeEvent
    public static void onMouseButtonPressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !Screen.hasControlDown()) return;

        if (ModLogger.isDebugEnabled()) {
            var mouse = getCurrentGuiMousePosition();
            logAeCtrlLeftClick("screen-press-pre", event.getScreen(), mouse.x(), mouse.y(), event.getButton());
        }
    }

    public static void logAeCtrlLeftClick(String phase, Screen screen, double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !Screen.hasControlDown()) return;

        try {
            var mc = Minecraft.getInstance();
            var handled = EmiApi.getHandledScreen();
            var hovered = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
            var recipe = hovered == null ? null : hovered.getRecipeContext();
            var space = EmiScreenManager.getHoveredSpace((int) mouseX, (int) mouseY);

            String slotInfo = "none";
            String carriedInfo = "none";
            String menuInfo = "none";
            if (screen instanceof AbstractContainerScreen<?> cs) {
                var slot = cs.getSlotUnderMouse();
                if (slot != null) {
                    slotInfo = "index=" + slot.index
                            + ",containerSlot=" + slot.getContainerSlot()
                            + ",container=" + safeClassName(slot.container)
                            + ",hasItem=" + slot.hasItem()
                            + ",item=" + formatItemStack(slot.getItem());
                }
                carriedInfo = formatItemStack(cs.getMenu().getCarried());
                menuInfo = safeClassName(cs.getMenu()) + "#" + cs.getMenu().containerId;
            }

            GuiEventListener focused = screen == null ? null : screen.getFocused();
            ModLogger.debug(
                    "AE_EMI_CTRL_CRAFT mouse-click phase={} button={} ctrl={} shift={} alt={} xy=({}, {}) screen={} handled={} focus={} menu={} slot={} carried={} emiHovered={} emiRecipe={} emiSpace={}",
                    phase,
                    button,
                    Screen.hasControlDown(),
                    Screen.hasShiftDown(),
                    Screen.hasAltDown(),
                    (int) mouseX,
                    (int) mouseY,
                    safeClassName(screen),
                    safeClassName(handled),
                    safeClassName(focused),
                    menuInfo,
                    slotInfo,
                    carriedInfo,
                    formatHoveredStack(hovered),
                    recipe == null ? "none" : recipe.getId(),
                    safeClassName(space));
        } catch (Throwable t) {
            ModLogger.warn("AE_EMI_CTRL_CRAFT mouse-click-log-error phase={} error={}: {}",
                    phase, t.getClass().getName(), t.getMessage());
        }
    }

    private static void logBomKeyPress(String phase, int keyCode, int scanCode, boolean quickCraftKey) {
        try {
            var mc = Minecraft.getInstance();
            var mouse = getCurrentGuiMousePosition();
            var hoveredScreen = EmiScreenManager.getHoveredStack((int) mouse.x(), (int) mouse.y(), false);
            var hoveredApi = EmiApi.getHoveredStack((int) mouse.x(), (int) mouse.y(), false);
            var hoveredLast = EmiScreenManager.getHoveredStack(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY, false);
            ModLogger.debug(
                    "BOM_KEY {} keyCode={} scanCode={} keyName={} bind={} quickCraftKey={} ctrl={} shift={} alt={} xy=({}, {}) lastXY=({}, {}) screen={} handled={} focus={} hoveredScreen={} hoveredApi={} hoveredLast={} bomScreenHover={} {}",
                    phase,
                    keyCode,
                    scanCode,
                    keyName(keyCode),
                    quickCraftBindText(),
                    quickCraftKey,
                    Screen.hasControlDown(),
                    Screen.hasShiftDown(),
                    Screen.hasAltDown(),
                    (int) mouse.x(),
                    (int) mouse.y(),
                    EmiScreenManager.lastMouseX,
                    EmiScreenManager.lastMouseY,
                    safeClassName(mc.screen),
                    safeClassName(EmiApi.getHandledScreen()),
                    mc.screen == null ? "none" : safeClassName(mc.screen.getFocused()),
                    describeHoveredSynthetic(hoveredScreen),
                    describeHoveredSynthetic(hoveredApi),
                    describeHoveredSynthetic(hoveredLast),
                    describeBomScreenHover(mc.screen, mouse.x(), mouse.y()),
                    BomTreePageHelper.describeActiveState());
        } catch (Throwable t) {
            ModLogger.warn("BOM_KEY log-error phase={} error={}: {}",
                    phase, t.getClass().getName(), t.getMessage());
        }
    }

    private static String keyName(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return Character.toString((char) ('A' + keyCode - GLFW.GLFW_KEY_A));
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return Character.toString((char) ('0' + keyCode - GLFW.GLFW_KEY_0));
        }
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
            return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            default -> Integer.toString(keyCode);
        };
    }

    private static String describeHoveredSynthetic(dev.emi.emi.api.stack.EmiStackInteraction hovered) {
        if (hovered == null || hovered.isEmpty()) {
            return formatHoveredStack(hovered);
        }
        String base = formatHoveredStack(hovered);
        if (hovered.getStack() instanceof EmiFavorite.Synthetic synthetic) {
            return base + " synthetic={" + BomTreePageHelper.describeSyntheticDebug(synthetic) + "}";
        }
        return base + " stackType=" + safeClassName(hovered.getStack());
    }

    private static String describeBomScreenHover(Screen screen, double mouseX, double mouseY) {
        if (!(screen instanceof dev.emi.emi.screen.BoMScreen)) {
            return "not-bom-screen";
        }
        Object hover = getBomScreenHoverObject(screen, mouseX, mouseY);
        if (hover == null) {
            return "none";
        }
        if (hover instanceof String text) {
            return text;
        }
        try {
            Object stack = getDeclaredFieldValue(hover, "stack");
            Object node = getDeclaredFieldValue(hover, "node");
            Object resolve = getDeclaredFieldValue(hover, "resolve");
            return "hoverClass=" + hover.getClass().getName()
                    + ",stack=" + describeIngredientObject(stack)
                    + ",node=" + describeMaterialNodeObject(node)
                    + ",resolve=" + describeMaterialNodeObject(resolve)
                    + ",isGoal=" + isGoalNodeObject(node);
        } catch (Throwable t) {
            return hover.getClass().getName() + ":describe-error:" + t.getClass().getSimpleName();
        }
    }

    private static Object getBomScreenHoverObject(Screen screen, double mouseX, double mouseY) {
        try {
            var method = screen.getClass().getDeclaredMethod("getHoveredStack", int.class, int.class);
            method.setAccessible(true);
            return method.invoke(screen, (int) mouseX, (int) mouseY);
        } catch (Throwable t) {
            return "method-missing:" + t.getClass().getSimpleName();
        }
    }

    private static Object getDeclaredFieldValue(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static String describeIngredientObject(Object value) {
        if (!(value instanceof EmiIngredient ingredient) || ingredient.isEmpty()) {
            return "none";
        }
        var stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) {
            return "empty";
        }
        var first = stacks.getFirst();
        return first.getId() + "/" + first.getName().getString();
    }

    private static String describeMaterialNodeObject(Object value) {
        if (!(value instanceof MaterialNode node)) {
            return "none";
        }
        return "recipe=" + recipeId(node)
                + ",amount=" + node.amount
                + ",neededBatches=" + node.neededBatches
                + ",totalNeeded=" + node.totalNeeded
                + ",isGoal=" + isGoalNodeObject(node);
    }

    private static boolean isGoalNodeObject(Object value) {
        if (!(value instanceof MaterialNode node)) {
            return false;
        }
        if (BoM.tree == null || BoM.tree.goal == null) {
            return false;
        }
        if (node == BoM.tree.goal) {
            return true;
        }
        return node.recipe != null
                && BoM.tree.goal.recipe != null
                && node.recipe.getId() != null
                && node.recipe.getId().equals(BoM.tree.goal.recipe.getId());
    }

    private record GuiMousePosition(double x, double y) {}

    private static GuiMousePosition getCurrentGuiMousePosition() {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        double x = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double y = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        return new GuiMousePosition(x, y);
    }

    private static String formatHoveredStack(dev.emi.emi.api.stack.EmiStackInteraction hovered) {
        if (hovered == null || hovered.isEmpty()) return "none";
        var ingredient = hovered.getStack();
        if (ingredient == null || ingredient.isEmpty()) return "empty";
        var stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) return "empty-stacks";
        var first = stacks.getFirst();
        var itemStack = first.getItemStack();
        return "id=" + first.getId()
                + ",name=" + first.getName().getString()
                + ",item=" + formatItemStack(itemStack);
    }

    private static String formatItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        return stack.getCount()
                + "x"
                + BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "("
                + stack.getDisplayName().getString()
                + ")";
    }

    private static String safeClassName(Object value) {
        return value == null ? "none" : value.getClass().getName();
    }

    public static boolean tryHandleDiscardMatchingKey(int keyCode, int scanCode) {
        if (!EmiLinkConfig.ENABLE_DISCARD_MATCHING_KEY.get()) return false;
        if (!matchesDiscardMatchingKeyCombo(keyCode, scanCode)) return false;

        BDShortcutHandler.tryBatchDropMatchingFromCurrentScreen();
        return true;
    }

    private static boolean matchesDiscardMatchingKeyCombo(int keyCode, int scanCode) {
        var mc = Minecraft.getInstance();
        return mc.options.keyDrop.matches(keyCode, scanCode)
                && Screen.hasControlDown()
                && Screen.hasShiftDown()
                && !Screen.hasAltDown();
    }

    private static boolean matchesConfiguredQuickCraftKey(int keyCode) {
        int configuredKey = parseKeyToken(EmiLinkConfig.QUICK_CRAFT_KEY.get());
        if (configuredKey < 0) {
            configuredKey = GLFW.GLFW_KEY_C;
        }
        return keyCode == configuredKey && matchesModifier(EmiLinkConfig.QUICK_CRAFT_MODIFIER.get());
    }

    private static boolean matchesModifier(EmiLinkConfig.ExtractTrigger modifier) {
        return switch (modifier) {
            case SHIFT -> Screen.hasShiftDown() && !Screen.hasControlDown() && !Screen.hasAltDown();
            case CONTROL -> Screen.hasControlDown() && !Screen.hasShiftDown() && !Screen.hasAltDown();
            case ALT -> Screen.hasAltDown() && !Screen.hasControlDown() && !Screen.hasShiftDown();
            case OFF -> !Screen.hasControlDown() && !Screen.hasShiftDown() && !Screen.hasAltDown();
        };
    }

    public static String quickCraftBindText() {
        String key = normalizeKeyToken(EmiLinkConfig.QUICK_CRAFT_KEY.get());
        if (key == null) {
            key = "C";
        }
        return switch (EmiLinkConfig.QUICK_CRAFT_MODIFIER.get()) {
            case SHIFT -> "Shift+" + key;
            case CONTROL -> "Ctrl+" + key;
            case ALT -> "Alt+" + key;
            case OFF -> key;
        };
    }

    private static String normalizeKeyToken(String value) {
        if (value == null || value.isBlank()) return null;
        String token = value.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "_");
        return parseKeyToken(token) < 0 ? null : token;
    }

    private static int parseKeyToken(String value) {
        if (value == null || value.isBlank()) return -1;
        String token = value.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "_");
        if (token.length() == 1) {
            char c = token.charAt(0);
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }
        if (token.startsWith("F")) {
            try {
                int fn = Integer.parseInt(token.substring(1));
                if (fn >= 1 && fn <= 25) return GLFW.GLFW_KEY_F1 + (fn - 1);
            } catch (NumberFormatException ignored) {}
        }
        return switch (token) {
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
            case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
            case "DELETE", "DEL" -> GLFW.GLFW_KEY_DELETE;
            case "INSERT", "INS" -> GLFW.GLFW_KEY_INSERT;
            case "HOME" -> GLFW.GLFW_KEY_HOME;
            case "END" -> GLFW.GLFW_KEY_END;
            case "PAGE_UP", "PAGEUP" -> GLFW.GLFW_KEY_PAGE_UP;
            case "PAGE_DOWN", "PAGEDOWN" -> GLFW.GLFW_KEY_PAGE_DOWN;
            case "LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            case "UP" -> GLFW.GLFW_KEY_UP;
            case "DOWN" -> GLFW.GLFW_KEY_DOWN;
            default -> -1;
        };
    }

    private static boolean tryCancelHoveredFinalBomTree() {
        EmiFavorite.Synthetic synthetic = getHoveredFinalSynthetic();
        if (synthetic == null) {
            boolean removedBomGoal = tryCancelHoveredBomScreenGoalTree();
            if (removedBomGoal) {
                return true;
            }
            var mouse = getCurrentGuiMousePosition();
            var hovered = EmiScreenManager.getHoveredStack((int) mouse.x(), (int) mouse.y(), false);
            ModLogger.debug("BOM_TREE_PAGE A cancel miss hovered={} textFocused={} bomHover={} preciseFavorite={} {}",
                    formatHoveredStack(hovered),
                    isTextInputFocused(),
                    describeBomScreenHover(Minecraft.getInstance().screen, mouse.x(), mouse.y()),
                    describePreciseFavoriteHit((int) mouse.x(), (int) mouse.y()),
                    BomTreePageHelper.describeActiveState());
            return false;
        }
        boolean removed = BomTreePageHelper.removeFinalSyntheticTree(synthetic);
        ModLogger.debug("BOM_TREE_PAGE A cancel synthetic={} removed={}",
                BomTreePageHelper.describeSyntheticDebug(synthetic), removed);
        if (removed) {
            abortCurrentJob("BoM tree canceled by A");
            clearPreflightState();
            ModLogger.debug("BOM_TREE_PAGE canceled hovered final tree with A");
        }
        return removed;
    }

    private static boolean tryCancelHoveredBomScreenGoalTree() {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof dev.emi.emi.screen.BoMScreen)) {
            return false;
        }
        var mouse = getCurrentGuiMousePosition();
        Object hover = getBomScreenHoverObject(mc.screen, mouse.x(), mouse.y());
        Object node = null;
        try {
            if (hover != null && !(hover instanceof String)) {
                node = getDeclaredFieldValue(hover, "node");
            }
        } catch (Throwable ignored) {}
        if (!isGoalNodeObject(node)) {
            ModLogger.debug("BOM_TREE_PAGE A bom-screen goal miss hover={} {}",
                    describeBomScreenHover(mc.screen, mouse.x(), mouse.y()),
                    BomTreePageHelper.describeActiveState());
            return false;
        }
        String goalRecipeId = BoM.tree == null || BoM.tree.goal == null ? "(unknown)" : recipeId(BoM.tree.goal);
        boolean removed = BomTreePageHelper.removeTreeForRecipeId(goalRecipeId);
        if (removed) {
            abortCurrentJob("BoM tree canceled by A on BoMScreen goal");
            clearPreflightState();
        }
        ModLogger.debug("BOM_TREE_PAGE A bom-screen goal cancel recipe={} removed={} hover={} {}",
                goalRecipeId,
                removed,
                describeBomScreenHover(mc.screen, mouse.x(), mouse.y()),
                BomTreePageHelper.describeActiveState());
        return removed;
    }

    private static boolean isTextInputFocused() {
        var screen = Minecraft.getInstance().screen;
        return screen != null && screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox;
    }

    @javax.annotation.Nullable
    private static EmiFavorite.Synthetic getHoveredFinalSynthetic() {
        var mouse = getCurrentGuiMousePosition();
        EmiFavorite.Synthetic synthetic = getFinalSyntheticFromHovered(
                EmiScreenManager.getHoveredStack((int) mouse.x(), (int) mouse.y(), false));
        if (synthetic != null) {
            return synthetic;
        }
        synthetic = getFinalSyntheticFromHovered(
                EmiApi.getHoveredStack((int) mouse.x(), (int) mouse.y(), false));
        if (synthetic != null) {
            return synthetic;
        }
        synthetic = getFinalSyntheticFromPreciseFavoriteHit((int) mouse.x(), (int) mouse.y());
        if (synthetic != null) {
            return synthetic;
        }
        return getFinalSyntheticFromHovered(
                EmiScreenManager.getHoveredStack(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY, false));
    }

    @javax.annotation.Nullable
    private static EmiFavorite.Synthetic getFinalSyntheticFromPreciseFavoriteHit(int mouseX, int mouseY) {
        try {
            var panel = EmiScreenManager.getHoveredPanel(mouseX, mouseY);
            if (panel == null || panel.getType() != dev.emi.emi.config.SidebarType.FAVORITES) {
                return null;
            }
            var space = panel.getHoveredSpace(mouseX, mouseY);
            if (space == null || space.pageSize <= 0) {
                return null;
            }
            int offset = space.getRawOffsetFromMouse(mouseX, mouseY);
            if (offset < 0) {
                return null;
            }
            int index = offset;
            if (space == panel.space) {
                index += space.pageSize * panel.page;
            }
            var stacks = space.getStacks();
            if (index < 0 || index >= stacks.size()) {
                return null;
            }
            var ingredient = stacks.get(index);
            if (ingredient instanceof EmiFavorite.Synthetic synthetic && BomTreePageHelper.isFinalSynthetic(synthetic)) {
                return synthetic;
            }
        } catch (Throwable t) {
            ModLogger.warn("BOM_KEY precise favorite hit error: {}: {}",
                    t.getClass().getName(), t.getMessage());
        }
        return null;
    }

    private static String describePreciseFavoriteHit(int mouseX, int mouseY) {
        try {
            var panel = EmiScreenManager.getHoveredPanel(mouseX, mouseY);
            if (panel == null) {
                return "panel=none";
            }
            var space = panel.getHoveredSpace(mouseX, mouseY);
            if (space == null) {
                return "panel=" + panel.getType() + ",space=none,page=" + panel.page;
            }
            int offset = space.getRawOffsetFromMouse(mouseX, mouseY);
            int index = offset;
            if (offset >= 0 && space == panel.space) {
                index += space.pageSize * panel.page;
            }
            String ingredientInfo = "none";
            if (offset >= 0) {
                var stacks = space.getStacks();
                if (index >= 0 && index < stacks.size()) {
                    var ingredient = stacks.get(index);
                    ingredientInfo = ingredient == null || ingredient.isEmpty()
                            ? "empty"
                            : ingredient.getClass().getName();
                    if (ingredient instanceof EmiFavorite.Synthetic synthetic) {
                        ingredientInfo += " synthetic={" + BomTreePageHelper.describeSyntheticDebug(synthetic) + "}";
                    }
                } else {
                    ingredientInfo = "out-of-range size=" + stacks.size();
                }
            }
            return "panel=" + panel.getType()
                    + ",page=" + panel.page
                    + ",spaceType=" + space.getType()
                    + ",offset=" + offset
                    + ",index=" + index
                    + ",pageSize=" + space.pageSize
                    + ",hit=" + ingredientInfo;
        } catch (Throwable t) {
            return "error=" + t.getClass().getSimpleName() + ":" + t.getMessage();
        }
    }

    @javax.annotation.Nullable
    private static EmiFavorite.Synthetic getFinalSyntheticFromHovered(dev.emi.emi.api.stack.EmiStackInteraction hovered) {
        if (hovered == null || !(hovered.getStack() instanceof EmiFavorite.Synthetic synthetic)) {
            return null;
        }
        return BomTreePageHelper.isFinalSynthetic(synthetic) ? synthetic : null;
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
        if (screen instanceof ChatScreen) return;

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
            ModLogger.debug("B key: BD single craft triggered");
            return;
        }

        if (!isPatternEncodingTerminal(handled)) {
            ModLogger.debug("B key: not a pattern terminal or BD craft, screen={}", handled.getClass().getName());
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
            ModLogger.debug("B key: no recipe found for hovered stack");
            return;
        }

        if (EmiRecipeFiller.performFill(recipe, handled,
                EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.NONE, 1)) {
            ModLogger.debug("QuickPattern: encoded {}", recipe.getId());
            event.setCanceled(true);
        } else {
            ModLogger.debug("QuickPattern: performFill returned false for {}", recipe.getId());
        }
    }

    /** N key (configurable): fill the first compatible ghost/filter slot with the hovered item. */
    private static void onQuickFillSlotKey(ScreenEvent.KeyPressed.Pre event) {
        if (tryQuickFillSlot()) {
            event.setCanceled(true);
        }
    }

    public static boolean tryHandleQuickFillSlotKeyFromEmi(int keyCode, int scanCode) {
        if (!ModKeybindings.QUICK_FILL_SLOT_KEY.matches(keyCode, scanCode)) {
            return false;
        }
        return tryQuickFillSlot();
    }

    private static boolean tryQuickFillSlot() {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> containerScreen)) return false;

        var emiStack = getQuickFillEmiStack();
        if (emiStack == null || emiStack.isEmpty()) {
            ModLogger.debug("QuickFillSlot: no EMI stack under cursor screen={}", safeClassName(mc.screen));
            return false;
        }

        if (tryFillCraftingTrackerLocatorSlot(mc.screen, containerScreen, emiStack)) {
            return true;
        }

        for (var slot : containerScreen.getMenu().slots) {
            if (!slot.getItem().isEmpty()) continue;

            // AE2 FakeSlot: try ItemStack first, then wrap fluids/chemicals as GenericStack.
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
                ModLogger.debug("QuickFillSlot: set slot {} with {}", fakeSlot.index, emiStack.getId());
                return true;
            }

        }
        return false;
    }

    private static boolean tryFillCraftingTrackerLocatorSlot(Screen screen,
                                                             AbstractContainerScreen<?> containerScreen,
                                                             EmiStack emiStack) {
        if (screen == null || !CRAFTING_TRACKER_LOCATOR_SCREEN.equals(screen.getClass().getName())) {
            return false;
        }

        ItemStack itemStack = emiStack.getItemStack();
        if (itemStack.isEmpty()) {
            return false;
        }

        int slotIndex = firstEmptyCraftingTrackerFilterSlot(containerScreen);
        if (slotIndex < 0) {
            ModLogger.debug("QuickFillSlot: Crafting Tracker locator has no empty filter slot");
            return false;
        }

        try {
            var method = screen.getClass().getMethod(CRAFTING_TRACKER_APPLY_GHOST_FILTER, int.class, ItemStack.class);
            method.invoke(screen, slotIndex, itemStack.copyWithCount(1));
            ModLogger.debug("QuickFillSlot: set Crafting Tracker locator slot {} with {}",
                    slotIndex, emiStack.getId());
            return true;
        } catch (ReflectiveOperationException e) {
            ModLogger.warn("QuickFillSlot: Crafting Tracker locator compatibility failed: {}",
                    e.getMessage());
            return false;
        }
    }

    private static int firstEmptyCraftingTrackerFilterSlot(AbstractContainerScreen<?> containerScreen) {
        var slots = containerScreen.getMenu().slots;
        Object menu = containerScreen.getMenu();
        try {
            var isFilterSlot = menu.getClass().getMethod(CRAFTING_TRACKER_IS_FILTER_SLOT, int.class);
            for (int i = 0; i < slots.size(); i++) {
                if (!Boolean.TRUE.equals(isFilterSlot.invoke(menu, i))) continue;
                if (slots.get(i).getItem().isEmpty()) {
                    return i;
                }
            }
            return -1;
        } catch (ReflectiveOperationException ignored) {
            int max = Math.min(CRAFTING_TRACKER_FALLBACK_FILTER_SLOTS, slots.size());
            for (int i = 0; i < max; i++) {
                if (slots.get(i).getItem().isEmpty()) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static EmiStack getQuickFillEmiStack() {
        var mouse = getCurrentGuiMousePosition();
        EmiStack stack;

        stack = firstStack(EmiApi.getHoveredStack(true));
        if (stack != null && !stack.isEmpty()) return stack;

        stack = firstStack(EmiApi.getHoveredStack((int) mouse.x(), (int) mouse.y(), false));
        if (stack != null && !stack.isEmpty()) return stack;

        stack = firstStack(EmiScreenManager.getHoveredStack((int) mouse.x(), (int) mouse.y(), false));
        if (stack != null && !stack.isEmpty()) return stack;

        stack = firstStack(EmiScreenManager.getHoveredStack(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY, false));
        if (stack != null && !stack.isEmpty()) return stack;

        stack = firstStack(EmiScreenManager.draggedStack);
        if (stack != null && !stack.isEmpty()) return stack;

        return firstStack(EmiScreenManager.pressedStack);
    }

    private static EmiStack firstStack(EmiStackInteraction hovered) {
        if (hovered == null || hovered.isEmpty()) return null;
        return firstStack(hovered.getStack());
    }

    private static EmiStack firstStack(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return null;
        var stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) return null;
        return stacks.getFirst();
    }

    /** Walk BoM tree and craft each node; intermediates → AE, only the final goal → inventory. */
    private static void onQuickCraftTabKey(ScreenEvent.KeyPressed.Pre event) {
        long runId = ++quickCraftRunSeq;
        EmiFavorite.Synthetic synthetic = getHoveredFinalSynthetic();
        if (synthetic != null) {
            boolean activated = BomTreePageHelper.activateSynthetic(synthetic);
            if (!activated) {
                BomTreePageHelper.refreshActiveSynthetic();
                activated = BomTreePageHelper.activateSynthetic(synthetic);
            }
            if (activated) {
                qcLog(runId, "activated BoM tree from hovered synthetic favorite");
            } else {
                qcLog(runId, "hovered synthetic favorite did not activate BoM tree: {}",
                        BomTreePageHelper.describeSyntheticActivationFailure(synthetic));
            }
        } else {
            var hovered = EmiScreenManager.getHoveredStack(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY, false);
            qcDebug(runId, "hovered stack is not a final BoM synthetic for activation: type={} stack={}",
                    hovered == null || hovered.getStack() == null ? "null" : hovered.getStack().getClass().getName(),
                    formatHoveredStack(hovered));
        }
        BomTreePageHelper.applyActiveToBoM();
        var mc = Minecraft.getInstance();
        var handled = EmiApi.getHandledScreen();
        if (handled == null) {
            if (event != null) {
                event.setCanceled(true);
            }
            qcLog(runId, "no handled screen (quick craft ignored)");
            return;
        }
        QuickCraftStorageMode storageMode = getQuickCraftStorageMode(handled, mc.player);
        qcLog(runId, "BEGIN handled={} storageMode={}",
                handled.getClass().getName(), storageMode);
        if (storageMode == QuickCraftStorageMode.UNSAFE_EXTERNAL_GRID) {
            if (event != null) {
                event.setCanceled(true);
            }
            qcLog(runId, "unsupported external grid for quick craft; stopping to avoid losing outputs");
            return;
        }

        if (BoM.tree == null || BoM.tree.goal == null) {
            if (event != null) {
                event.setCanceled(true);
            }
            qcLog(runId, "no BoM tree (quick craft ignored)");
            return;
        }

        // Collect all craftable nodes bottom-up (leaves first)
        var nodes = new java.util.ArrayList<MaterialNode>();
        collectRecipeNodes(BoM.tree.goal, nodes);
        if (nodes.isEmpty()) {
            event.setCanceled(true);
            qcLog(runId, "no craftable nodes in BoM tree");
            return;
        }
        qcLog(runId, "BoM tree has {} craftable nodes", nodes.size());

        // The last node in bottom-up order is the goal (final item)
        MaterialNode goalNode = nodes.get(nodes.size() - 1);

        // Compute needed amounts top-down from recipe input/output ratios.
        long initialBatches = Math.max(1, BoM.tree.batches);
        qcLog(runId, "tree.batches={}, using initialBatches={}", BoM.tree.batches, initialBatches);

        var neededAmounts = new java.util.LinkedHashMap<MaterialNode, Long>();
        computeCraftAmounts(runId, goalNode, initialBatches, neededAmounts);
        logPlanSummary(runId, nodes, neededAmounts, "initial");

        // Detect BoM tree goal/amount change → reset completedBatches.
        // BoM.tree.batches alone is not a reliable identity for the actual plan:
        // EMI can rebuild/reset the tree while leaving enough old state around
        // that completedBatches would otherwise be reused for the wrong amount.
        String goalId = goalNode.recipe != null && goalNode.recipe.getId() != null
                ? goalNode.recipe.getId().toString() : null;
        String planSignature = buildPlanSignature(goalId, initialBatches, nodes, neededAmounts);
        boolean goalChanged = goalId != null && !goalId.equals(lastGoalRecipeId);
        boolean batchesChanged = BoM.tree.batches != lastBoMBatches;
        boolean planChanged = lastPlanSignature != null && !lastPlanSignature.equals(planSignature);
        if (goalChanged || batchesChanged || planChanged) {
            qcLog(runId, "resetting completedBatches (goalChanged={}, batchesChanged={}, planChanged={}, oldBatches={}, newBatches={}, previousEntries={})",
                    goalChanged, batchesChanged, planChanged, lastBoMBatches, BoM.tree.batches, completedBatches.size());
            if (!completedBatches.isEmpty()) {
                qcLog(runId, "completedBatches before reset: {}", completedBatches);
            }
            completedBatches.clear();
            lastGoalRecipeId = goalId;
            lastBoMBatches = BoM.tree.batches;
            lastPlanSignature = planSignature;
            if (currentJob != null || preflightNodes != null) {
                qcLog(runId, "aborting old job/preflight because BoM plan changed");
                abortCurrentJob("BoM plan changed");
                preflightNodes = null;
                preflightGoalNode = null;
                preflightNeededAmounts = null;
                preflightScreen = null;
                preflightStorageMode = QuickCraftStorageMode.LOCAL_INVENTORY;
                preflightQueryStartedAt = 0;
                preflightRunId = 0;
                AENetworkCache.clearEphemeralQuery();
            }
        } else if (lastPlanSignature == null) {
            lastPlanSignature = planSignature;
        }

        // Sweep intermediate items to AE before computing need adjustments.
        // Otherwise items stuck in inventory from an interrupted job would be
        // counted as "available" and cause intermediates to be skipped while
        // AE still needs them for subsequent crafting.
        if (storageMode == QuickCraftStorageMode.AE_EXTERNAL && mc.player != null) {
            var inv = mc.player.getInventory();
            var allOutputs = new java.util.ArrayList<dev.emi.emi.api.stack.EmiStack>();
            for (var node : nodes) {
                if (node == goalNode || node.recipe == null) continue;
                allOutputs.addAll(node.recipe.getOutputs());
            }
            if (!allOutputs.isEmpty()) {
                int total = 0;
                var detail = new StringBuilder();
                for (int i = 0; i < inv.items.size(); i++) {
                    var stack = inv.getItem(i);
                    if (stack.isEmpty()) continue;
                    if (matchesOutput(stack, allOutputs)) {
                        appendStackAmount(detail, stack, stack.getCount());
                        PacketDistributor.sendToServer(new AEDepositPacket(stack.copy(), i));
                        inv.setItem(i, ItemStack.EMPTY);
                        total += stack.getCount();
                    }
                }
                if (total > 0) qcLog(runId, "swept {} intermediate items to AE at start: {}", total, detail);
            }
        }

        if (currentJob != null || preflightNodes != null) {
            qcLog(runId, "already {} (quick craft ignored)",
                    currentJob != null ? "crafting" : "in preflight");
            return;
        }

        // Preflight: log inventory and AE network cache for all needed items
        if (mc.player != null) {
            logInventorySnapshot(runId, mc.player, "preflight");
        }
        var preflightItems = new java.util.LinkedHashSet<ItemStack>();
        for (var node : nodes) {
            if (node.recipe == null) continue;
            for (var input : node.recipe.getInputs()) {
                if (input == null || input.isEmpty()) continue;
                for (var es : input.getEmiStacks()) {
                    var s = es.getItemStack();
                    if (!s.isEmpty()) preflightItems.add(s.copyWithCount(1));
                }
            }
        }
        if (storageMode == QuickCraftStorageMode.AE_EXTERNAL && !preflightItems.isEmpty()) {
            var sb = new StringBuilder("PREFLIGHT AE cache for inputs:");
            for (var stack : preflightItems) {
                var cached = org.chatterjay.emiextend.client.AENetworkCache.getCachedResult(stack);
                sb.append(" [").append(stack.getDisplayName().getString()).append(" stored=");
                sb.append(cached.count()).append(" craftable=").append(cached.craftable());
                sb.append(" cached=").append(cached.found()).append("]");
            }
            qcLog(runId, "{}", sb);
        }

        // Row clear: before starting, send existing items in the top visible backpack row (9-17) to the active network.
        // Skip IPN-locked slots and keep existing final goal outputs in inventory.
        if ((storageMode == QuickCraftStorageMode.AE_EXTERNAL || storageMode == QuickCraftStorageMode.BD_EXTERNAL)
                && mc.player != null) {
            var inv = mc.player.getInventory();
            var lockedSlots = IPNProxy.getLockedSlots();
            for (int i = 9; i <= 17 && i < inv.items.size(); i++) {
                if (lockedSlots.contains(i)) {
                    qcLog(runId, "row-clear skipped IPN-locked slot {}", i);
                    continue;
                }
                var stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    if (matchesNodeOutput(stack, goalNode)) {
                        qcLog(runId, "row-clear kept goal output in slot {}: {} x{}", i,
                                stack.getDisplayName().getString(), stack.getCount());
                        continue;
                    }
                    if (storageMode == QuickCraftStorageMode.BD_EXTERNAL) {
                        PacketDistributor.sendToServer(new BDDepositSlotPacket(i));
                    } else {
                        PacketDistributor.sendToServer(new AEDepositPacket(stack.copy(), i));
                    }
                    inv.setItem(i, ItemStack.EMPTY);
                    qcLog(runId, "row-clear deposited slot {} to {}: {} x{}", i,
                            storageMode == QuickCraftStorageMode.BD_EXTERNAL ? "BD" : "AE",
                            stack.getDisplayName().getString(), stack.getCount());
                }
            }
        }

        // Preflight: invalidate stale cache entries and send fresh queries
        // for ALL node outputs and inputs. Bypasses TTL checks so we always get current AE counts.
        var queryItems = new java.util.LinkedHashSet<ItemStack>();
        if (storageMode == QuickCraftStorageMode.AE_EXTERNAL) {
            for (var node : nodes) {
                if (node.recipe == null) continue;
                for (var output : node.recipe.getOutputs()) {
                    var stack = output.getItemStack();
                    if (stack.isEmpty()) continue;
                    queryItems.add(stack.copyWithCount(1));
                }
                for (var input : node.recipe.getInputs()) {
                    if (input == null || input.isEmpty()) continue;
                    for (var es : input.getEmiStacks()) {
                        var stack = es.getItemStack();
                        if (stack.isEmpty()) continue;
                        queryItems.add(stack.copyWithCount(1));
                    }
                }
            }
        }
        preflightQueryStartedAt = System.currentTimeMillis();
        if (!queryItems.isEmpty()) {
            AENetworkCache.invalidateEntries(queryItems);
            AENetworkCache.beginEphemeralQuery(queryItems);
            PacketDistributor.sendToServer(new AEBatchQueryPacket(new java.util.ArrayList<>(queryItems)));
            qcLog(runId, "preflight direct query sent for {} AE stacks: {}", queryItems.size(), describeStacks(queryItems));
        }

        preflightNodes = nodes;
        preflightGoalNode = goalNode;
        preflightNeededAmounts = neededAmounts;
        preflightScreen = handled;
        preflightStorageMode = storageMode;
        preflightStartTime = System.currentTimeMillis();
        preflightRunId = runId;
        if (storageMode == QuickCraftStorageMode.BD_EXTERNAL) {
            PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, 3));
            qcLog(runId, "BD_QUICKCRAFT_RETURN_MODE network-first enabled");
        }
        event.setCanceled(true);
        qcLog(runId, "preflight started ({} nodes, {} target batches), waiting for AE cache...",
                nodes.size(), initialBatches);
    }

/** Collect all MaterialNodes that have a non-null recipe, in bottom-up (leaves-first) order. */
    private static void collectRecipeNodes(MaterialNode node, java.util.List<MaterialNode> out) {
        if (node == null) return;
        if (node.children != null) {
            for (MaterialNode child : node.children) {
                collectRecipeNodes(child, out);
            }
        }
        if (node.recipe != null && node.recipe.getId() != null) {
            out.add(node);
        }
    }

    /** Check if a stack matches any of the given EmiStacks. */
    private static boolean matchesOutput(ItemStack stack, java.util.List<dev.emi.emi.api.stack.EmiStack> outputs) {
        for (var es : outputs) {
            if (es == null || es.isEmpty()) continue;
            var outputStack = es.getItemStack();
            if (outputStack == null || outputStack.isEmpty()) continue;
            if (ItemStack.isSameItemSameComponents(stack, outputStack)) return true;
        }
        return false;
    }

    private static boolean matchesNodeOutput(ItemStack stack, MaterialNode node) {
        if (stack == null || stack.isEmpty()) return false;
        if (node == null || node.recipe == null) return false;
        for (var output : node.recipe.getOutputs()) {
            var outputStack = output.getItemStack();
            if (outputStack == null || outputStack.isEmpty()) continue;
            if (ItemStack.isSameItemSameComponents(stack, outputStack)) return true;
        }
        return false;
    }

    /** After ALL nodes are done: scan player inventory and deposit every item that matches an intermediate recipe output. */
    private static void depositAllIntermediates(long runId, Player player, CraftingJob job) {
        if (job.storageMode != QuickCraftStorageMode.AE_EXTERNAL) return;
        var inv = player.getInventory();
        var outputsToDeposit = new java.util.ArrayList<dev.emi.emi.api.stack.EmiStack>();
        for (var node : job.nodes) {
            if (node == job.goalNode || node.recipe == null) continue;
            outputsToDeposit.addAll(node.recipe.getOutputs());
        }
        if (outputsToDeposit.isEmpty()) return;

        int total = 0;
        var detail = new StringBuilder();
        for (int i = 0; i < inv.items.size(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (matchesOutput(stack, outputsToDeposit)) {
                appendStackAmount(detail, stack, stack.getCount());
                PacketDistributor.sendToServer(new AEDepositPacket(stack.copy(), i));
                inv.setItem(i, ItemStack.EMPTY);
                total += stack.getCount();
            }
        }

        // Also sweep the cursor in case the last item didn't reach inventory yet.
        var carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && matchesOutput(carried, outputsToDeposit)) {
            appendStackAmount(detail, carried, carried.getCount());
            PacketDistributor.sendToServer(new AEDepositPacket(carried.copy(), -1));
            player.containerMenu.setCarried(ItemStack.EMPTY);
            total += carried.getCount();
        }

        if (total > 0) qcLog(runId, "deposited {} intermediate items to AE: {}", total, detail);
    }

    /** Deposit just one finished node's outputs immediately. */
    private static int depositNodeOutputs(long runId, Player player, MaterialNode node) {
        if (currentJob != null && currentJob.storageMode != QuickCraftStorageMode.AE_EXTERNAL) return 0;
        if (player == null || node == null || node.recipe == null) return 0;

        var outputs = node.recipe.getOutputs();
        if (outputs.isEmpty()) return 0;

        var inv = player.getInventory();
        int total = 0;
        var detail = new StringBuilder();
        var invalidation = new java.util.ArrayList<ItemStack>();

        for (int i = 0; i < inv.items.size(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (matchesOutput(stack, outputs)) {
                appendStackAmount(detail, stack, stack.getCount());
                PacketDistributor.sendToServer(new AEDepositPacket(stack.copy(), i));
                inv.setItem(i, ItemStack.EMPTY);
                total += stack.getCount();
                invalidation.add(stack.copyWithCount(1));
            }
        }

        var carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && matchesOutput(carried, outputs)) {
            appendStackAmount(detail, carried, carried.getCount());
            PacketDistributor.sendToServer(new AEDepositPacket(carried.copy(), -1));
            player.containerMenu.setCarried(ItemStack.EMPTY);
            total += carried.getCount();
            invalidation.add(carried.copyWithCount(1));
        }

        if (total > 0) {
            AENetworkCache.invalidateEntries(invalidation);
            qcLog(runId, "deposited finished node outputs to AE: {}", detail);
        }
        return total;
    }


    /** After each batch: if backpack has fewer than 3 empty slots, sweep intermediates to AE. */
    private static void checkAndSweepIntermediates(Player player) {
        if (currentJob == null) return;
        if (currentJob.storageMode != QuickCraftStorageMode.AE_EXTERNAL) return;
        int emptySlots = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (inv.getItem(i).isEmpty()) emptySlots++;
        }
        if (emptySlots < 3) {
            qcLog(currentJob.runId, "low empty slots ({}), sweeping intermediates", emptySlots);
            depositAllIntermediates(currentJob.runId, player, currentJob);
        }
    }

    /** Process one batch per game tick, linear through nodes. */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance();

        // Handle preflight: wait for AE cache queries to return before creating job
        if (preflightNodes != null) {
            long runId = preflightRunId;
            long elapsed = System.currentTimeMillis() - preflightStartTime;
            QuickCraftStorageMode storageMode = preflightStorageMode;
            boolean usesAeStorage = storageMode == QuickCraftStorageMode.AE_EXTERNAL;
            boolean allCached = !usesAeStorage || allNodeOutputsCached(preflightNodes, preflightQueryStartedAt);
            boolean timedOut = elapsed > PREFLIGHT_TIMEOUT_MS;

            if (!allCached && !timedOut) {
                return; // Still waiting for cache
            }

            int cachedCount = countCachedOutputs(preflightNodes, preflightQueryStartedAt);
            int totalOutputs = countTotalOutputs(preflightNodes);
            qcLog(runId, "preflight complete (cached={}/{}, elapsed={}ms{})",
                    cachedCount, totalOutputs, elapsed, timedOut ? ", TIMEOUT" : "");
            if (timedOut && cachedCount < totalOutputs) {
                logMissingFreshOutputs(runId, preflightNodes, preflightQueryStartedAt);
            }
            logOutputAvailability(runId, preflightNodes, mc.player, "preflight-output-availability", storageMode);

            var jobNeededAmounts = preflightNeededAmounts;
            long originalGoalNeed = preflightNeededAmounts.getOrDefault(preflightGoalNode,
                    (long) preflightGoalNode.divisor);
            var goalAvailability = getNodeAvailability(preflightGoalNode, mc.player, storageMode);
            long availableGoal = goalAvailability.total();
            long remainingGoalNeed = Math.max(0, originalGoalNeed - availableGoal);
            qcLog(runId, "goal availability recipe={} need={} ae={} inv={} carried={} total={} remaining={} detail={}",
                    recipeId(preflightGoalNode), originalGoalNeed, goalAvailability.ae(),
                    goalAvailability.inventory(), goalAvailability.carried(), availableGoal,
                    remainingGoalNeed, goalAvailability.detail());

            long goalOutputDeduction = 0;
            if (remainingGoalNeed <= 0) {
                qcLog(runId, "goal already satisfied; no crafting needed");
                removeCompletedBomTree(runId, preflightGoalNode, "goal already satisfied");
                preflightNodes = null;
                preflightGoalNode = null;
                preflightNeededAmounts = null;
                preflightScreen = null;
                preflightStorageMode = QuickCraftStorageMode.LOCAL_INVENTORY;
                preflightQueryStartedAt = 0;
                preflightRunId = 0;
                completedBatches.clear();
                lastGoalRecipeId = null;
                lastBoMBatches = -1;
                lastPlanSignature = null;
                AENetworkCache.clearEphemeralQuery();
                return;
            }

            if (remainingGoalNeed < originalGoalNeed) {
                goalOutputDeduction = originalGoalNeed - remainingGoalNeed;
                long adjustedBatches = (remainingGoalNeed + preflightGoalNode.divisor - 1) / preflightGoalNode.divisor;
                jobNeededAmounts = new java.util.LinkedHashMap<>();
                computeCraftAmounts(runId, preflightGoalNode, adjustedBatches, jobNeededAmounts);
                completedBatches.clear();
                qcLog(runId, "adjusted BoM target from {} to {} items ({} batches) based on existing goal output; goalOutputDeduction={}",
                        originalGoalNeed, remainingGoalNeed, adjustedBatches, goalOutputDeduction);
                logPlanSummary(runId, preflightNodes, jobNeededAmounts, "adjusted");
            }

            if (storageMode == QuickCraftStorageMode.AE_EXTERNAL) {
                AeCraftableShortage directAeRequest = findTopDownAeCraftableOutputShortage(
                        preflightGoalNode, jobNeededAmounts, mc.player, preflightGoalNode, goalOutputDeduction);
                if (directAeRequest != null) {
                    PacketDistributor.sendToServer(new AEAutocraftRequestPacket(
                            directAeRequest.stack().copyWithCount(1), directAeRequest.missing()));
                    qcLog(runId, "AE_QUICKCRAFT direct top-down ME autocraft recipe={} item={} needed={} available={} missing={}; quick craft job paused before manual chain",
                            recipeId(directAeRequest.node()),
                            directAeRequest.stack().getHoverName().getString(),
                            directAeRequest.needed(),
                            directAeRequest.available(),
                            directAeRequest.missing());
                    preflightNodes = null;
                    preflightGoalNode = null;
                    preflightNeededAmounts = null;
                    preflightScreen = null;
                    preflightStorageMode = QuickCraftStorageMode.LOCAL_INVENTORY;
                    preflightQueryStartedAt = 0;
                    preflightRunId = 0;
                    AENetworkCache.clearEphemeralQuery();
                    return;
                }
            }

            currentJob = new CraftingJob(preflightScreen, preflightGoalNode,
                    preflightNodes, jobNeededAmounts, runId, goalOutputDeduction, storageMode);
            preflightNodes = null;
            preflightGoalNode = null;
            preflightNeededAmounts = null;
            preflightScreen = null;
            preflightStorageMode = QuickCraftStorageMode.LOCAL_INVENTORY;
            preflightQueryStartedAt = 0;
            preflightRunId = 0;
            qcLog(runId, "job started with {} nodes, completedBatches={}", currentJob.nodes.size(), completedBatches);
            // Fall through to process first batch in this tick
        }

        if (currentJob == null) return;

        if (mc.player == null) {
            qcLog(currentJob.runId, "player gone, aborting job");
            abortCurrentJob("player gone");
            return;
        }

        // Unwrap overlay screens (BoMScreen, RecipeScreen) to find the actual container screen
        var screen = mc.screen;
        if (screen instanceof dev.emi.emi.screen.BoMScreen bom && bom.old != null) {
            screen = bom.old;
        } else if (screen instanceof dev.emi.emi.screen.RecipeScreen rs && rs.old != null) {
            screen = rs.old;
        }
        if (screen != currentJob.screen) {
            qcLog(currentJob.runId, "screen changed (current={}, job={}), aborting", screen, currentJob.screen);
            abortCurrentJob("screen changed");
            return;
        }

        if (currentJob.pendingBdCraftNode != null) {
            var node = currentJob.pendingBdCraftNode;
            currentJob.pendingBdCraftTicks++;
            if (currentJob.pendingBdCraftTicks < 2) {
                return;
            }

            boolean bdIntermediate = currentJob.storageMode == QuickCraftStorageMode.BD_EXTERNAL
                    && node != currentJob.goalNode;
            PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, bdIntermediate ? 7 : 2));
            currentJob.pendingVerifyNode = node;
            currentJob.pendingVerifyTicks = 0;
            currentJob.pendingVerifyBeforeTotal = currentJob.pendingBdCraftBeforeTotal;
            qcLog(currentJob.runId, "BD_CRAFT_RESULT_SENT {} destination={} beforeTotal={}",
                    recipeId(node), bdIntermediate ? "NETWORK" : "INVENTORY",
                    currentJob.pendingVerifyBeforeTotal);
            currentJob.pendingBdCraftNode = null;
            currentJob.pendingBdCraftTicks = 0;
            currentJob.pendingBdCraftBeforeTotal = 0;
            return;
        }

        if (currentJob.pendingVerifyNode != null) {
            var node = currentJob.pendingVerifyNode;
            var availability = getNodeAvailability(node, mc.player, currentJob.storageMode);
            currentJob.pendingVerifyTicks++;
            if (availability.total() > currentJob.pendingVerifyBeforeTotal) {
                currentJob.remainingBatches--;
                currentJob.nodeSucceeded++;
                currentJob.consecutiveFails = 0;
                qcLog(currentJob.runId, "VERIFY_OK {} before={} after={} left={}",
                        recipeId(node), currentJob.pendingVerifyBeforeTotal,
                        availability.total(), currentJob.remainingBatches);
                currentJob.pendingVerifyNode = null;
                currentJob.pendingVerifyTicks = 0;
                currentJob.pendingVerifyBeforeTotal = 0;
                if (currentJob.storageMode == QuickCraftStorageMode.BD_EXTERNAL && currentJob.remainingBatches > 0) {
                    currentJob.pendingBdCraftNode = node;
                    currentJob.pendingBdCraftTicks = 0;
                    currentJob.pendingBdCraftBeforeTotal = availability.total();
                    qcLog(currentJob.runId, "BD_NEXT_CRAFT_SCHEDULE {} beforeTotal={} left={}",
                            recipeId(node), currentJob.pendingBdCraftBeforeTotal, currentJob.remainingBatches);
                    return;
                }
                if (currentJob.remainingBatches <= 0) {
                    finishCurrentNode(mc.player, node);
                }
                return;
            } else if (currentJob.pendingVerifyTicks >= verificationTimeoutTicks(currentJob.storageMode)) {
                currentJob.nodeFailed++;
                currentJob.consecutiveFails++;
                currentJob.hadFailures = true;
                qcLog(currentJob.runId, "VERIFY_FAIL {} before={} after={} ticks={} storageMode={}, aborting",
                        recipeId(node), currentJob.pendingVerifyBeforeTotal,
                        availability.total(), currentJob.pendingVerifyTicks, currentJob.storageMode);
                currentJob.pendingVerifyNode = null;
                abortCurrentJob("crafted output was not observed");
                return;
            } else {
                return;
            }
        }

        if (currentJob.pendingDepositNode != null) {
            int deposited = depositNodeOutputs(currentJob.runId, mc.player, currentJob.pendingDepositNode);
            currentJob.pendingDepositTicks++;
            if (deposited > 0 || currentJob.pendingDepositTicks >= 3) {
                if (deposited <= 0) {
                    qcLog(currentJob.runId, "no finished node outputs found to deposit after {} ticks for {}",
                            currentJob.pendingDepositTicks, recipeId(currentJob.pendingDepositNode));
                    if (currentJob.storageMode == QuickCraftStorageMode.AE_EXTERNAL
                            || currentJob.storageMode == QuickCraftStorageMode.BD_EXTERNAL) {
                        abortCurrentJob("intermediate output could not be stored");
                        return;
                    }
                }
                currentJob.pendingDepositNode = null;
                currentJob.pendingDepositTicks = 0;
                currentJob.nodeIndex++;
            }
            return;
        }

        while (currentJob.nodeIndex < currentJob.nodes.size()) {
            var node = currentJob.nodes.get(currentJob.nodeIndex);
            if (node.recipe == null || node.recipe.getId() == null) {
                qcDebug(currentJob.runId, "skip null-recipe node at index={}", currentJob.nodeIndex);
                currentJob.nodeIndex++;
                continue;
            }

            // Initialize/replenish remaining batches for this node
            if (currentJob.remainingBatches <= 0) {
                long needed = currentJob.neededAmounts.getOrDefault(node, (long) node.divisor);
                long alreadyDone = completedBatches.getOrDefault(node.recipe.getId().toString(), 0L);
                long alreadyDoneItems = alreadyDone * node.divisor;

                var availability = getNodeAvailability(node, mc.player, currentJob.storageMode);
                long rawAvailabilityTotal = availability.total();
                long effectiveAvailabilityTotal = rawAvailabilityTotal;
                if (node == currentJob.goalNode && currentJob.goalOutputDeduction > 0) {
                    effectiveAvailabilityTotal = Math.max(0, rawAvailabilityTotal - currentJob.goalOutputDeduction);
                }
                long alreadyAvailable = Math.max(alreadyDoneItems, effectiveAvailabilityTotal);
                long adjustedNeed = needed - alreadyAvailable;
                if (adjustedNeed <= 0) {
                    qcLog(currentJob.runId, "NODE_DECISION SKIP index={} recipe={} need={} divisor={} completedBatches={} completedItems={} availabilityTotal={} effectiveAvailabilityTotal={} goalOutputDeduction={} ae={} inv={} carried={} adjustedNeed={} detail={}",
                            currentJob.nodeIndex, node.recipe.getId(), needed, node.divisor,
                            alreadyDone, alreadyDoneItems, rawAvailabilityTotal, effectiveAvailabilityTotal,
                            node == currentJob.goalNode ? currentJob.goalOutputDeduction : 0,
                            availability.ae(),
                            availability.inventory(), availability.carried(), adjustedNeed, availability.detail());
                    currentJob.nodeIndex++;
                    continue;
                }
                currentJob.remainingBatches = (adjustedNeed + node.divisor - 1) / node.divisor;
                if (currentJob.remainingBatches > 100000) currentJob.remainingBatches = 100000;
                currentJob.nodeSucceeded = 0;
                currentJob.nodeFailed = 0;
                currentJob.consecutiveFails = 0;
                qcLog(currentJob.runId, "NODE_DECISION CRAFT index={} recipe={} need={} divisor={} completedBatches={} completedItems={} availabilityTotal={} effectiveAvailabilityTotal={} goalOutputDeduction={} ae={} inv={} carried={} adjustedNeed={} batches={} detail={}",
                        currentJob.nodeIndex, node.recipe.getId(), needed, node.divisor,
                        alreadyDone, alreadyDoneItems, rawAvailabilityTotal, effectiveAvailabilityTotal,
                        node == currentJob.goalNode ? currentJob.goalOutputDeduction : 0,
                        availability.ae(),
                        availability.inventory(), availability.carried(), adjustedNeed,
                        currentJob.remainingBatches, availability.detail());
                logInventorySnapshot(currentJob.runId, mc.player, "node-start");
                if (currentJob.storageMode == QuickCraftStorageMode.AE_EXTERNAL) {
                    logAENetworkForNode(currentJob.runId, node);
                }
            }

            if (currentJob.remainingBatches <= 0) {
                qcLog(currentJob.runId, "SKIP {} (need=0)", node.recipe.getId());
                currentJob.nodeIndex++;
                continue;
            }

            checkAndSweepIntermediates(mc.player);

            boolean outputMustFitInventory = currentJob.storageMode != QuickCraftStorageMode.BD_EXTERNAL
                    || node == currentJob.goalNode;
            if (outputMustFitInventory && !canAcceptRecipeOutputs(mc.player, node.recipe)) {
                qcLog(currentJob.runId, "STOP inventory cannot accept output for {} outputs={}",
                        node.recipe.getId(), describeNodeOutputs(node));
                abortCurrentJob("no room for crafting output");
                return;
            }

            if (currentJob.storageMode == QuickCraftStorageMode.AE_EXTERNAL) {
                AeInputNeed aeInputNeed = findAeAutocraftInputNeed(node, currentJob.remainingBatches, mc.player);
                if (aeInputNeed != null && aeInputNeed.needed() > 0) {
                    long available = countPlayerAndCarried(mc.player, aeInputNeed.stack())
                            + AENetworkCache.getCachedResult(aeInputNeed.stack()).count();
                    long missing = Math.max(0, aeInputNeed.needed() - available);
                    qcLog(currentJob.runId, "AE_QUICKCRAFT prefill direct request recipe={} item={} needed={} available={} missing={}",
                            node.recipe.getId(), aeInputNeed.stack().getHoverName().getString(),
                            aeInputNeed.needed(), available, missing);
                    PacketDistributor.sendToServer(new AEAutocraftRequestPacket(
                            aeInputNeed.stack().copyWithCount(1), (int) Math.min(Integer.MAX_VALUE, missing)));
                    abortCurrentJob("prefill AE autocraft request");
                    return;
                }
            }

            // All nodes use INVENTORY — goes through CraftingTermSlotMixin
            // which reliably puts items into PlayerInternalInventory.
            //
            // Important for AE quick-craft: do not enable the post-fill AE autocraft
            // handoff here. That path only runs after EMI/AE has filled the crafting
            // grid, and AE's missing-material calculation does not count items already
            // sitting in that grid. Missing craftable inputs are requested above before
            // performFill, while the network still sees the complete inventory.
            long beforeOutputTotal = currentJob.storageMode == QuickCraftStorageMode.AE_EXTERNAL
                    ? 0
                    : getNodeAvailability(node, mc.player, currentJob.storageMode).total();
            if (currentJob.storageMode == QuickCraftStorageMode.BD_EXTERNAL) {
                PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, 3));
            }
            if (currentJob.storageMode == QuickCraftStorageMode.AE_EXTERNAL) {
                qcLog(currentJob.runId, "AE_QUICKCRAFT performFill without post-fill ME handoff for {}; prefill request already checked",
                        node.recipe.getId());
            }
            boolean ok = EmiRecipeFiller.performFill(node.recipe, currentJob.screen,
                    EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.INVENTORY, 1);

            if (ok && currentJob.storageMode == QuickCraftStorageMode.BD_EXTERNAL) {
                currentJob.pendingBdCraftNode = node;
                currentJob.pendingBdCraftTicks = 0;
                currentJob.pendingBdCraftBeforeTotal = beforeOutputTotal;
                qcLog(currentJob.runId, "BD_FILL_SCHEDULE {} beforeTotal={}",
                        node.recipe.getId(), currentJob.pendingBdCraftBeforeTotal);
                return;
            } else if (ok && currentJob.storageMode != QuickCraftStorageMode.AE_EXTERNAL) {
                currentJob.pendingVerifyNode = node;
                currentJob.pendingVerifyTicks = 0;
                currentJob.pendingVerifyBeforeTotal = beforeOutputTotal;
                qcLog(currentJob.runId, "VERIFY_SCHEDULE {} beforeTotal={}",
                        node.recipe.getId(), currentJob.pendingVerifyBeforeTotal);
                return;
            } else if (ok) {
                currentJob.remainingBatches--;
                currentJob.nodeSucceeded++;
                currentJob.consecutiveFails = 0;
            } else {
                currentJob.nodeFailed++;
                currentJob.consecutiveFails++;
                currentJob.hadFailures = true;
            }

            qcLog(currentJob.runId, "PERFORM {} -> {} (ok={}, fail={}, left={}/{})",
                    node.recipe.getId(), ok,
                    currentJob.nodeSucceeded, currentJob.nodeFailed,
                    currentJob.remainingBatches,
                    currentJob.nodeSucceeded + currentJob.nodeFailed + currentJob.remainingBatches);

            // After 3 consecutive failures, assume materials exhausted; skip node
            if (currentJob.consecutiveFails >= 3) {
                qcLog(currentJob.runId, "FAIL_LIMIT {} after {} ok + {} fail, skipping {} remaining batches",
                        node.recipe.getId(), currentJob.nodeSucceeded, currentJob.nodeFailed, currentJob.remainingBatches);
                currentJob.remainingBatches = 0;
                currentJob.hadFailures = true;
            }

            if (currentJob.remainingBatches <= 0) {
                finishCurrentNode(mc.player, node);
            }

            return; // One batch per tick
        }

        // ALL DONE — sweep intermediates to AE (goal is excluded from sweep)
        qcLog(currentJob.runId, "ALL DONE ({} nodes), sweeping intermediates",
                currentJob.nodes.size());
        logInventorySnapshot(currentJob.runId, mc.player, "all-done");
        depositAllIntermediates(currentJob.runId, mc.player, currentJob);
        logInventorySnapshot(currentJob.runId, mc.player, "after-sweep");
        if (currentJob.storageMode == QuickCraftStorageMode.BD_EXTERNAL) {
            PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, 6));
            PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, 4));
        }
        if (currentJob.hadFailures) {
            qcLog(currentJob.runId, "finished with failures; keeping completedBatches for resume ({} entries): {}",
                    completedBatches.size(), completedBatches);
        } else {
            qcLog(currentJob.runId, "finished cleanly; clearing completedBatches ({} entries)", completedBatches.size());
            removeCompletedBomTree(currentJob.runId, currentJob.goalNode, "quick craft finished cleanly");
            completedBatches.clear();
            lastGoalRecipeId = null;
            lastBoMBatches = -1;
            lastPlanSignature = null;
        }
        currentJob = null;
        AENetworkCache.clearEphemeralQuery();
    }

    private static void removeCompletedBomTree(long runId, MaterialNode goalNode, String reason) {
        String goalRecipeId = recipeId(goalNode);
        if (goalRecipeId == null) {
            return;
        }
        boolean removed = BomTreePageHelper.removeTreeForRecipeId(goalRecipeId);
        qcLog(runId, "BOM_TREE_PAGE completion cleanup recipe={} reason={} removed={}",
                goalRecipeId, reason, removed);
    }

    private static void finishCurrentNode(Player player, MaterialNode node) {
        if (currentJob == null || node == null || node.recipe == null || node.recipe.getId() == null) return;

        logNodeSummary(currentJob.runId, player, node);
        if (node != currentJob.goalNode
                && currentJob.storageMode == QuickCraftStorageMode.AE_EXTERNAL) {
            currentJob.pendingDepositNode = node;
            currentJob.pendingDepositTicks = 0;
        } else {
            currentJob.nodeIndex++;
        }
        completedBatches.merge(node.recipe.getId().toString(), currentJob.nodeSucceeded, Long::sum);
        qcLog(currentJob.runId, "saved {} batches for {} (total={}, map={})",
                currentJob.nodeSucceeded, node.recipe.getId(),
                completedBatches.get(node.recipe.getId().toString()), completedBatches);
        currentJob.nodeSucceeded = 0;
        currentJob.nodeFailed = 0;
        currentJob.consecutiveFails = 0;
    }

    private static int verificationTimeoutTicks(QuickCraftStorageMode storageMode) {
        return storageMode == QuickCraftStorageMode.BD_EXTERNAL ? 5 : 3;
    }

    /** Preserve partial progress when a job is interrupted before a node completes. */
    private static void abortCurrentJob(String reason) {
        if (currentJob == null) return;
        if (currentJob.storageMode == QuickCraftStorageMode.BD_EXTERNAL) {
            PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, 6));
            PacketDistributor.sendToServer(new BDActionPacket(ItemStack.EMPTY, 4));
        }
        savePartialNodeProgress(reason);
        currentJob = null;
        AENetworkCache.clearEphemeralQuery();
    }

    private static void clearPreflightState() {
        preflightNodes = null;
        preflightGoalNode = null;
        preflightNeededAmounts = null;
        preflightScreen = null;
        preflightStorageMode = QuickCraftStorageMode.LOCAL_INVENTORY;
        preflightQueryStartedAt = 0;
        preflightRunId = 0;
        AENetworkCache.clearEphemeralQuery();
    }

    private static void savePartialNodeProgress(String reason) {
        if (currentJob == null) return;
        if (currentJob.nodeSucceeded <= 0) return;
        if (currentJob.nodeIndex < 0 || currentJob.nodeIndex >= currentJob.nodes.size()) return;

        var node = currentJob.nodes.get(currentJob.nodeIndex);
        if (node.recipe == null || node.recipe.getId() == null) return;

        completedBatches.merge(node.recipe.getId().toString(), currentJob.nodeSucceeded, Long::sum);
        qcLog(currentJob.runId, "saved partial {} batches for {} on abort ({}) (total={}, map={})",
                currentJob.nodeSucceeded, node.recipe.getId(), reason,
                completedBatches.get(node.recipe.getId().toString()), completedBatches);
    }

    /** Log a compact snapshot of the player's inventory (item names + counts). */
    private static void logInventorySnapshot(long runId, Player player, String tag) {
        var sb = new StringBuilder();
        var inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.items.size(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(stack.getDisplayName().getString()).append(" x").append(stack.getCount());
            total += stack.getCount();
        }
        var carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            sb.append("  [cursor: ").append(carried.getDisplayName().getString()).append(" x").append(carried.getCount()).append("]");
            total += carried.getCount();
        }
        qcLog(runId, "INV[{}] {} items, {} slots used: {}", tag, total, inv.items.size() - countEmptySlots(player), sb.toString());
    }

    private static int countEmptySlots(Player player) {
        int empty = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (inv.getItem(i).isEmpty()) empty++;
        }
        return empty;
    }

    private static QuickCraftStorageMode getQuickCraftStorageMode(AbstractContainerScreen<?> screen, Player player) {
        if (screen != null) {
            if (BDProxy.isBDNetGUI(screen) || BDProxy.isBDCraftGUI(screen)
                    || BDProxy.isBDBaseMenu(screen.getMenu())) {
                return QuickCraftStorageMode.BD_EXTERNAL;
            }
            String screenName = screen.getClass().getName();
            if (screenName.startsWith("com.refinedmods.refinedstorage.")) {
                return QuickCraftStorageMode.UNSAFE_EXTERNAL_GRID;
            }
            try {
                Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
                if (aeBaseMenuClass.isInstance(screen.getMenu())) {
                    return QuickCraftStorageMode.AE_EXTERNAL;
                }
            } catch (Throwable ignored) {}
        }
        if (player != null
                && org.chatterjay.emiextend.client.handler.EmiInteractionHandler.hasWirelessTerminal(player)) {
            return QuickCraftStorageMode.AE_EXTERNAL;
        }
        return QuickCraftStorageMode.LOCAL_INVENTORY;
    }

    private static boolean canAcceptRecipeOutputs(Player player, EmiRecipe recipe) {
        if (player == null || recipe == null) return false;

        var simulated = new java.util.ArrayList<ItemStack>();
        var inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            simulated.add(inv.getItem(i).copy());
        }

        for (var output : recipe.getOutputs()) {
            var out = output.getItemStack();
            if (out == null || out.isEmpty()) continue;

            int remaining = Math.max(1, out.getCount());
            for (var slot : simulated) {
                if (remaining <= 0) break;
                if (slot.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(slot, out)) continue;

                int room = Math.max(0, slot.getMaxStackSize() - slot.getCount());
                int moved = Math.min(room, remaining);
                if (moved > 0) {
                    slot.grow(moved);
                    remaining -= moved;
                }
            }

            for (int i = 0; i < simulated.size() && remaining > 0; i++) {
                var slot = simulated.get(i);
                if (!slot.isEmpty()) continue;

                int moved = Math.min(out.getMaxStackSize(), remaining);
                simulated.set(i, out.copyWithCount(moved));
                remaining -= moved;
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /** Log what the AE network cache says about this node's inputs and outputs. */
    private static void logAENetworkForNode(long runId, MaterialNode node) {
        if (node.recipe == null) return;
        var recipe = node.recipe;

        // Log cached counts for recipe outputs
        for (var output : recipe.getOutputs()) {
            var stack = output.getItemStack();
            if (stack.isEmpty()) continue;
            var cached = org.chatterjay.emiextend.client.AENetworkCache.getCachedResult(stack);
            if (cached.found()) {
                qcLog(runId, "AE output {} stored={} craftable={}",
                        stack.getDisplayName().getString(), cached.count(), cached.craftable());
            }
        }

        // Log cached counts for recipe inputs
        for (var input : recipe.getInputs()) {
            if (input == null || input.isEmpty()) continue;
            for (var es : input.getEmiStacks()) {
                var stack = es.getItemStack();
                if (stack.isEmpty()) continue;
                var cached = org.chatterjay.emiextend.client.AENetworkCache.getCachedResult(stack);
                if (cached.found()) {
                    qcLog(runId, "AE input {} stored={} craftable={}",
                            stack.getDisplayName().getString(), cached.count(), cached.craftable());
                }
                break; // One representative per input slot
            }
        }
    }

    /** Log summary after a node completes: crafted/failed batches, output in AE cache. */
    private static void logNodeSummary(long runId, Player player, MaterialNode node) {
        if (node.recipe == null) return;
        qcLog(runId, "DONE_NODE {} ok={} fail={}",
                node.recipe.getId(), currentJob.nodeSucceeded, currentJob.nodeFailed);

        // AE cache check for node output
        for (var output : node.recipe.getOutputs()) {
            var stack = output.getItemStack();
            if (stack.isEmpty()) continue;
            var cached = org.chatterjay.emiextend.client.AENetworkCache.getCachedResult(stack);
            qcLog(runId, "  AE after: {} stored={} craftable={} (cached={})",
                    stack.getDisplayName().getString(), cached.count(), cached.craftable(), cached.found());
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

    /** Top-down tree walk: compute how many items each node must produce from recipe input/output ratios. */
    private static void computeCraftAmounts(long runId, MaterialNode node, long batchesNeeded, java.util.Map<MaterialNode, Long> out) {
        out.merge(node, batchesNeeded * node.divisor, Long::sum);
        String nodeId = (node.recipe != null && node.recipe.getId() != null) ? node.recipe.getId().toString() : "(raw)";
        qcLog(runId, "compute node={} batchesNeeded={} divisor={} => totalNeed={}",
                nodeId, batchesNeeded, node.divisor, batchesNeeded * node.divisor);
        if (node.children == null || node.children.isEmpty()) return;
        if (node.recipe == null || node.recipe.getId() == null) return;

        var inputs = node.recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return;

        // Accumulate needed amounts from each recipe input slot
        var childNeeded = new java.util.LinkedHashMap<MaterialNode, Long>();
        for (var input : inputs) {
            if (input.isEmpty()) continue;
            long perBatch = input.getAmount();
            for (var child : node.children) {
                if (child.recipe == null || child.recipe.getId() == null) continue;
                if (childProducesInput(child, input)) {
                    childNeeded.merge(child, batchesNeeded * perBatch, Long::sum);
                    qcDebug(runId, "  input amount={} -> child {}", perBatch, child.recipe.getId());
                    break;
                }
            }
        }

        for (var entry : childNeeded.entrySet()) {
            var child = entry.getKey();
            long needed = entry.getValue();
            long batches = (needed + child.divisor - 1) / child.divisor;
            qcLog(runId, "  -> child {} need={} div={} batches={}", child.recipe.getId(), needed, child.divisor, batches);
            computeCraftAmounts(runId, child, batches, out);
        }
    }

    private static boolean childProducesInput(MaterialNode child, dev.emi.emi.api.stack.EmiIngredient input) {
        for (var output : child.recipe.getOutputs()) {
            for (var os : output.getEmiStacks()) {
                if (os.isEmpty()) continue;
                for (var is : input.getEmiStacks()) {
                    if (!is.isEmpty() && os.getId().equals(is.getId())) return true;
                }
            }
        }
        return false;
    }

    /** Check if AE cache has fresh data for every output of every node. */
    private static boolean allNodeOutputsCached(java.util.List<MaterialNode> nodes, long minTimestamp) {
        for (var node : nodes) {
            if (node.recipe == null) continue;
            for (var output : node.recipe.getOutputs()) {
                var stack = output.getItemStack();
                if (stack.isEmpty()) continue;
                if (!AENetworkCache.getCachedResultSince(stack, minTimestamp).found()) return false;
            }
            for (var input : node.recipe.getInputs()) {
                if (input == null || input.isEmpty()) continue;
                for (var es : input.getEmiStacks()) {
                    var stack = es.getItemStack();
                    if (stack.isEmpty()) continue;
                    if (!AENetworkCache.getCachedResultSince(stack, minTimestamp).found()) return false;
                }
            }
        }
        return true;
    }

    private static String buildPlanSignature(String goalId, long initialBatches,
                                             java.util.List<MaterialNode> nodes,
                                             java.util.Map<MaterialNode, Long> neededAmounts) {
        var sb = new StringBuilder();
        sb.append(goalId).append('|').append(initialBatches);
        for (var node : nodes) {
            if (node.recipe == null || node.recipe.getId() == null) continue;
            sb.append('|').append(node.recipe.getId());
            sb.append('#').append(node.divisor);
            sb.append('=').append(neededAmounts.getOrDefault(node, 0L));
            for (var output : node.recipe.getOutputs()) {
                var stack = output.getItemStack();
                if (stack.isEmpty()) continue;
                sb.append('>').append(stack.getItem()).append('@').append(output.getAmount());
            }
        }
        return sb.toString();
    }

    private static int countCachedOutputs(java.util.List<MaterialNode> nodes, long minTimestamp) {
        int count = 0;
        for (var node : nodes) {
            if (node.recipe == null) continue;
            for (var output : node.recipe.getOutputs()) {
                var stack = output.getItemStack();
                if (stack.isEmpty()) continue;
                if (AENetworkCache.getCachedResultSince(stack, minTimestamp).found()) count++;
            }
            for (var input : node.recipe.getInputs()) {
                if (input == null || input.isEmpty()) continue;
                for (var es : input.getEmiStacks()) {
                    var stack = es.getItemStack();
                    if (stack.isEmpty()) continue;
                    if (AENetworkCache.getCachedResultSince(stack, minTimestamp).found()) count++;
                }
            }
        }
        return count;
    }

    private static int countTotalOutputs(java.util.List<MaterialNode> nodes) {
        int count = 0;
        for (var node : nodes) {
            if (node.recipe == null) continue;
            for (var output : node.recipe.getOutputs()) {
                var stack = output.getItemStack();
                if (!stack.isEmpty()) count++;
            }
            for (var input : node.recipe.getInputs()) {
                if (input == null || input.isEmpty()) continue;
                for (var es : input.getEmiStacks()) {
                    var stack = es.getItemStack();
                    if (!stack.isEmpty()) count++;
                }
            }
        }
        return count;
    }

    private static void qcLog(long runId, String msg, Object... args) {
        ModLogger.debug("QuickCraft#{}: " + msg, prependRunId(runId, args));
    }

    private static void qcDebug(long runId, String msg, Object... args) {
        ModLogger.debug("QuickCraft#{}: " + msg, prependRunId(runId, args));
    }

    private static Object[] prependRunId(long runId, Object[] args) {
        var out = new Object[args.length + 1];
        out[0] = runId;
        System.arraycopy(args, 0, out, 1, args.length);
        return out;
    }

    private static String recipeId(MaterialNode node) {
        if (node == null || node.recipe == null || node.recipe.getId() == null) return "(unknown)";
        return node.recipe.getId().toString();
    }

    private static void logPlanSummary(long runId, java.util.List<MaterialNode> nodes,
                                       java.util.Map<MaterialNode, Long> neededAmounts,
                                       String tag) {
        qcLog(runId, "PLAN[{}] nodes={} completedBatches={}", tag, nodes.size(), completedBatches);
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            if (node.recipe == null || node.recipe.getId() == null) continue;
            qcLog(runId, "PLAN[{}] node#{} recipe={} divisor={} need={} outputs={}",
                    tag, i, node.recipe.getId(), node.divisor,
                    neededAmounts.getOrDefault(node, 0L), describeNodeOutputs(node));
        }
    }

    private static void logMissingFreshOutputs(long runId, java.util.List<MaterialNode> nodes, long minTimestamp) {
        for (var node : nodes) {
            if (node.recipe == null) continue;
            for (var output : node.recipe.getOutputs()) {
                var stack = output.getItemStack();
                if (stack.isEmpty()) continue;
                if (!AENetworkCache.getCachedResultSince(stack, minTimestamp).found()) {
                    qcLog(runId, "PREFLIGHT_MISSING_FRESH_OUTPUT recipe={} output={}",
                            recipeId(node), stack.getDisplayName().getString());
                }
            }
        }
    }

    private static void logOutputAvailability(long runId, java.util.List<MaterialNode> nodes,
                                              Player player, String tag, QuickCraftStorageMode storageMode) {
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            if (node.recipe == null || node.recipe.getId() == null) continue;
            var availability = getNodeAvailability(node, player, storageMode);
            qcLog(runId, "{} node#{} recipe={} total={} ae={} inv={} carried={} detail={}",
                    tag, i, node.recipe.getId(), availability.total(), availability.ae(),
                    availability.inventory(), availability.carried(), availability.detail());
        }
    }

    private static AeInputNeed findAeAutocraftInputNeed(MaterialNode node, long batches, Player player) {
        if (node == null || node.recipe == null || batches <= 0) {
            return null;
        }

        var needs = new java.util.ArrayList<AeInputNeed>();
        for (var input : node.recipe.getInputs()) {
            if (input == null || input.isEmpty()) {
                continue;
            }
            ItemStack craftableStack = ItemStack.EMPTY;
            for (var emiStack : input.getEmiStacks()) {
                var stack = emiStack.getItemStack();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                var cached = AENetworkCache.getCachedResult(stack);
                if (cached.craftable()) {
                    craftableStack = stack.copyWithCount(1);
                    break;
                }
            }
            if (craftableStack.isEmpty()) {
                continue;
            }

            long need = Math.max(1L, input.getAmount()) * batches;
            boolean merged = false;
            for (int i = 0; i < needs.size(); i++) {
                var existing = needs.get(i);
                if (ItemStack.isSameItemSameComponents(existing.stack(), craftableStack)) {
                    needs.set(i, new AeInputNeed(existing.stack(), existing.needed() + need));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                needs.add(new AeInputNeed(craftableStack, need));
            }
        }

        for (var need : needs) {
            long available = AENetworkCache.getCachedResult(need.stack()).count()
                    + countPlayerAndCarried(player, need.stack());
            if (need.needed() > available) {
                return need;
            }
        }
        return null;
    }

    private static AeCraftableShortage findTopDownAeCraftableOutputShortage(
            MaterialNode node, java.util.Map<MaterialNode, Long> neededAmounts, Player player,
            MaterialNode goalNode, long goalOutputDeduction) {
        if (node == null || node.recipe == null) {
            return null;
        }

        long needed = neededAmounts.getOrDefault(node, (long) Math.max(1, node.divisor));
        for (var output : node.recipe.getOutputs()) {
            var stack = output.getItemStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            var cached = AENetworkCache.getCachedResult(stack);
            if (!cached.found() || !cached.craftable()) {
                continue;
            }
            long available = cached.count() + countPlayerAndCarried(player, stack);
            if (node == goalNode && goalOutputDeduction > 0) {
                available = Math.max(0, available - goalOutputDeduction);
            }
            long missing = Math.max(0, needed - available);
            if (missing > 0) {
                return new AeCraftableShortage(
                        node,
                        stack.copyWithCount(1),
                        needed,
                        available,
                        (int) Math.min(Integer.MAX_VALUE, missing));
            }
        }

        if (node.children != null) {
            for (MaterialNode child : node.children) {
                AeCraftableShortage childRequest = findTopDownAeCraftableOutputShortage(
                        child, neededAmounts, player, goalNode, goalOutputDeduction);
                if (childRequest != null) {
                    return childRequest;
                }
            }
        }

        return null;
    }

    private static long countPlayerAndCarried(Player player, ItemStack template) {
        if (player == null || template == null || template.isEmpty()) {
            return 0;
        }
        long count = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            var stack = inv.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                count += stack.getCount();
            }
        }
        var carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, template)) {
            count += carried.getCount();
        }
        return count;
    }

    private static NodeAvailability getNodeAvailability(MaterialNode node, Player player, QuickCraftStorageMode storageMode) {
        if (node == null || node.recipe == null) return new NodeAvailability(0, 0, 0, 0, "");

        long bestTotal = 0;
        long bestAe = 0;
        long bestInv = 0;
        long bestCarried = 0;
        var detail = new StringBuilder();

        for (var output : node.recipe.getOutputs()) {
            var stack = output.getItemStack();
            if (stack == null || stack.isEmpty()) continue;

            long ae = 0;
            if (storageMode == QuickCraftStorageMode.AE_EXTERNAL) {
                var cached = AENetworkCache.getCachedResult(stack);
                if (cached.found() && cached.count() > 0) {
                    ae = cached.count();
                }
            } else if (storageMode == QuickCraftStorageMode.BD_EXTERNAL && player != null) {
                ae = BDProxy.getStoredAmount(player, stack);
            }

            long invCount = 0;
            long carriedCount = 0;
            if (player != null) {
                var inv = player.getInventory();
                for (int i = 0; i < inv.items.size(); i++) {
                    var invStack = inv.getItem(i);
                    if (!invStack.isEmpty() && ItemStack.isSameItemSameComponents(invStack, stack)) {
                        invCount += invStack.getCount();
                    }
                }
                var carried = player.containerMenu.getCarried();
                if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, stack)) {
                    carriedCount += carried.getCount();
                }
            }

            long total = ae + invCount + carriedCount;
            if (!detail.isEmpty()) detail.append("; ");
            detail.append(stack.getDisplayName().getString())
                    .append("{ae=").append(ae)
                    .append(",inv=").append(invCount)
                    .append(",carried=").append(carriedCount)
                    .append(",total=").append(total)
                    .append('}');

            if (total > bestTotal) {
                bestTotal = total;
                bestAe = ae;
                bestInv = invCount;
                bestCarried = carriedCount;
            }
        }

        return new NodeAvailability(bestAe, bestInv, bestCarried, bestTotal, detail.toString());
    }

    private static String describeNodeOutputs(MaterialNode node) {
        if (node == null || node.recipe == null) return "";
        var sb = new StringBuilder();
        for (var output : node.recipe.getOutputs()) {
            var stack = output.getItemStack();
            if (stack.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(stack.getDisplayName().getString()).append(" x").append(output.getAmount());
        }
        return sb.toString();
    }

    private static String describeStacks(java.util.Collection<ItemStack> stacks) {
        var sb = new StringBuilder();
        for (var stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(stack.getDisplayName().getString());
        }
        return sb.toString();
    }

    private static void appendStackAmount(StringBuilder sb, ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty()) return;
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(stack.getDisplayName().getString()).append(" x").append(amount);
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
