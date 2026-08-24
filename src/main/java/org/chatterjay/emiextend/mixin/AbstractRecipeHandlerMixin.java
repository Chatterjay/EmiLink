package org.chatterjay.emiextend.mixin;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.integration.modules.emi.EmiUseCraftingRecipeHandler;
import appeng.integration.modules.itemlists.CraftingHelper;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.CraftingTermMenu;
import org.chatterjay.emiextend.client.AEQuickCraftDelayHandler;
import org.chatterjay.emiextend.client.AENetworkCache;
import org.chatterjay.emiextend.client.ClientPacketHelper;
import org.chatterjay.emiextend.client.ae.BulkAutocraftNeed;
import org.chatterjay.emiextend.client.ae.BulkAutocraftRequest;
import org.chatterjay.emiextend.client.ae.CraftableMissingCheck;
import org.chatterjay.emiextend.client.ae.CraftableShortageCheck;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiStack;
import org.chatterjay.emiextend.network.packet.c2s.AEAutocraftAmountOverridePacket;
import org.chatterjay.emiextend.network.packet.c2s.AEAutocraftRequestPacket;
import org.chatterjay.emiextend.util.EmiCraftHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Mixin(targets = "appeng.integration.modules.emi.AbstractRecipeHandler")
public class AbstractRecipeHandlerMixin {

    @Unique
    private static final long SINGLE_CRAFT_SIGNAL = Long.MIN_VALUE;

    @Inject(method = "craft", at = @At("RETURN"), remap = false)
    private <T extends AEBaseMenu> void emilink$afterCraft(EmiRecipe recipe, EmiCraftContext<T> context, CallbackInfoReturnable<Boolean> cir) {
        boolean ctrl = Screen.hasControlDown();
        boolean quickCraftAeAutocraft = EmiCraftHelper.checkAeAutocraftFromQuickCraft();
        boolean allowAeAutocraft = ctrl || quickCraftAeAutocraft;
        boolean shift = Screen.hasShiftDown();
        boolean alt = Screen.hasAltDown();

        ModLogger.debug("AE_EMI_CTRL_CRAFT enter recipe={} category={} result={} type={} dest={} amount={} returned={} ctrl={} quickCraftAeAutocraft={} shift={} alt={} screen={} handler={}",
                recipe.getId(),
                recipe.getCategory() == null ? "null" : recipe.getCategory().getId(),
                describeOutputs(recipe),
                context.getType(),
                context.getDestination(),
                context.getAmount(),
                cir.getReturnValueZ(),
                ctrl,
                quickCraftAeAutocraft,
                shift,
                alt,
                context.getScreen() == null ? "null" : context.getScreen().getClass().getName(),
                context.getScreenHandler() == null ? "null" : context.getScreenHandler().getClass().getName());

        if (!cir.getReturnValueZ()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT stop reason=emi_handler_returned_false recipe={} inputs={}",
                    recipe.getId(), describeInputsWithCache(recipe));
            return;
        }

        EmiCraftContext.Destination dest = context.getDestination();
        if (dest == EmiCraftContext.Destination.NONE) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT stop reason=destination_none recipe={}", recipe.getId());
            return;
        }

        T menu = context.getScreenHandler();
        if (!(menu instanceof CraftingTermMenu ctm)) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT stop reason=not_crafting_terminal recipe={} menu={}",
                    recipe.getId(), menu == null ? "null" : menu.getClass().getName());
            return;
        }

        var outputSlots = ctm.getSlots(SlotSemantics.CRAFTING_RESULT);
        if (outputSlots.isEmpty()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT stop reason=no_output_slot recipe={}", recipe.getId());
            return;
        }
        int slotIndex = outputSlots.get(0).index;

        CraftableMissingCheck missingCheck = checkCraftableMissingInputs(recipe, ctm);
        if (missingCheck.anyMissing() || missingCheck.anyCraftable()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT missing-check recipe={} ingredients={} missing={} craftable={} anyMissing={} anyCraftable={} inputs={}",
                    recipe.getId(),
                    missingCheck.ingredients(),
                    missingCheck.missingSlots(),
                    missingCheck.craftableSlots(),
                    missingCheck.anyMissing(),
                    missingCheck.anyCraftable(),
                    describeInputsWithCache(recipe));
        } else if (ModLogger.isDebugEnabled()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT missing-check recipe={} anyMissing=false anyCraftable=false", recipe.getId());
        }

        CraftableShortageCheck shortageCheck = checkCraftableShortageInputs(recipe);
        if (shortageCheck.anyShortage() || shortageCheck.anyCraftableShortage()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT shortage-check recipe={} anyShortage={} anyCraftableShortage={} detail={}",
                    recipe.getId(),
                    shortageCheck.anyShortage(),
                    shortageCheck.anyCraftableShortage(),
                    shortageCheck.detail());
        } else if (ModLogger.isDebugEnabled()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT shortage-check recipe={} anyShortage=false anyCraftableShortage=false", recipe.getId());
        }

        if (dest == EmiCraftContext.Destination.INVENTORY
                && allowAeAutocraft
                && missingCheck.anyCraftable()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT decision=schedule_result_click_for_missing_autocraft recipe={} craftableSlots={} missingSlots={} trigger={}",
                    recipe.getId(), missingCheck.craftableSlots(), missingCheck.missingSlots(),
                    quickCraftAeAutocraft ? "QUICK" : "CTRL");
            sendMissingAutocraftTransferOrFallback(recipe, context, ctm, slotIndex);
            if (quickCraftAeAutocraft) {
                EmiCraftHelper.markAeAutocraftHandoff();
            }
            return;
        }

        if (dest == EmiCraftContext.Destination.INVENTORY
                && allowAeAutocraft
                && shortageCheck.anyCraftableShortage()) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT decision=schedule_result_click_for_shortage_autocraft recipe={} shortage={} trigger={}",
                    recipe.getId(), shortageCheck.detail(), quickCraftAeAutocraft ? "QUICK" : "CTRL");
            sendMissingAutocraftTransferOrFallback(recipe, context, ctm, slotIndex);
            if (quickCraftAeAutocraft) {
                EmiCraftHelper.markAeAutocraftHandoff();
            }
            return;
        }

        if (dest == EmiCraftContext.Destination.INVENTORY
                && quickCraftAeAutocraft) {
            BulkAutocraftRequest bulkRequest = computeFirstMissingAutocraftRequest(recipe,
                    EmiCraftHelper.getAeAutocraftRequestedAmount(), ctm);
            if (bulkRequest.amount() > 0 && !bulkRequest.stack().isEmpty()) {
                ModLogger.debug("AE_EMI_CTRL_CRAFT decision=direct_bulk_autocraft recipe={} item={} amount={} batches={}",
                        recipe.getId(),
                        bulkRequest.stack().getDisplayName().getString(),
                        bulkRequest.amount(),
                        EmiCraftHelper.getAeAutocraftRequestedAmount());
                ClientPacketHelper.sendToServer(new AEAutocraftRequestPacket(bulkRequest.stack(), bulkRequest.amount()));
                EmiCraftHelper.markAeAutocraftHandoff();
                return;
            }
        }

        if (context.getAmount() > 1) {
            InventoryAction action = (dest == EmiCraftContext.Destination.INVENTORY)
                    ? InventoryAction.CRAFT_ALL
                    : InventoryAction.CRAFT_STACK;
            if (dest == EmiCraftContext.Destination.INVENTORY && ctrl) {
                AEQuickCraftDelayHandler.schedule(action, slotIndex, 0, ctm.containerId,
                        context.getScreen(), ctm, recipe.getId());
                return;
            }
            ModLogger.debug("AE_EMI_CTRL_CRAFT send action={} slot={} id=0 recipe={}",
                    action, slotIndex, recipe.getId());
            PacketDistributor.sendToServer(new InventoryActionPacket(action, slotIndex, 0));
        } else if (dest == EmiCraftContext.Destination.INVENTORY) {
            if (ctrl) {
                AEQuickCraftDelayHandler.schedule(InventoryAction.CRAFT_SHIFT, slotIndex, SINGLE_CRAFT_SIGNAL,
                        ctm.containerId, context.getScreen(), ctm, recipe.getId());
                return;
            }
            ModLogger.debug("AE_EMI_CTRL_CRAFT send action={} slot={} id=SINGLE_CRAFT_SIGNAL recipe={}",
                    InventoryAction.CRAFT_SHIFT, slotIndex, recipe.getId());
            PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.CRAFT_SHIFT, slotIndex, SINGLE_CRAFT_SIGNAL));
        } else {
            ModLogger.debug("AE_EMI_CTRL_CRAFT send action={} slot={} id=0 recipe={}",
                    InventoryAction.CRAFT_ITEM, slotIndex, recipe.getId());
            PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.CRAFT_ITEM, slotIndex, 0));
        }
    }

    @Unique
    private static void sendMissingAutocraftTransferOrFallback(EmiRecipe recipe, EmiCraftContext<?> context,
                                                               CraftingTermMenu ctm, int slotIndex) {
        RecipeHolder<?> holder = getRecipeHolder(ctm.getPlayer().level(), recipe);
        if (holder != null && holder.value() instanceof CraftingRecipe craftingRecipe) {
            if (EmiCraftHelper.checkAeAutocraftFromQuickCraft()) {
                BulkAutocraftRequest overrideAmount = computeFirstMissingAutocraftRequest(recipe,
                        EmiCraftHelper.getAeAutocraftRequestedAmount(), ctm);
                if (overrideAmount.amount() > 0) {
                    ClientPacketHelper.sendToServer(new AEAutocraftAmountOverridePacket(overrideAmount.amount()));
                    ModLogger.debug("AE_EMI_CTRL_CRAFT missing-autocraft amount-override send recipe={} amount={} batches={}",
                            recipe.getId(), overrideAmount.amount(), EmiCraftHelper.getAeAutocraftRequestedAmount());
                }
            }
            CraftingHelper.performTransfer(ctm, holder.id(), craftingRecipe, true);
            ModLogger.debug("AE_EMI_CTRL_CRAFT missing-autocraft-transfer sent recipe={} holder={} craftMissing=true",
                    recipe.getId(), holder.id());
            return;
        }

        ModLogger.debug("AE_EMI_CTRL_CRAFT missing-autocraft-transfer fallback-delayed-click recipe={} holder={}",
                recipe.getId(), holder == null ? "null" : holder.value().getClass().getName());
        if (context.getAmount() > 1) {
            AEQuickCraftDelayHandler.schedule(InventoryAction.CRAFT_ALL, slotIndex, 0,
                    ctm.containerId, context.getScreen(), ctm, recipe.getId());
        } else {
            AEQuickCraftDelayHandler.schedule(InventoryAction.CRAFT_SHIFT, slotIndex, SINGLE_CRAFT_SIGNAL,
                    ctm.containerId, context.getScreen(), ctm, recipe.getId());
        }
    }

    @Unique
    private static BulkAutocraftRequest computeFirstMissingAutocraftRequest(EmiRecipe recipe, long batches,
                                                                            CraftingTermMenu menu) {
        if (recipe == null || batches <= 1) {
            return new BulkAutocraftRequest(ItemStack.EMPTY, 0);
        }

        var needs = new java.util.ArrayList<BulkAutocraftNeed>();
        for (var input : recipe.getInputs()) {
            if (input == null || input.isEmpty()) {
                continue;
            }
            long needForSlot = Math.max(1L, input.getAmount()) * batches;
            ItemStack craftableStack = ItemStack.EMPTY;
            for (var emiStack : input.getEmiStacks()) {
                ItemStack stack = emiStack.getItemStack();
                if (stack.isEmpty()) {
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
            boolean merged = false;
            for (int i = 0; i < needs.size(); i++) {
                BulkAutocraftNeed existing = needs.get(i);
                if (ItemStack.isSameItemSameComponents(existing.stack(), craftableStack)) {
                    needs.set(i, new BulkAutocraftNeed(existing.stack(), existing.needed() + needForSlot));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                needs.add(new BulkAutocraftNeed(craftableStack, needForSlot));
            }
        }

        for (var need : needs) {
            if (need.stack().isEmpty() || need.needed() <= 0) {
                continue;
            }
            long cachedStored = AENetworkCache.getCachedResult(need.stack()).count();
            long playerAvailable = countPlayerInventoryForAutocraft(menu, need.stack());
            long available = cachedStored + playerAvailable;
            long missing = Math.max(0, need.needed() - available);
            ModLogger.debug("AE_EMI_CTRL_CRAFT bulk-autocraft-candidate item={} needed={} aeStored={} playerAvailable={} available={} missing={}",
                    need.stack().getDisplayName().getString(), need.needed(), cachedStored,
                    playerAvailable, available, missing);
            if (missing > 0) {
                return new BulkAutocraftRequest(need.stack(), (int) Math.min(Integer.MAX_VALUE, missing));
            }
        }

        return new BulkAutocraftRequest(ItemStack.EMPTY, 0);
    }

    @Unique
    private static long countPlayerInventoryForAutocraft(CraftingTermMenu menu, ItemStack template) {
        if (menu == null || template == null || template.isEmpty()) {
            return 0;
        }
        long count = 0;
        var inv = menu.getPlayerInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (menu.isPlayerInventorySlotLocked(i)) {
                continue;
            }
            var stack = inv.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                count += stack.getCount();
            }
        }
        var carried = menu.getCarried();
        if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, template)) {
            count += carried.getCount();
        }
        return count;
    }

    @Unique
    private static CraftableMissingCheck checkCraftableMissingInputs(EmiRecipe recipe, CraftingTermMenu menu) {
        try {
            Map<Integer, Ingredient> ingredients = getGuiSlotToIngredientMap(recipe, menu.getPlayer().level());
            if (ingredients.isEmpty()) {
                return new CraftableMissingCheck("empty", "[]", "[]", false, false);
            }
            var missing = menu.findMissingIngredients(ingredients);
            boolean detailed = missing.anyMissing() || missing.anyCraftable() || ModLogger.isDebugEnabled();
            return new CraftableMissingCheck(
                    detailed ? describeIngredientMap(ingredients) : "omitted",
                    missing.missingSlots().toString(),
                    missing.craftableSlots().toString(),
                    missing.anyMissing(),
                    missing.anyCraftable());
        } catch (Throwable t) {
            ModLogger.warn("AE_EMI_CTRL_CRAFT missing-check-error recipe={} error={}",
                    recipe.getId(), t.toString());
            return new CraftableMissingCheck("error", "[]", "[]", false, false);
        }
    }

    @Unique
    private static CraftableShortageCheck checkCraftableShortageInputs(EmiRecipe recipe) {
        var reserved = new IdentityHashMap<ItemStack, Long>();
        boolean anyShortage = false;
        boolean anyCraftableShortage = false;
        var detail = new StringJoiner(" | ", "[", "]");

        int slot = 0;
        for (var input : recipe.getInputs()) {
            if (input == null || input.isEmpty()) {
                slot++;
                continue;
            }

            long need = Math.max(1, input.getAmount());
            boolean satisfied = false;
            boolean craftable = false;
            var options = new StringJoiner("/", "slot" + slot + "{need=" + need + ", options=", "}");

            for (var emiStack : input.getEmiStacks()) {
                var stack = emiStack.getItemStack();
                if (stack.isEmpty()) continue;

                var cached = AENetworkCache.getCachedResult(stack);
                long alreadyReserved = getReserved(reserved, stack);
                long available = Math.max(0, cached.count() - alreadyReserved);
                if (cached.craftable()) craftable = true;

                options.add(stack.getDisplayName().getString()
                        + " stored=" + cached.count()
                        + " reserved=" + alreadyReserved
                        + " available=" + available
                        + " craftable=" + cached.craftable()
                        + " cached=" + cached.found());

                if (available >= need) {
                    addReserved(reserved, stack, need);
                    satisfied = true;
                    break;
                }
            }

            if (!satisfied) {
                anyShortage = true;
                if (craftable) {
                    anyCraftableShortage = true;
                }
                detail.add(options + " shortage=true craftableShortage=" + craftable);
            } else {
                detail.add(options + " shortage=false craftableShortage=false");
            }

            slot++;
        }

        return new CraftableShortageCheck(detail.toString(), anyShortage, anyCraftableShortage);
    }

    @Unique
    private static long getReserved(IdentityHashMap<ItemStack, Long> reserved, ItemStack stack) {
        for (var entry : reserved.entrySet()) {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    @Unique
    private static void addReserved(IdentityHashMap<ItemStack, Long> reserved, ItemStack stack, long amount) {
        for (var entry : reserved.entrySet()) {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
                entry.setValue(entry.getValue() + amount);
                return;
            }
        }
        reserved.put(stack.copyWithCount(1), amount);
    }

    @Unique
    private static Map<Integer, Ingredient> getGuiSlotToIngredientMap(EmiRecipe emiRecipe, Level level) {
        RecipeHolder<?> holder = getRecipeHolder(level, emiRecipe);
        Recipe<?> recipe = holder != null ? holder.value() : null;
        if (recipe != null) {
            return EmiUseCraftingRecipeHandler.getGuiSlotToIngredientMap(recipe);
        }

        var result = new HashMap<Integer, Ingredient>();
        int max = Math.min(emiRecipe.getInputs().size(), 9);
        for (int i = 0; i < max; i++) {
            var input = emiRecipe.getInputs().get(i);
            if (input == null || input.isEmpty()) continue;
            var ingredient = Ingredient.of(input.getEmiStacks().stream()
                    .map(EmiStack::getItemStack)
                    .filter(stack -> !stack.isEmpty()));
            if (!ingredient.isEmpty()) {
                result.put(i, ingredient);
            }
        }
        return result;
    }

    @Unique
    private static RecipeHolder<?> getRecipeHolder(Level level, EmiRecipe recipe) {
        if (recipe.getBackingRecipe() != null) {
            return recipe.getBackingRecipe();
        }
        if (recipe.getId() != null) {
            return level.getRecipeManager().byKey(recipe.getId()).orElse(null);
        }
        return null;
    }

    @Unique
    private static String describeOutputs(EmiRecipe recipe) {
        var joiner = new StringJoiner(", ", "[", "]");
        for (var output : recipe.getOutputs()) {
            var stack = output.getItemStack();
            if (!stack.isEmpty()) {
                joiner.add(stack.getDisplayName().getString() + " x" + stack.getCount());
            } else if (output.getId() != null) {
                joiner.add(output.getId().toString() + " x" + output.getAmount());
            }
        }
        return joiner.toString();
    }

    @Unique
    private static String describeInputsWithCache(EmiRecipe recipe) {
        var joiner = new StringJoiner(" | ", "[", "]");
        int index = 0;
        for (var input : recipe.getInputs()) {
            if (input == null || input.isEmpty()) {
                index++;
                continue;
            }
            var stackJoiner = new StringJoiner("/", "slot" + index + "{amount=" + input.getAmount() + ", options=", "}");
            for (var emiStack : input.getEmiStacks()) {
                var stack = emiStack.getItemStack();
                if (stack.isEmpty()) {
                    stackJoiner.add(String.valueOf(emiStack.getId()));
                    continue;
                }
                var cached = AENetworkCache.getCachedResult(stack);
                stackJoiner.add(stack.getDisplayName().getString()
                        + " x" + stack.getCount()
                        + " cached(found=" + cached.found()
                        + ", stored=" + cached.count()
                        + ", craftable=" + cached.craftable() + ")");
            }
            joiner.add(stackJoiner.toString());
            index++;
        }
        return joiner.toString();
    }

    @Unique
    private static String describeIngredientMap(Map<Integer, Ingredient> ingredients) {
        var joiner = new StringJoiner(" | ", "[", "]");
        for (var entry : ingredients.entrySet()) {
            var stackJoiner = new StringJoiner("/", "slot" + entry.getKey() + "=", "");
            for (var stack : entry.getValue().getItems()) {
                if (!stack.isEmpty()) {
                    var cached = AENetworkCache.getCachedResult(stack);
                    stackJoiner.add(stack.getDisplayName().getString()
                            + " cached(found=" + cached.found()
                            + ", stored=" + cached.count()
                            + ", craftable=" + cached.craftable() + ")");
                }
            }
            joiner.add(stackJoiner.toString());
        }
        return joiner.toString();
    }
}
