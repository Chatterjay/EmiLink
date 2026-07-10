package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialNode;
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
import org.chatterjay.emiextend.config.EmiLinkConfig;
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
import net.minecraft.world.entity.player.Player;
import org.chatterjay.emiextend.network.packet.c2s.BDActionPacket;
import org.chatterjay.emiextend.network.packet.c2s.AEDepositPacket;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class InputEvents {
    private InputEvents() {}

    private static boolean fillSearchHandled = false;

    /** Active multi-tick crafting job (one batch per game tick to avoid freezing). */
    private static CraftingJob currentJob = null;

    private static class CraftingJob {
        final AbstractContainerScreen<?> screen;
        final MaterialNode goalNode;
        final java.util.List<MaterialNode> nodes;
        final java.util.Map<MaterialNode, Long> neededAmounts;
        int nodeIndex;
        long remainingBatches;

        CraftingJob(AbstractContainerScreen<?> screen, MaterialNode goalNode,
                    java.util.List<MaterialNode> nodes, java.util.Map<MaterialNode, Long> neededAmounts) {
            this.screen = screen;
            this.goalNode = goalNode;
            this.nodes = nodes;
            this.neededAmounts = neededAmounts;
            this.nodeIndex = 0;
            this.remainingBatches = 0;
        }
    }

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
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_P && EmiLinkConfig.ENABLE_QUICK_CRAFT_TAB.get()) {
            onQuickCraftTabKey(event);
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

    /** Walk BoM tree and craft each node; intermediates → AE, only the final goal → inventory. */
    private static void onQuickCraftTabKey(ScreenEvent.KeyPressed.Pre event) {
        var mc = Minecraft.getInstance();
        var handled = EmiApi.getHandledScreen();
        if (handled == null) {
            event.setCanceled(true);
            ModLogger.debug("QuickCraft: no handled screen");
            return;
        }
        ModLogger.info("QuickCraft: handled={}", handled.getClass().getName());

        if (BoM.tree == null || BoM.tree.goal == null) {
            event.setCanceled(true);
            ModLogger.debug("QuickCraft: no BoM tree");
            return;
        }

        // Collect all craftable nodes bottom-up (leaves first)
        var nodes = new java.util.ArrayList<MaterialNode>();
        collectRecipeNodes(BoM.tree.goal, nodes);
        if (nodes.isEmpty()) {
            event.setCanceled(true);
            ModLogger.info("QuickCraft: no craftable nodes in BoM tree");
            return;
        }
        ModLogger.info("QuickCraft: BoM tree has {} craftable nodes", nodes.size());

        // The last node in bottom-up order is the goal (final item)
        MaterialNode goalNode = nodes.get(nodes.size() - 1);

        // Compute needed amounts top-down from recipe input/output ratios.
        // Use tree.batches as the initial multiplier (set by EMI's +/- buttons in BoM UI).
        long initialBatches = Math.max(1, BoM.tree.batches);
        ModLogger.info("QuickCraft: tree.batches={}, using initialBatches={}", BoM.tree.batches, initialBatches);
        var neededAmounts = new java.util.LinkedHashMap<MaterialNode, Long>();
        computeCraftAmounts(goalNode, initialBatches, neededAmounts);

        if (currentJob != null) {
            ModLogger.info("QuickCraft: already crafting, ignoring");
            return;
        }
        currentJob = new CraftingJob(handled, goalNode, nodes, neededAmounts);
        event.setCanceled(true);
        ModLogger.info("QuickCraft: job started with {} nodes", nodes.size());
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

    /** After ALL nodes are done: scan player inventory and deposit every item that matches an intermediate recipe output. */
    private static void depositAllIntermediates(Player player, CraftingJob job) {
        var inv = player.getInventory();
        var outputsToDeposit = new java.util.ArrayList<dev.emi.emi.api.stack.EmiStack>();
        for (var node : job.nodes) {
            if (node == job.goalNode || node.recipe == null) continue;
            outputsToDeposit.addAll(node.recipe.getOutputs());
        }
        if (outputsToDeposit.isEmpty()) return;

        int total = 0;
        for (int i = 0; i < inv.items.size(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (matchesOutput(stack, outputsToDeposit)) {
                PacketDistributor.sendToServer(new AEDepositPacket(stack.copy(), i));
                inv.setItem(i, ItemStack.EMPTY);
                total += stack.getCount();
            }
        }

        // Also sweep the cursor in case the last item didn't reach inventory yet.
        var carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && matchesOutput(carried, outputsToDeposit)) {
            PacketDistributor.sendToServer(new AEDepositPacket(carried.copy(), -1));
            player.containerMenu.setCarried(ItemStack.EMPTY);
            total += carried.getCount();
        }

        if (total > 0) ModLogger.info("QuickCraft: deposited {} intermediate items to AE at end", total);
    }

    /** Process one batch per game tick to avoid freezing. */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
        if (currentJob == null) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            ModLogger.info("QuickCraft: player gone, aborting job");
            currentJob = null;
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
            ModLogger.info("QuickCraft: screen changed (current={}, job={}), aborting", screen, currentJob.screen);
            currentJob = null;
            return;
        }

        while (currentJob.nodeIndex < currentJob.nodes.size()) {
            var node = currentJob.nodes.get(currentJob.nodeIndex);
            if (node.recipe == null || node.recipe.getId() == null) {
                ModLogger.debug("QuickCraft:   skip null-recipe node");
                currentJob.nodeIndex++;
                continue;
            }

            // Initialize/replenish remaining batches for this node
            if (currentJob.remainingBatches <= 0) {
                long needed = currentJob.neededAmounts.getOrDefault(node, (long) node.divisor);
                currentJob.remainingBatches = (needed + node.divisor - 1) / node.divisor;
                if (currentJob.remainingBatches > 100000) currentJob.remainingBatches = 100000;
                ModLogger.info("QuickCraft: START {} need={} div={} totalBatches={}",
                        node.recipe.getId(), needed, node.divisor, currentJob.remainingBatches);
            }

            // All nodes use INVENTORY — the AE2 terminal handles placement.
            boolean ok = EmiRecipeFiller.performFill(node.recipe, currentJob.screen,
                    EmiCraftContext.Type.FILL_BUTTON, EmiCraftContext.Destination.INVENTORY, 1);
            if (!ok) {
                ModLogger.info("QuickCraft: FAIL {} at batch {} (skip)",
                        node.recipe.getId(), currentJob.remainingBatches);
                currentJob.nodeIndex++;
                currentJob.remainingBatches = 0;
                continue;
            }

            currentJob.remainingBatches--;
            boolean done = currentJob.remainingBatches <= 0;

            if (done) {
                if (node == currentJob.goalNode) {
                    ModLogger.info("QuickCraft: DONE {} (final goal)", node.recipe.getId());
                } else {
                    ModLogger.info("QuickCraft: DONE {} (will deposit at end)", node.recipe.getId());
                }
                currentJob.nodeIndex++;
            }

            ModLogger.info("QuickCraft: BATCH {} {} ({} remaining)",
                    node.recipe.getId(), ok ? "OK" : "FAIL", currentJob.remainingBatches);

            return; // One batch per tick
        }

        ModLogger.info("QuickCraft: ALL DONE, sweeping intermediates");
        depositAllIntermediates(mc.player, currentJob);
        currentJob = null;
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
    private static void computeCraftAmounts(MaterialNode node, long batchesNeeded, java.util.Map<MaterialNode, Long> out) {
        out.merge(node, batchesNeeded * node.divisor, Long::sum);
        String nodeId = (node.recipe != null && node.recipe.getId() != null) ? node.recipe.getId().toString() : "(raw)";
        ModLogger.info("QuickCraft: compute node={} batchesNeeded={} divisor={} => totalNeed={}",
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
                    ModLogger.debug("QuickCraft:   input amount={} -> child {}", perBatch, child.recipe.getId());
                    break;
                }
            }
        }

        for (var entry : childNeeded.entrySet()) {
            var child = entry.getKey();
            long needed = entry.getValue();
            long batches = (needed + child.divisor - 1) / child.divisor;
            ModLogger.info("QuickCraft:   -> child {} need={} div={} batches={}", child.recipe.getId(), needed, child.divisor, batches);
            computeCraftAmounts(child, batches, out);
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
