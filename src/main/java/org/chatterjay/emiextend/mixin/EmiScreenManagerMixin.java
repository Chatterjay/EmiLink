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
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.client.bookmark.BookmarkPageHelper;
import org.chatterjay.emiextend.client.bookmark.BomFavoriteQuickCraftButton;
import org.chatterjay.emiextend.client.DragFillTarget;
import org.chatterjay.emiextend.client.InputEvents;
import org.chatterjay.emiextend.client.handler.FavoriteDragSelectHandler;
import org.chatterjay.emiextend.client.handler.FavoriteHighlightRenderer;
import org.chatterjay.emiextend.client.handler.EmiInteractionHandler;
import org.chatterjay.emiextend.client.search.SearchHistoryOverlay;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private static void emilink$clearFavoriteBomButtons(EmiDrawContext context, int mouseX, int mouseY, float delta,
                                                        CallbackInfo ci) {
        BomFavoriteQuickCraftButton.clearFrame();
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private static void emilink$renderSearchHistory(EmiDrawContext context, int mouseX, int mouseY, float delta,
                                                    CallbackInfo ci) {
        SearchHistoryOverlay.render(context.raw(), mouseX, mouseY);
        FavoriteHighlightRenderer.renderOverlays(context.raw());
        emilink$renderDragFillTargets(context.raw(), mouseX, mouseY);
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
        if (FavoriteDragSelectHandler.handleProtectedFavoriteDelete(keyCode, lastMouseX, lastMouseY)) {
            cir.setReturnValue(true);
            return;
        }
        if (emilink$copyHoveredStackId(keyCode, scanCode)) {
            cir.setReturnValue(true);
            return;
        }
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
        if (FavoriteDragSelectHandler.isActive()
                && FavoriteDragSelectHandler.onMouseReleased(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (org.chatterjay.emiextend.util.ModLogger.isDebugEnabled()) {
            org.chatterjay.emiextend.client.InputEvents.logAeCtrlLeftClick(
                    "emi-mouse-released-head", Minecraft.getInstance().screen, mouseX, mouseY, button);
        }

        if (BomFavoriteQuickCraftButton.isMouseOver(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }

        if (SearchHistoryOverlay.isMouseOver(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
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
                    if (search != null && search.isMouseOver(mouseX, mouseY)) {
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

                    // Include non-EditBox text widgets exposed as children.
                    for (var child : mc.screen.children()) {
                        if (emilink$isDragFillTargetAt(child, mouseX, mouseY)
                                && emilink$setDragFillValue(child, text)) {
                            SearchHistoryOverlay.remember(text, icon);
                            pressedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                            draggedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                            cir.setReturnValue(true);
                            return;
                        }
                    }

                    // Some screens keep their search widget out of children().
                    for (String fieldName : new String[]{"searchBox", "searchField", "search"}) {
                        Object widget = emilink$findScreenField(mc.screen, fieldName);
                        if (emilink$isDragFillTargetAt(widget, mouseX, mouseY)
                                && emilink$setDragFillValue(widget, text)) {
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
        if (BomFavoriteQuickCraftButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if (SearchHistoryOverlay.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if (FavoriteDragSelectHandler.isActive()) {
            FavoriteDragSelectHandler.clearEmiDragState();
            cir.setReturnValue(true);
            return;
        }
        if (FavoriteDragSelectHandler.onMousePressed(
                Minecraft.getInstance().screen, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$consumeFavoriteDrag(double mouseX, double mouseY, int button,
                                                     double dragX, double dragY,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (FavoriteDragSelectHandler.isActive()) {
            FavoriteDragSelectHandler.onMouseDragged(mouseX, mouseY);
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
        if (BomFavoriteQuickCraftButton.isMouseOver(mouseX, mouseY)) {
            cir.setReturnValue(EmiStackInteraction.EMPTY);
            return;
        }
        if (SearchHistoryOverlay.isMouseOver(mouseX, mouseY)) {
            cir.setReturnValue(EmiStackInteraction.EMPTY);
            return;
        }
        var hovered = emilink$getFavoriteSidebarStack(mouseX, mouseY);
        if (hovered != null) {
            cir.setReturnValue(hovered);
        }
    }

    @Inject(method = "renderCurrentTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$hideTooltipBehindSearchHistory(EmiDrawContext context, int mouseX, int mouseY,
                                                               float delta, EmiScreenBase base, CallbackInfo ci) {
        if (SearchHistoryOverlay.isMouseOver(mouseX, mouseY)) {
            ci.cancel();
        }
    }

    @Redirect(method = "renderCurrentTooltip",
              at = @At(value = "INVOKE", target = "Ldev/emi/emi/api/stack/EmiIngredient;getTooltip()Ljava/util/List;"),
              require = 0)
    private static List<ClientTooltipComponent> emilink$addAeTooltipInfo(EmiIngredient hov) {
        List<ClientTooltipComponent> tooltip = EmiInteractionHandler.addAeTooltipInfo(
                hov, lastMouseX, lastMouseY, hov.getTooltip());
        return FavoriteHighlightRenderer.appendProtectedTooltip(hov, tooltip);
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

    /** Draw compatible input fields as opaque drop targets during EMI dragging. */
    private static void emilink$renderDragFillTargets(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!EmiLinkConfig.ENABLE_DRAG_FILL.get() || draggedStack == null || draggedStack.isEmpty()) {
            return;
        }

        var screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return;
        }

        List<DragFillTarget> targets = new ArrayList<>();
        for (var child : screen.children()) {
            emilink$addDragFillTarget(targets, child);
        }

        // Some mod screens keep their search widget out of children(). Keep
        // this list in sync with the reflective drag-fill fallback.
        for (String fieldName : new String[]{"searchBox", "searchField", "search"}) {
            Object widget = emilink$findScreenField(screen, fieldName);
            emilink$addDragFillTarget(targets, widget);
        }

        for (DragFillTarget target : targets) {
            boolean hovered = mouseX >= target.x() && mouseX < target.x() + target.width()
                    && mouseY >= target.y() && mouseY < target.y() + target.height();
            // Keep the target visible without obscuring the widget text.
            int fill = hovered ? 0x663c9cff : 0x302b78d0;
            int x = target.x();
            int y = target.y();
            int right = x + target.width();
            int bottom = y + target.height();
            graphics.fill(x, y, right, bottom, fill);
        }
    }

    private static void emilink$addDragFillTarget(List<DragFillTarget> targets, Object widget) {
        // EMI's own search widget is handled by EMI and should remain visually
        // unchanged while other mod input fields are highlighted.
        if (widget == null || widget == search) {
            return;
        }

        int x;
        int y;
        int width;
        int height;
        if (widget instanceof EditBox editBox) {
            x = editBox.getX();
            y = editBox.getY();
            width = editBox.getWidth();
            height = editBox.getHeight();
        } else {
            if (!emilink$hasStringSetter(widget)) {
                return;
            }
            x = emilink$readInt(widget, "getX");
            y = emilink$readInt(widget, "getY");
            width = emilink$readInt(widget, "getWidth");
            height = emilink$readInt(widget, "getHeight");
        }

        if (width <= 0 || height <= 0) {
            return;
        }
        for (DragFillTarget target : targets) {
            if (target.x() == x && target.y() == y
                    && target.width() == width && target.height() == height) {
                return;
            }
        }
        targets.add(new DragFillTarget(x, y, width, height));
    }

    private static boolean emilink$hasStringSetter(Object widget) {
        try {
            widget.getClass().getMethod("setValue", String.class);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static int emilink$readInt(Object widget, String methodName) {
        try {
            Method method = widget.getClass().getMethod(methodName);
            Object value = method.invoke(widget);
            return value instanceof Number number ? number.intValue() : -1;
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private static boolean emilink$isDragFillTargetAt(Object widget, double mouseX, double mouseY) {
        if (widget == null || !emilink$hasStringSetter(widget)) {
            return false;
        }
        if (widget instanceof EditBox editBox) {
            return editBox.isMouseOver((int) mouseX, (int) mouseY);
        }
        int x = emilink$readInt(widget, "getX");
        int y = emilink$readInt(widget, "getY");
        int width = emilink$readInt(widget, "getWidth");
        int height = emilink$readInt(widget, "getHeight");
        return width > 0 && height > 0
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }

    private static boolean emilink$setDragFillValue(Object widget, String text) {
        if (widget == null || text == null) {
            return false;
        }
        try {
            Method setValue = widget.getClass().getMethod("setValue", String.class);
            setValue.invoke(widget, text);
            try {
                Method setCursor = widget.getClass().getMethod("setCursorPosition", int.class);
                setCursor.invoke(widget, text.length());
            } catch (ReflectiveOperationException ignored) {
                // A custom text widget may not expose cursor positioning.
            }
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object emilink$findScreenField(Screen screen, String fieldName) {
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(screen);
            } catch (ReflectiveOperationException ignored) {
                // Search fields are frequently declared on a superclass.
            }
        }
        return null;
    }

    private static boolean emilink$copyHoveredStackId(int keyCode, int scanCode) {
        if (!EmiLinkConfig.ENABLE_COPY_HOVERED_STACK_ID.get()) {
            return false;
        }

        if (keyCode != GLFW.GLFW_KEY_C || !Screen.hasControlDown() || Screen.hasShiftDown() || Screen.hasAltDown()) {
            return false;
        }

        var mc = Minecraft.getInstance();
        if (mc.screen == null || isDisabled()) return false;
        if (search != null && search.canConsumeInput()) return false;
        if (hasFocusedTextField(mc.screen, 10)) return false;

        var hovered = EmiScreenManager.getHoveredStack(lastMouseX, lastMouseY, false);
        if (hovered == null || hovered.isEmpty()) return false;

        var id = hovered.getStack().getEmiStacks().stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(EmiStack::getId)
                .filter(stackId -> stackId != null)
                .findFirst()
                .orElse(null);
        if (id == null) return false;

        GLFW.glfwSetClipboardString(mc.getWindow().getWindow(), id.toString());
        return true;
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
