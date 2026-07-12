package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.screen.BoMScreen;
import dev.emi.emi.widget.RecipeButtonWidget;
import dev.emi.emi.widget.RecipeTreeButtonWidget;
import net.minecraft.client.Minecraft;
import org.chatterjay.emiextend.client.BomTreePageHelper;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeTreeButtonWidget.class, remap = false)
public class RecipeTreeButtonWidgetMixin {
    @Unique
    private boolean emilink$wasCurrentTreeButton;

    @Unique
    private boolean emilink$previousCraftingMode;

    @Unique
    private long emilink$previousBatches;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void emilink$logRecipeTreeButtonHead(int mouseX, int mouseY, int button,
                                                 CallbackInfoReturnable<Boolean> cir) {
        EmiRecipe recipe = emilink$getRecipe();
        emilink$wasCurrentTreeButton = BoM.tree != null
                && BoM.tree.goal != null
                && BoM.tree.goal.recipe == recipe;
        emilink$previousCraftingMode = BoM.craftingMode;
        emilink$previousBatches = BoM.tree == null ? 1 : Math.max(1, BoM.tree.batches);
        ModLogger.info("BOM_TREE_BUTTON click-head button={} xy=({}, {}) beforeRecipe={} beforeCraftingMode={} beforeBatches={} activePage={}",
                button,
                mouseX,
                mouseY,
                BoM.tree == null || BoM.tree.goal == null || BoM.tree.goal.recipe == null
                        ? "null"
                        : BoM.tree.goal.recipe.getId(),
                BoM.craftingMode,
                emilink$previousBatches,
                BomTreePageHelper.getActiveFavoritePage());
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void emilink$syncRecipeTreeButtonReturn(int mouseX, int mouseY, int button,
                                                    CallbackInfoReturnable<Boolean> cir) {
        EmiRecipe recipe = emilink$getRecipe();
        if (cir.getReturnValueZ() && emilink$wasCurrentTreeButton && emilink$previousCraftingMode) {
            if (BoM.tree != null) {
                BoM.tree.batches = emilink$previousBatches;
            }
            BoM.craftingMode = true;
            if (Minecraft.getInstance().screen instanceof BoMScreen bomScreen) {
                bomScreen.recalculateTree();
            }
            ModLogger.info("BOM_TREE_BUTTON preserved current tree state recipe={} previousCraftingMode={} forcedCraftingMode=true batches={}",
                    recipe == null || recipe.getId() == null ? "null" : recipe.getId(),
                    emilink$previousCraftingMode,
                    emilink$previousBatches);
        } else if (cir.getReturnValueZ() && BoM.tree != null) {
            if (Minecraft.getInstance().screen instanceof BoMScreen bomScreen) {
                bomScreen.recalculateTree();
            }
            ModLogger.info("BOM_TREE_BUTTON opened normal tree recipe={} previousCraftingMode={} batches={}",
                    BoM.tree.goal == null || BoM.tree.goal.recipe == null || BoM.tree.goal.recipe.getId() == null
                            ? "null"
                            : BoM.tree.goal.recipe.getId(),
                    emilink$previousCraftingMode,
                    BoM.tree.batches);
        }
        ModLogger.info("BOM_TREE_BUTTON click-return result={} button={} afterRecipe={} afterCraftingMode={} afterBatches={} activePage={}",
                cir.getReturnValueZ(),
                button,
                BoM.tree == null || BoM.tree.goal == null || BoM.tree.goal.recipe == null
                        ? "null"
                        : BoM.tree.goal.recipe.getId(),
                BoM.craftingMode,
                BoM.tree == null ? 0 : BoM.tree.batches,
                BomTreePageHelper.getActiveFavoritePage());
        BomTreePageHelper.storeNewActiveTree();
        BomTreePageHelper.refreshActiveSynthetic();
        BomTreePageHelper.logCurrentState("recipe-tree-button-return");
    }

    @Unique
    private EmiRecipe emilink$getRecipe() {
        return ((RecipeButtonWidgetAccessor) (RecipeButtonWidget) (Object) this).emilink$getRecipe();
    }
}
