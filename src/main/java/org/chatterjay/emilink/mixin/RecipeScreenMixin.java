package org.chatterjay.emilink.mixin;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    private static final int BTN_SIZE = 14;

    @Inject(method = "render", at = @At("RETURN"))
    private void emilink$onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ModLogger.info("RecipeScreenMixin: render called, old={}, isEncodingTerm={}",
                old == null ? "null" : old.getClass().getName(),
                old instanceof PatternEncodingTermScreen);

        if (!(old instanceof PatternEncodingTermScreen)) return;

        // Provider search key
        try {
            EmiRecipeCategory category = getFocusedCategory();
            if (category != null && category.getId() != null) {
                String key = category.getId().getPath();
                if (!key.equals(emilink$lastProviderSearchKey)) {
                    emilink$lastProviderSearchKey = key;
                    ProviderSearchHelper.setLastProcessingName(key);
                    ModLogger.info("RecipeScreenMixin: set provider search key to '{}'", key);
                }
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

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void emilink$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!(old instanceof PatternEncodingTermScreen)) {
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
