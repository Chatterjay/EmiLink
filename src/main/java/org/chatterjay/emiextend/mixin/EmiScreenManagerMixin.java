package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.network.CreateItemC2SPacket;
import dev.emi.emi.network.EmiNetwork;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.client.bookmark.BookmarkPageHelper;
import org.chatterjay.emiextend.client.InputEvents;
import org.chatterjay.emiextend.client.handler.EmiInteractionHandler;
import org.chatterjay.emiextend.client.search.SearchHistoryOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.ArrayList;

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
    @Shadow
    public static boolean isDisabled() {
        throw new AssertionError();
    }
    @Shadow
    private static boolean hasFocusedTextField(ContainerEventHandler parent, int depthBail) {
        throw new AssertionError();
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private static void emilink$renderSearchHistory(EmiDrawContext context, int mouseX, int mouseY, float delta,
                                                    CallbackInfo ci) {
        SearchHistoryOverlay.render(context.raw(), mouseX, mouseY);
    }

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
            return;
        }
        if (EmiApi.isCheatMode() && EmiConfig.deleteCursorStack.matchesKey(keyCode, scanCode)
                && !emilink$shouldSkipFavoriteDeleteKey()
                && emilink$deleteCursorOnFavoriteSidebar(lastMouseX, lastMouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getSearchSource", at = @At("RETURN"), cancellable = true, require = 0)
    private static void emilink$filterNullSearchSource(CallbackInfoReturnable<List<? extends EmiIngredient>> cir) {
        var source = cir.getReturnValue();
        if (source == null || source.isEmpty()) return;
        List<EmiIngredient> filtered = null;
        for (int i = 0; i < source.size(); i++) {
            EmiIngredient ingredient = source.get(i);
            if (ingredient == null) {
                if (filtered == null) {
                    filtered = new ArrayList<>(source.size());
                    for (int j = 0; j < i; j++) {
                        filtered.add(source.get(j));
                    }
                }
            } else if (filtered != null) {
                filtered.add(ingredient);
            }
        }
        if (filtered != null) {
            cir.setReturnValue(List.copyOf(filtered));
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (org.chatterjay.emiextend.util.ModLogger.isDebugEnabled()) {
            org.chatterjay.emiextend.client.InputEvents.logAeCtrlLeftClick(
                    "emi-mouse-released-head", Minecraft.getInstance().screen, mouseX, mouseY, button);
        }

        if (EmiApi.isCheatMode() && EmiConfig.deleteCursorStack.matchesMouse(button)
                && emilink$deleteCursorOnFavoriteSidebar((int) mouseX, (int) mouseY)) {
            cir.setReturnValue(true);
            return;
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
                    ItemStack icon = emilink$getDragFillIcon(draggedStack);
                    // Check EMI's own search widget first (not in screen.children())
                    if (search.isMouseOver(mouseX, mouseY)) {
                        search.setValue(text);
                        search.setCursorPosition(text.length());
                        SearchHistoryOverlay.remember(text, icon);
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
                            SearchHistoryOverlay.remember(text, icon);
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

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$clickSearchHistory(double mouseX, double mouseY, int button,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (SearchHistoryOverlay.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$scrollSearchHistory(double mouseX, double mouseY, double amount,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (SearchHistoryOverlay.mouseScrolled(mouseX, mouseY, amount)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$favoritesBeforeUnderlyingGui(int mouseX, int mouseY, boolean notClick,
                                                            boolean ignoreLastHoveredCraftable,
                                                            CallbackInfoReturnable<EmiStackInteraction> cir) {
        var hovered = emilink$getFavoriteSidebarStack(mouseX, mouseY);
        if (hovered != null) {
            cir.setReturnValue(hovered);
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

    private static ItemStack emilink$getDragFillIcon(EmiIngredient stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        return stack.getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(itemStack -> !itemStack.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    private static boolean emilink$shouldSkipFavoriteDeleteKey() {
        var mc = Minecraft.getInstance();
        if (mc.screen == null || isDisabled()) return true;
        if (search != null && search.canConsumeInput()) return true;
        return hasFocusedTextField(mc.screen, 10);
    }

    private static boolean emilink$deleteCursorOnFavoriteSidebar(int mouseX, int mouseY) {
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> handled)) return false;
        ItemStack cursor = handled.getMenu().getCarried();
        if (cursor.isEmpty()) return false;

        var space = EmiScreenManager.getHoveredSpace(mouseX, mouseY);
        if (space == null || space.pageSize <= 0 || space.getType() != SidebarType.FAVORITES) return false;
        var hovered = emilink$getFavoriteSidebarStack(mouseX, mouseY);
        if (hovered == null || hovered.isEmpty()) return false;

        handled.getMenu().setCarried(ItemStack.EMPTY);
        EmiNetwork.sendToServer(new CreateItemC2SPacket(1, ItemStack.EMPTY));
        return true;
    }

    private static EmiStackInteraction emilink$getFavoriteSidebarStack(int mouseX, int mouseY) {
        var panel = EmiScreenManager.getHoveredPanel(mouseX, mouseY);
        if (panel == null || panel.getType() != SidebarType.FAVORITES || !panel.isVisible()) return null;
        var space = panel.getHoveredSpace(mouseX, mouseY);
        if (space == null || space.getType() != SidebarType.FAVORITES || space.pageSize <= 0) return null;
        if (!space.contains(mouseX, mouseY) || mouseX < space.tx || mouseY < space.ty) return null;

        int local = space.getRawOffset((mouseX - space.tx) / 18, (mouseY - space.ty) / 18);
        if (local < 0) return null;
        int index = local;
        if (space == panel.space) {
            index += space.pageSize * panel.page;
        }
        var stacks = space.getStacks();
        if (index < 0 || index >= stacks.size()) {
            return EmiStackInteraction.EMPTY;
        }
        EmiIngredient hovered = stacks.get(index);
        if (hovered == null || hovered.isEmpty()) {
            return EmiStackInteraction.EMPTY;
        }
        if (hovered instanceof EmiFavorite favorite) {
            return new EmiScreenManager.SidebarEmiStackInteraction(hovered, space, favorite.getRecipe(), true);
        }
        return new EmiScreenManager.SidebarEmiStackInteraction(hovered, space);
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
        int localIndex = BookmarkPageHelper.compactLocalInsertIndex(panel.page, space.pageSize,
                space.getClosestEdge(mx, my));
        BookmarkPageHelper.addOrMoveFavoriteToPage(draggedStack, null, panel.page, space.pageSize, localIndex);
        space.batcher.repopulate();
        return true;
    }
}
