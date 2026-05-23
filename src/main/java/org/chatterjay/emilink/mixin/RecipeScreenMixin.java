package org.chatterjay.emilink.mixin;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private void emilink$saveCategory(String path) {
        if (path == null || path.isBlank() || "jemi".equals(path)) {
            ModLogger.info("RecipeScreenMixin: skip saving category '{}'", path);
            return;
        }
        if (path.equals(emilink$lastProviderSearchKey)) return;
        emilink$lastProviderSearchKey = path;
        ProviderSearchHelper.setLastProcessingName(path);
        ProviderSearchHelper.setLastFocusedRecipeCategory(path);
        ModLogger.info("RecipeScreenMixin: saved category '{}' (from focusCategory/focusRecipe)", path);
    }

    @Unique
    private static final int BTN_SIZE = 14;

    @Unique
    private static boolean emilink$isEncodingScreen(Screen screen) {
        if (screen instanceof PatternEncodingTermScreen) return true;
        try {
            Class<?> guiClass = Class.forName("com.glodblock.github.extendedae.client.gui.GuiExPatternTerminal");
            return guiClass.isInstance(screen);
        } catch (Throwable e) {
            return false;
        }
    }

    // Capture category when player clicks a category tab (works in EMI 1.1.24+)
    @Inject(method = "focusCategory", at = @At("HEAD"), require = 0)
    private void emilink$onFocusCategory(EmiRecipeCategory category, CallbackInfo ci) {
        if (category == null || category.getId() == null) return;
        emilink$saveCategory(category.getId().getPath());
    }

    // Capture category when player selects a specific recipe (works in EMI 1.1.24+)
    @Inject(method = "focusRecipe", at = @At("HEAD"), require = 0)
    private void emilink$onFocusRecipe(EmiRecipe recipe, CallbackInfo ci) {
        if (recipe == null || recipe.getCategory() == null || recipe.getCategory().getId() == null) return;
        emilink$saveCategory(recipe.getCategory().getId().getPath());
    }

    @Inject(method = {"render", "renderWidget"}, at = @At("TAIL"), require = 0)
    private void emilink$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ModLogger.info("RecipeScreenMixin: render called, old={}, isEncodingTerm={}",
                old == null ? "null" : old.getClass().getName(),
                old instanceof PatternEncodingTermScreen);

        if (!emilink$isEncodingScreen(old)) return;

        // Provider search key (fallback for EMI versions without focusCategory support)
        try {
            EmiRecipeCategory category = getFocusedCategory();
            if (category != null && category.getId() != null) {
                emilink$saveCategory(category.getId().getPath());
            }
        } catch (Throwable e) {
            ModLogger.warn("RecipeScreenMixin: failed to get focused category: {}", e.getMessage());
        }

        // WB button
        if (!Config.ENABLE_WRAP_BOOK.get()) {
            ModLogger.info("RecipeScreenMixin: WB button disabled by config");
            return;
        }

        boolean active = WrapAsBookHandler.isActive();
        int btnX = x + backgroundWidth;
        int btnY = y + 5;
        int s = BTN_SIZE;
        ModLogger.info("RecipeScreenMixin: drawing WB button at ({},{}), active={}, x={}, bgW={}",
                btnX, btnY, active, x, backgroundWidth);

        Font font = Minecraft.getInstance().font;
        String label = active ? "书" : "WB";
        int textX = btnX + (s - font.width(label)) / 2;
        int textY = btnY + (s - font.lineHeight) / 2;
        guiGraphics.drawString(font, label, textX, textY, active ? 0xFFFFAA : 0xAAAAAA, true);

        if (mouseX >= btnX && mouseX < btnX + s && mouseY >= btnY && mouseY < btnY + s) {
            String tooltipKey = active ? "emilink.tooltip.wrap_as_book.on" : "emilink.tooltip.wrap_as_book.off";
            guiGraphics.renderTooltip(font, Component.translatable(tooltipKey), mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void emilink$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!emilink$isEncodingScreen(old)) {
            ModLogger.info("RecipeScreenMixin: click ignored, old is {}",
                    old == null ? "null" : old.getClass().getSimpleName());
            return;
        }
        if (!Config.ENABLE_WRAP_BOOK.get()) return;
        if (button != 0) return;

        int btnX = x + backgroundWidth;
        int btnY = y + 5;
        int s = BTN_SIZE;
        boolean hit = mouseX >= btnX && mouseX < btnX + s && mouseY >= btnY && mouseY < btnY + s;
        ModLogger.info("RecipeScreenMixin: click at ({},{}), btn=({},{}) size={} hit={}",
                mouseX, mouseY, btnX, btnY, s, hit);

        if (hit) {
            boolean before = WrapAsBookHandler.isActive();
            WrapAsBookHandler.toggle();
            boolean after = WrapAsBookHandler.isActive();
            ModLogger.info("RecipeScreenMixin: WB toggled {} -> {}", before, after);
            cir.setReturnValue(true);
        }
    }
}
