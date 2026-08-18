package org.chatterjay.emilink.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.client.handler.WrapAsBookHandler;
import org.chatterjay.emilink.util.ModLogger;
import org.chatterjay.emilink.util.ProviderSearchHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeScreen.class, remap = false)
public class RecipeScreenMixin {

    @Shadow(remap = false)
    public AbstractContainerScreen<?> old;

    @Shadow(remap = false)
    private int x, y, backgroundWidth;

    @Shadow(remap = false)
    public EmiRecipeCategory getFocusedCategory() { return null; }

    @Unique
    private String emilink$lastProviderSearchKey;

    @Unique
    private String emilink$lastRenderState;

    @Unique
    private String emilink$lastButtonState;

    @Unique
    private boolean emilink$hasFocusedRecipeContext;

    @Unique
    private void emilink$saveCategory(String path) {
        emilink$saveCategory(path, false);
    }

    @Unique
    private void emilink$saveCategory(String path, boolean selectedRecipe) {
        if (path == null || path.isBlank() || "jemi".equals(path)) {
            ModLogger.debug("RecipeScreenMixin: skip saving category '{}'", path);
            return;
        }
        if (!selectedRecipe && emilink$hasFocusedRecipeContext && !path.equals(emilink$lastProviderSearchKey)) {
            ModLogger.debug("RecipeScreenMixin: retaining selected recipe category '{}' instead of fallback '{}'",
                    emilink$lastProviderSearchKey, path);
            return;
        }
        if (path.equals(emilink$lastProviderSearchKey)) return;
        emilink$lastProviderSearchKey = path;
        ProviderSearchHelper.setLastProcessingName(path);
        ProviderSearchHelper.setLastFocusedRecipeCategory(path);
        ModLogger.debug("RecipeScreenMixin: saved category '{}' (from focusCategory/focusRecipe)", path);
    }

    @Unique
    private void emilink$rememberRecipeOutput(EmiRecipe recipe) {
        if (recipe == null) {
            ModLogger.debug("WB_FLOW RecipeFocus: recipe=null");
            return;
        }
        try {
            var outputs = recipe.getOutputs();
            ModLogger.debug("WB_FLOW RecipeFocus: recipeClass={} id={} category={} outputs={}",
                    recipe.getClass().getName(), recipe.getId(),
                    recipe.getCategory() == null || recipe.getCategory().getId() == null ? "null" : recipe.getCategory().getId(),
                    outputs == null ? "null" : outputs.size());
            if (outputs != null) {
                for (int i = 0; i < outputs.size(); i++) {
                    EmiStack output = outputs.get(i);
                    ModLogger.debug("WB_FLOW RecipeFocus: output[{}]={}", i, emilink$describeEmiStack(output));
                    if (output == null || output.isEmpty()) continue;
                    var stack = output.getItemStack();
                    if (!stack.isEmpty()) {
                        WrapAsBookHandler.rememberRecipeOutput(stack);
                        ModLogger.debug("WB_FLOW RecipeFocus: cached output[{}] {}", i, emilink$describeItemStack(stack));
                        return;
                    }
                }
            }
            ModLogger.debug("WB_FLOW RecipeFocus: recipe has no item output for WB cache");
        } catch (Throwable e) {
            ModLogger.debug("WB_FLOW RecipeFocus: failed to remember recipe output error={}:{}", e.getClass().getName(), e.getMessage());
            ModLogger.debug("WB_FLOW RecipeFocus: stacktrace", e);
        }
    }

    @Unique
    private static final int BTN_SIZE = 14;

    @Unique
    private static boolean emilink$isEncodingScreen(Screen screen) {
        try {
            Class<?> encodingClass = Class.forName("appeng.client.gui.me.items.PatternEncodingTermScreen");
            if (encodingClass.isInstance(screen)) return true;
        } catch (Throwable ignored) {}
        try {
            Class<?> guiClass = Class.forName("com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal");
            return guiClass.isInstance(screen);
        } catch (Throwable e) {
            return false;
        }
    }

    @Inject(method = "focusCategory", at = @At("HEAD"), require = 0)
    private void emilink$onFocusCategory(EmiRecipeCategory category, CallbackInfo ci) {
        ModLogger.debug("WB_FLOW CategoryFocus: category={} id={}",
                category == null ? "null" : category.getClass().getName(),
                category == null ? "null" : category.getId());
        if (category == null || category.getId() == null) return;
        emilink$saveCategory(category.getId().getPath());
    }

    @Inject(method = "focusRecipe", at = @At("HEAD"), require = 0)
    private void emilink$onFocusRecipe(EmiRecipe recipe, CallbackInfo ci) {
        ModLogger.debug("WB_FLOW FocusRecipeHook: recipe={} id={} category={}",
                recipe == null ? "null" : recipe.getClass().getName(),
                recipe == null ? "null" : recipe.getId(),
                recipe == null || recipe.getCategory() == null ? "null" : recipe.getCategory().getId());
        if (recipe != null && recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            emilink$hasFocusedRecipeContext = true;
            emilink$saveCategory(recipe.getCategory().getId().getPath(), true);
        }
        emilink$rememberRecipeOutput(recipe);
    }

    @Inject(method = {"render", "renderWidget", "m_88315_"}, at = @At("TAIL"), require = 0)
    private void emilink$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        boolean encodingScreen = emilink$isEncodingScreen(old);
        boolean active = WrapAsBookHandler.isActive();
        String renderState = (old == null ? "null" : old.getClass().getName()) + "|" + encodingScreen + "|" + x + "|" + y + "|" + backgroundWidth + "|" + active;
        if (!renderState.equals(emilink$lastRenderState)) {
            emilink$lastRenderState = renderState;
            ModLogger.debug("WB_FLOW RenderState: old={} isEncodingTerm={} x={} y={} bgW={} active={}",
                    old == null ? "null" : old.getClass().getName(), encodingScreen, x, y, backgroundWidth, active);
        }

        if (!encodingScreen) return;

        try {
            EmiRecipeCategory category = getFocusedCategory();
            if (category != null && category.getId() != null) {
                emilink$saveCategory(category.getId().getPath());
            }
        } catch (Throwable e) {
            ModLogger.warn("RecipeScreenMixin: failed to get focused category: {}", e.getMessage());
        }

        if (!Config.ENABLE_WRAP_BOOK.get()) {
            ModLogger.debug("WB_FLOW RenderState: WB button disabled by config");
            return;
        }

        int btnX = x + backgroundWidth;
        int btnY = y + 5;
        int s = BTN_SIZE;
        String buttonState = "button|" + btnX + "|" + btnY + "|" + active;
        if (!buttonState.equals(emilink$lastButtonState)) {
            emilink$lastButtonState = buttonState;
            ModLogger.debug("WB_FLOW RenderButton: at=({}, {}) active={} x={} bgW={}", btnX, btnY, active, x, backgroundWidth);
        }

        Font font = Minecraft.getInstance().font;
        String label = active ? "ON" : "WB";
        int textX = btnX + (s - font.width(label)) / 2;
        int textY = btnY + (s - font.lineHeight) / 2;
        guiGraphics.drawString(font, label, textX, textY, active ? 0xFFFFAA : 0xAAAAAA, true);

        if (mouseX >= btnX && mouseX < btnX + s && mouseY >= btnY && mouseY < btnY + s) {
            String tooltipKey = active ? "emilink.tooltip.wrap_as_book.on" : "emilink.tooltip.wrap_as_book.off";
            guiGraphics.renderTooltip(font, Component.translatable(tooltipKey), mouseX, mouseY);
        }
    }

    @Inject(method = {"mouseClicked", "m_6375_"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void emilink$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!emilink$isEncodingScreen(old)) {
            ModLogger.debug("WB_FLOW Click: ignored old={}", old == null ? "null" : old.getClass().getSimpleName());
            return;
        }
        if (!Config.ENABLE_WRAP_BOOK.get()) return;
        if (button != 0) return;

        int btnX = x + backgroundWidth;
        int btnY = y + 5;
        int s = BTN_SIZE;
        boolean hit = mouseX >= btnX && mouseX < btnX + s && mouseY >= btnY && mouseY < btnY + s;
        ModLogger.debug("WB_FLOW Click: mouse=({}, {}) button={} btn=({}, {}) size={} hit={} activeBefore={}",
                mouseX, mouseY, button, btnX, btnY, s, hit, WrapAsBookHandler.isActive());

        if (hit) {
            boolean before = WrapAsBookHandler.isActive();
            WrapAsBookHandler.toggle();
            boolean after = WrapAsBookHandler.isActive();
            ModLogger.debug("WB_FLOW Click: toggled {} -> {}", before, after);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static String emilink$describeEmiStack(EmiStack stack) {
        if (stack == null) return "null";
        try {
            var itemStack = stack.getItemStack();
            return "class=" + stack.getClass().getName()
                    + ", empty=" + stack.isEmpty()
                    + ", amount=" + stack.getAmount()
                    + ", key=" + stack.getKey()
                    + ", item=" + emilink$describeItemStack(itemStack);
        } catch (Throwable e) {
            return "class=" + stack.getClass().getName() + ", describe-error=" + e.getClass().getName() + ":" + e.getMessage();
        }
    }

    @Unique
    private static String emilink$describeItemStack(net.minecraft.world.item.ItemStack stack) {
        if (stack == null) return "null";
        if (stack.isEmpty()) return "empty";
        String id;
        try {
            id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Throwable e) {
            id = stack.getItem().getClass().getName();
        }
        String name;
        try {
            name = stack.getHoverName().getString();
        } catch (Throwable e) {
            name = "<name-error:" + e.getClass().getSimpleName() + ">";
        }
        return id + " x" + stack.getCount() + " name='" + name + "' tag=" + stack.getTag();
    }
}
