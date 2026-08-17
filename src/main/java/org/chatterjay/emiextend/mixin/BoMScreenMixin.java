package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.chatterjay.emiextend.client.BDEmiInventoryAdapter;
import org.chatterjay.emiextend.client.bookmark.BomTreePageHelper;
import org.chatterjay.emiextend.util.ModLogger;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BoMScreen.class, remap = false)
public abstract class BoMScreenMixin {
    @Shadow
    public AbstractContainerScreen<?> old;

    @Shadow
    private EmiPlayerInventory playerInv;

    @Shadow
    private Bounds mode;

    @Shadow
    private Bounds batches;

    @Shadow
    private double offX;

    @Shadow
    private double offY;

    @Shadow
    public abstract float getScale();

    @Inject(
            method = "recalculateTree",
            at = @At(
                    value = "FIELD",
                    target = "Ldev/emi/emi/screen/BoMScreen;playerInv:Ldev/emi/emi/api/recipe/EmiPlayerInventory;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void emilink$useBdInventoryForBoM(CallbackInfo ci) {
        EmiPlayerInventory bdInventory = BDEmiInventoryAdapter.createBoMInventory(old);
        if (bdInventory != null) {
            playerInv = bdInventory;
        }
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void emilink$syncPageTreeAfterKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        ModLogger.debug("BOM_TREE_SCREEN key-return keyCode={} scanCode={} modifiers={} consumed={} beforeSync={} bomRecipe={} craftingMode={} batches={}",
                keyCode,
                scanCode,
                modifiers,
                cir.getReturnValueZ(),
                BomTreePageHelper.describeActiveState(),
                BoM.tree == null || BoM.tree.goal == null || BoM.tree.goal.recipe == null
                        ? "null"
                        : BoM.tree.goal.recipe.getId(),
                BoM.craftingMode,
                BoM.tree == null ? 0 : BoM.tree.batches);
        BomTreePageHelper.syncActiveModeFromBoM();
        BomTreePageHelper.refreshActiveSynthetic();
        ModLogger.debug("BOM_TREE_SCREEN key-return afterSync={}", BomTreePageHelper.describeActiveState());
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void emilink$logBoMScreenClickHead(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        logBoMScreenClick("head", mouseX, mouseY, button, null);
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void emilink$syncPageTreeAfterClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        ModLogger.debug("BOM_TREE_SCREEN click-sync before button={} consumed={} state={} bomRecipe={} craftingMode={} batches={}",
                button,
                cir.getReturnValueZ(),
                BomTreePageHelper.describeActiveState(),
                BoM.tree == null || BoM.tree.goal == null || BoM.tree.goal.recipe == null
                        ? "null"
                        : BoM.tree.goal.recipe.getId(),
                BoM.craftingMode,
                BoM.tree == null ? 0 : BoM.tree.batches);
        BomTreePageHelper.syncActiveModeFromBoM();
        BomTreePageHelper.refreshActiveSynthetic();
        ModLogger.debug("BOM_TREE_SCREEN click-sync after={}", BomTreePageHelper.describeActiveState());
        logBoMScreenClick("return", mouseX, mouseY, button, cir.getReturnValueZ());
        if (cir.getReturnValueZ()) {
            BomTreePageHelper.logCurrentState("bom-screen-click");
        }
    }

    @Inject(method = "mouseScrolled", at = @At("RETURN"))
    private void emilink$syncPageTreeAfterScroll(double mouseX, double mouseY, double horizontal, double amount, CallbackInfoReturnable<Boolean> cir) {
        ModLogger.debug("BOM_TREE_SCREEN scroll-sync before amount={} consumed={} state={} bomRecipe={} craftingMode={} batches={}",
                amount,
                cir.getReturnValueZ(),
                BomTreePageHelper.describeActiveState(),
                BoM.tree == null || BoM.tree.goal == null || BoM.tree.goal.recipe == null
                        ? "null"
                        : BoM.tree.goal.recipe.getId(),
                BoM.craftingMode,
                BoM.tree == null ? 0 : BoM.tree.batches);
        BomTreePageHelper.syncActiveModeFromBoM();
        BomTreePageHelper.refreshActiveSynthetic();
        ModLogger.debug("BOM_TREE_SCREEN scroll-sync after={}", BomTreePageHelper.describeActiveState());
    }

    private void logBoMScreenClick(String phase, double mouseX, double mouseY, int button, Boolean consumed) {
        try {
            float scale = getScale();
            int localX = (int) ((mouseX - ((BoMScreen) (Object) this).width / 2) / scale - offX);
            int localY = (int) ((mouseY - ((BoMScreen) (Object) this).height / 2) / scale - offY);
            Object hover = getBoMScreenHover(mouseX, mouseY);
            boolean modeHit = mode != null && mode.contains(localX, localY);
            boolean batchesHit = batches != null && BoM.tree != null && batches.contains(localX, localY);
            ModLogger.debug("BOM_TREE_SCREEN click-{} button={} consumed={} screenXY=({}, {}) localXY=({}, {}) scale={} modeHit={} batchesHit={} hover={} bomRecipe={} craftingMode={}",
                    phase,
                    button,
                    consumed == null ? "n/a" : consumed,
                    (int) mouseX,
                    (int) mouseY,
                    localX,
                    localY,
                    scale,
                    modeHit,
                    batchesHit,
                    describeHover(hover),
                    BoM.tree == null || BoM.tree.goal == null || BoM.tree.goal.recipe == null
                            ? "null"
                            : BoM.tree.goal.recipe.getId(),
                    BoM.craftingMode);
        } catch (Throwable t) {
            ModLogger.warn("BOM_TREE_SCREEN click-log-error phase={} error={}: {}",
                    phase, t.getClass().getName(), t.getMessage());
        }
    }

    private static String describeHover(Object hover) {
        if (hover == null) {
            return "none";
        }
        if (hover instanceof String text) {
            return text;
        }
        try {
            Object stack = getField(hover, "stack");
            Object node = getField(hover, "node");
            Object resolve = getField(hover, "resolve");
            Object category = getField(hover, "category");
            return "stack=" + describeIngredient(stack)
                    + ",node=" + describeNode(node)
                    + ",resolve=" + describeNode(resolve)
                    + ",category=" + describeCategory(category);
        } catch (Throwable t) {
            return hover.getClass().getName();
        }
    }

    private Object getBoMScreenHover(double mouseX, double mouseY) {
        try {
            var method = ((BoMScreen) (Object) this).getClass().getDeclaredMethod("getHoveredStack", int.class, int.class);
            method.setAccessible(true);
            return method.invoke(this, (int) mouseX, (int) mouseY);
        } catch (Throwable t) {
            return "method-missing:" + t.getClass().getSimpleName();
        }
    }

    private static Object getField(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static String describeIngredient(Object value) {
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

    private static String describeNode(Object value) {
        if (!(value instanceof MaterialNode node)) {
            return "none";
        }
        String recipe = node.recipe == null || node.recipe.getId() == null ? "null" : node.recipe.getId().toString();
        return "recipe=" + recipe
                + ",amount=" + node.amount
                + ",totalNeeded=" + node.totalNeeded
                + ",batches=" + node.neededBatches;
    }

    private static String describeCategory(Object value) {
        if (value == null) {
            return "none";
        }
        try {
            var method = value.getClass().getMethod("getId");
            Object id = method.invoke(value);
            return String.valueOf(id);
        } catch (Throwable t) {
            return value.getClass().getName();
        }
    }
}
