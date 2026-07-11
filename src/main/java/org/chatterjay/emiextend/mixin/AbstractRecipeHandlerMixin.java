package org.chatterjay.emiextend.mixin;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.integration.modules.emi.EmiUseCraftingRecipeHandler;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.CraftingTermMenu;
import org.chatterjay.emiextend.client.AEQuickCraftDelayHandler;
import org.chatterjay.emiextend.client.AENetworkCache;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
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
        boolean shift = Screen.hasShiftDown();
        boolean alt = Screen.hasAltDown();

        ModLogger.info("AE_EMI_CTRL_CRAFT enter recipe={} category={} result={} type={} dest={} amount={} returned={} ctrl={} shift={} alt={} screen={} handler={}",
                recipe.getId(),
                recipe.getCategory() == null ? "null" : recipe.getCategory().getId(),
                describeOutputs(recipe),
                context.getType(),
                context.getDestination(),
                context.getAmount(),
                cir.getReturnValueZ(),
                ctrl,
                shift,
                alt,
                context.getScreen() == null ? "null" : context.getScreen().getClass().getName(),
                context.getScreenHandler() == null ? "null" : context.getScreenHandler().getClass().getName());

        if (!cir.getReturnValueZ()) {
            ModLogger.info("AE_EMI_CTRL_CRAFT stop reason=emi_handler_returned_false recipe={} inputs={}",
                    recipe.getId(), describeInputsWithCache(recipe));
            return;
        }

        EmiCraftContext.Destination dest = context.getDestination();
        if (dest == EmiCraftContext.Destination.NONE) {
            ModLogger.info("AE_EMI_CTRL_CRAFT stop reason=destination_none recipe={}", recipe.getId());
            return;
        }

        T menu = context.getScreenHandler();
        if (!(menu instanceof CraftingTermMenu ctm)) {
            ModLogger.info("AE_EMI_CTRL_CRAFT stop reason=not_crafting_terminal recipe={} menu={}",
                    recipe.getId(), menu == null ? "null" : menu.getClass().getName());
            return;
        }

        CraftableMissingCheck missingCheck = checkCraftableMissingInputs(recipe, ctm);
        ModLogger.info("AE_EMI_CTRL_CRAFT missing-check recipe={} ingredients={} missing={} craftable={} anyMissing={} anyCraftable={} inputs={}",
                recipe.getId(),
                missingCheck.ingredients(),
                missingCheck.missingSlots(),
                missingCheck.craftableSlots(),
                missingCheck.anyMissing(),
                missingCheck.anyCraftable(),
                describeInputsWithCache(recipe));

        CraftableShortageCheck shortageCheck = checkCraftableShortageInputs(recipe);
        ModLogger.info("AE_EMI_CTRL_CRAFT shortage-check recipe={} anyShortage={} anyCraftableShortage={} detail={}",
                recipe.getId(),
                shortageCheck.anyShortage(),
                shortageCheck.anyCraftableShortage(),
                shortageCheck.detail());

        if (dest == EmiCraftContext.Destination.INVENTORY
                && ctrl
                && missingCheck.anyCraftable()) {
            ModLogger.info("AE_EMI_CTRL_CRAFT decision=skip_result_click_let_ae_schedule recipe={} craftableSlots={} missingSlots={}",
                    recipe.getId(), missingCheck.craftableSlots(), missingCheck.missingSlots());
            return;
        }

        if (dest == EmiCraftContext.Destination.INVENTORY
                && ctrl
                && shortageCheck.anyCraftableShortage()) {
            ModLogger.info("AE_EMI_CTRL_CRAFT decision=skip_result_click_wait_for_missing_autocraft recipe={} shortage={}",
                    recipe.getId(), shortageCheck.detail());
            return;
        }

        var outputSlots = ctm.getSlots(SlotSemantics.CRAFTING_RESULT);
        if (outputSlots.isEmpty()) {
            ModLogger.info("AE_EMI_CTRL_CRAFT stop reason=no_output_slot recipe={}", recipe.getId());
            return;
        }
        int slotIndex = outputSlots.get(0).index;

        if (context.getAmount() > 1) {
            InventoryAction action = (dest == EmiCraftContext.Destination.INVENTORY)
                    ? InventoryAction.CRAFT_ALL
                    : InventoryAction.CRAFT_STACK;
            if (dest == EmiCraftContext.Destination.INVENTORY && ctrl) {
                AEQuickCraftDelayHandler.schedule(action, slotIndex, 0, ctm.containerId,
                        context.getScreen(), ctm, recipe.getId());
                return;
            }
            ModLogger.info("AE_EMI_CTRL_CRAFT send action={} slot={} id=0 recipe={}",
                    action, slotIndex, recipe.getId());
            PacketDistributor.sendToServer(new InventoryActionPacket(action, slotIndex, 0));
        } else if (dest == EmiCraftContext.Destination.INVENTORY) {
            if (ctrl) {
                AEQuickCraftDelayHandler.schedule(InventoryAction.CRAFT_SHIFT, slotIndex, SINGLE_CRAFT_SIGNAL,
                        ctm.containerId, context.getScreen(), ctm, recipe.getId());
                return;
            }
            ModLogger.info("AE_EMI_CTRL_CRAFT send action={} slot={} id=SINGLE_CRAFT_SIGNAL recipe={}",
                    InventoryAction.CRAFT_SHIFT, slotIndex, recipe.getId());
            PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.CRAFT_SHIFT, slotIndex, SINGLE_CRAFT_SIGNAL));
        } else {
            ModLogger.info("AE_EMI_CTRL_CRAFT send action={} slot={} id=0 recipe={}",
                    InventoryAction.CRAFT_ITEM, slotIndex, recipe.getId());
            PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.CRAFT_ITEM, slotIndex, 0));
        }
    }

    @Unique
    private record CraftableMissingCheck(String ingredients, String missingSlots, String craftableSlots,
                                         boolean anyMissing, boolean anyCraftable) {}

    @Unique
    private record CraftableShortageCheck(String detail, boolean anyShortage, boolean anyCraftableShortage) {}

    @Unique
    private static CraftableMissingCheck checkCraftableMissingInputs(EmiRecipe recipe, CraftingTermMenu menu) {
        try {
            Map<Integer, Ingredient> ingredients = getGuiSlotToIngredientMap(recipe, menu.getPlayer().level());
            if (ingredients.isEmpty()) {
                return new CraftableMissingCheck("empty", "[]", "[]", false, false);
            }
            var missing = menu.findMissingIngredients(ingredients);
            return new CraftableMissingCheck(
                    describeIngredientMap(ingredients),
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
