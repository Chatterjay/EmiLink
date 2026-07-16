package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.chatterjay.emiextend.client.bookmark.BookmarkPageHelper;
import org.chatterjay.emiextend.client.InputEvents;
import org.chatterjay.emiextend.client.handler.EmiInteractionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = EmiScreenManager.class, remap = false)
public class EmiScreenManagerMixin {
    @Shadow
    private static int lastMouseX;
    @Shadow
    private static int lastMouseY;
    @Shadow
    public static dev.emi.emi.api.stack.EmiIngredient pressedStack;
    @Shadow
    public static dev.emi.emi.api.stack.EmiIngredient draggedStack;
    @Shadow
    public static dev.emi.emi.screen.widget.EmiSearchWidget search;

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, require = 0)
    private static void emilink$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (EmiInteractionHandler.onKeyPressed(keyCode, scanCode, modifiers, lastMouseX, lastMouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$onBomKeyPressedHead(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (InputEvents.tryHandleQuickFillSlotKeyFromEmi(keyCode, scanCode)) {
            cir.setReturnValue(true);
            return;
        }
        if (InputEvents.tryHandleBomKeyFromEmi(keyCode, scanCode)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (org.chatterjay.emiextend.util.ModLogger.isDebugEnabled()) {
            org.chatterjay.emiextend.client.InputEvents.logAeCtrlLeftClick(
                    "emi-mouse-released-head", Minecraft.getInstance().screen, mouseX, mouseY, button);
        }

        if (emilink$dropFavoriteOnPagedBookmark(mouseX, mouseY)) {
            pressedStack = EmiStack.EMPTY;
            draggedStack = EmiStack.EMPTY;
            cir.setReturnValue(true);
            return;
        }

        // Drag-fill: when dragging an EMI item, fill text fields with item name
        if (draggedStack != null && !draggedStack.isEmpty()
                && org.chatterjay.emiextend.config.EmiLinkConfig.ENABLE_DRAG_FILL.get()) {
            var mc = Minecraft.getInstance();
            if (mc.screen != null) {
                var text = getDragFillText(draggedStack);
                if (text != null) {
                    // Check EMI's own search widget first (not in screen.children())
                    if (search.isMouseOver(mouseX, mouseY)) {
                        search.setValue(text);
                        search.setCursorPosition(text.length());
                        pressedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                        draggedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                        cir.setReturnValue(true);
                        return;
                    }
                    // Check other EditBox children on the screen
                    for (var child : mc.screen.children()) {
                        if (child instanceof EditBox eb && eb.isMouseOver((int) mouseX, (int) mouseY)) {
                            eb.setValue(text);
                            eb.setCursorPosition(text.length());
                            pressedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                            draggedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                }
            }
        }

        if (EmiInteractionHandler.onMouseReleased(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(method = "renderCurrentTooltip",
              at = @At(value = "INVOKE", target = "Ldev/emi/emi/api/stack/EmiIngredient;getTooltip()Ljava/util/List;"),
              require = 0)
    private static List<ClientTooltipComponent> emilink$addAeTooltipInfo(EmiIngredient hov) {
        return EmiInteractionHandler.addAeTooltipInfo(hov, lastMouseX, lastMouseY, hov.getTooltip());
    }

    @Redirect(method = "renderDraggedStack",
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"),
            require = 0)
    private static int emilink$allowFavoriteDropPreviewOnEmptyPages(List<?> stacks) {
        return Integer.MAX_VALUE;
    }

    @Redirect(method = "renderDraggedStack",
            at = @At(value = "INVOKE", target = "Ldev/emi/emi/screen/EmiScreenManager$ScreenSpace;getClosestEdge(II)I"),
            require = 0)
    private static int emilink$compactFavoriteDropPreview(ScreenSpace space, int x, int y) {
        int localIndex = space.getClosestEdge(x, y);
        var panel = EmiScreenManager.getHoveredPanel(x, y);
        if (panel != null && panel.getType() == SidebarType.FAVORITES
                && space.getType() == SidebarType.FAVORITES && space.pageSize > 0) {
            BookmarkPageHelper.compactAllPages(space.pageSize);
            return BookmarkPageHelper.compactLocalInsertIndex(panel.page, space.pageSize, localIndex);
        }
        return localIndex;
    }

    /** Get fill text from EMI dragged stack */
    private static String getDragFillText(EmiIngredient stack) {
        if (stack == null || stack.isEmpty()) return null;
        var first = stack.getEmiStacks().stream().findFirst().orElse(null);
        if (first == null) return null;
        if (net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            var id = first.getId();
            return id != null ? "@" + id.getNamespace() : null;
        }
        return first.getName().getString();
    }

    private static boolean emilink$dropFavoriteOnPagedBookmark(double mouseX, double mouseY) {
        if (draggedStack == null || draggedStack.isEmpty()) return false;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        var panel = EmiScreenManager.getHoveredPanel(mx, my);
        if (panel == null || panel.getType() != SidebarType.FAVORITES) return false;
        var space = panel.getHoveredSpace(mx, my);
        if (space == null || space.getType() != SidebarType.FAVORITES || space.pageSize <= 0) return false;

        BookmarkPageHelper.compactAllPages(space.pageSize);

        int pageStart = panel.page * space.pageSize;
        BookmarkPageHelper.ensureSize(pageStart);
        BookmarkPageHelper.addOrMoveFavoriteToPage(draggedStack, null, panel.page, space.pageSize);
        space.batcher.repopulate();
        return true;
    }
}
