package org.chatterjay.emiextend.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.Range;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;
import org.chatterjay.emiextend.config.EmiLinkConfig;

/** Client-only NeoForge configuration UI with live favorite protection previews. */
public final class EmiLinkConfigScreen {
    private static final int WIDGET_WIDTH = Button.DEFAULT_WIDTH;
    private static final int WIDGET_HEIGHT = Button.DEFAULT_HEIGHT;
    private static final int PREVIEW_SIZE = 18;
    private static final int PREVIEW_GAP = 4;

    private EmiLinkConfigScreen() {
    }

    /** Called reflectively from the common mod entry point. */
    public static Screen create(ModContainer container, Screen parent) {
        return new ConfigurationScreen(container, parent,
                (_screen, type, config, title) -> new PreviewSectionScreen(_screen, type, config, title));
    }

    private static final class PreviewSectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {
        private PreviewSectionScreen(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
            super(parent, type, config, title);
        }

        private PreviewSectionScreen(Context parentContext, Screen parent, Map<String, Object> valueSpecs,
                String key, Set<? extends UnmodifiableConfig.Entry> entries, Component title) {
            super(parentContext, parent, valueSpecs, key, entries, title);
        }

        @Override
        protected Element createSection(String key, UnmodifiableConfig subconfig, UnmodifiableConfig subsection) {
            if (subconfig.isEmpty()) {
                return null;
            }
            return new Element(
                    Component.translatable("neoforge.configuration.uitext.section", getTranslationComponent(key)),
                    getTooltipComponent(key, null),
                    Button.builder(
                                    Component.translatable("neoforge.configuration.uitext.section",
                                            Component.translatable("neoforge.configuration.uitext.sectiontext")),
                                    button -> minecraft.setScreen(sectionCache.computeIfAbsent(key,
                                            k -> new PreviewSectionScreen(context, this, subconfig.valueMap(), key,
                                                    subsection.entrySet(),
                                                    Component.translatable(getTranslationKey(key))).rebuild())))
                            .tooltip(Tooltip.create(getTooltipComponent(key, null)))
                            .width(Button.DEFAULT_WIDTH)
                            .build(),
                    false);
        }

        @Override
        protected Element createStringValue(String key, Predicate<String> tester, Supplier<String> source,
                Consumer<String> target) {
            if (!isPreviewColorKey(key)) {
                return super.createStringValue(key, tester, source, target);
            }

            PreviewEditBoxWidget widget = new PreviewEditBoxWidget(
                    getTranslationComponent(key), previewSupplier(key));
            EditBox box = widget.editBox();
            box.setEditable(true);
            box.setTooltip(Tooltip.create(getTooltipComponent(key, null)));
            box.setMaxLength(Mth.clamp(source.get().length() + 5, 128, 192));
            box.setValue(source.get());
            box.setResponder(newValue -> {
                if (newValue != null && tester.test(newValue)) {
                    if (!newValue.equals(source.get())) {
                        undoManager.add(v -> {
                            target.accept(v);
                            onChanged(key);
                        }, newValue, v -> {
                            target.accept(v);
                            onChanged(key);
                        }, source.get());
                    }
                    box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                    return;
                }
                box.setTextColor(0xFFFF0000);
            });
            widget.setTooltip(Tooltip.create(getTooltipComponent(key, null)));
            return new Element(getTranslationComponent(key), getTooltipComponent(key, null), widget);
        }

        @Override
        protected Element createIntegerValue(String key, ValueSpec spec, Supplier<Integer> source,
                Consumer<Integer> target) {
            if (!isPreviewOpacityKey(key)) {
                return super.createIntegerValue(key, spec, source, target);
            }

            Range<Integer> range = spec.getRange();
            int min = range != null ? range.getMin() : 0;
            int max = range != null ? range.getMax() : 255;
            PreviewSliderWidget widget = new PreviewSliderWidget(
                    getTranslationComponent(key), source.get(), min, max, previewSupplier(key),
                    newValue -> {
                        if (!newValue.equals(source.get())) {
                            undoManager.add(v -> {
                                target.accept(v);
                                onChanged(key);
                            }, newValue, v -> {
                                target.accept(v);
                                onChanged(key);
                            }, source.get());
                        }
                    });
            widget.setTooltip(Tooltip.create(getTooltipComponent(key, range)));
            return new Element(getTranslationComponent(key), getTooltipComponent(key, range), widget);
        }
    }

    private static boolean isPreviewColorKey(String key) {
        return "favoriteProtectionBorderColor".equals(key)
                || "favoriteProtectionBackgroundColor".equals(key);
    }

    private static boolean isPreviewOpacityKey(String key) {
        return "favoriteProtectionBorderOpacity".equals(key)
                || "favoriteProtectionBackgroundOpacity".equals(key);
    }

    private static IntSupplier previewSupplier(String key) {
        if ("favoriteProtectionBackgroundColor".equals(key)
                || "favoriteProtectionBackgroundOpacity".equals(key)) {
            return EmiLinkConfig::getFavoriteProtectionBackgroundArgb;
        }
        return EmiLinkConfig::getFavoriteProtectionBorderArgb;
    }

    private abstract static class PreviewContainerWidget extends AbstractContainerWidget {
        private final IntSupplier previewColor;

        private PreviewContainerWidget(Component message, IntSupplier previewColor) {
            super(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, message);
            this.previewColor = previewColor;
        }

        protected int contentWidth() {
            return Math.max(20, getWidth() - PREVIEW_SIZE - PREVIEW_GAP);
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            updateChildLayout();
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            updateChildLayout();
        }

        @Override
        public void setWidth(int width) {
            super.setWidth(width);
            updateChildLayout();
        }

        @Override
        public void setHeight(int height) {
            super.setHeight(height);
            updateChildLayout();
        }

        @Override
        public void setSize(int width, int height) {
            super.setSize(width, height);
            updateChildLayout();
        }

        protected abstract void updateChildLayout();

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderChild(graphics, mouseX, mouseY, partialTick);
            renderPreview(graphics);
        }

        protected abstract void renderChild(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

        private void renderPreview(GuiGraphics graphics) {
            int x = getX() + getWidth() - PREVIEW_SIZE;
            int y = getY() + Math.max(0, (getHeight() - PREVIEW_SIZE) / 2);
            graphics.fill(x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE, 0xff101010);
            int innerX = x + 1;
            int innerY = y + 1;
            int half = (PREVIEW_SIZE - 2) / 2;
            graphics.fill(innerX, innerY, innerX + half, innerY + half, 0xffeeeeee);
            graphics.fill(innerX + half, innerY, x + PREVIEW_SIZE - 1, innerY + half, 0xffaaaaaa);
            graphics.fill(innerX, innerY + half, innerX + half, y + PREVIEW_SIZE - 1, 0xffaaaaaa);
            graphics.fill(innerX + half, innerY + half, x + PREVIEW_SIZE - 1,
                    y + PREVIEW_SIZE - 1, 0xffeeeeee);
            graphics.fill(innerX, innerY, x + PREVIEW_SIZE - 1, y + PREVIEW_SIZE - 1, previewColor.getAsInt());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }

    private static final class PreviewEditBoxWidget extends PreviewContainerWidget {
        private final EditBox editBox;

        private PreviewEditBoxWidget(Component message, IntSupplier previewColor) {
            super(message, previewColor);
            this.editBox = new EditBox(Minecraft.getInstance().font, 0, 0, contentWidth(), WIDGET_HEIGHT, message);
            updateChildLayout();
        }

        private EditBox editBox() {
            return editBox;
        }

        @Override
        protected void updateChildLayout() {
            if (editBox != null) {
                editBox.setX(getX());
                editBox.setY(getY());
                editBox.setWidth(contentWidth());
                editBox.setHeight(getHeight());
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(editBox);
        }

        @Override
        protected void renderChild(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            editBox.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class PreviewSliderWidget extends PreviewContainerWidget {
        private final PreviewSlider slider;

        private PreviewSliderWidget(Component message, int value, int min, int max, IntSupplier previewColor,
                Consumer<Integer> valueChanged) {
            super(message, previewColor);
            this.slider = new PreviewSlider(0, 0, contentWidth(), WIDGET_HEIGHT, message, value, min, max, valueChanged);
            updateChildLayout();
        }

        @Override
        protected void updateChildLayout() {
            if (slider != null) {
                slider.setX(getX());
                slider.setY(getY());
                slider.setWidth(contentWidth());
                slider.setHeight(getHeight());
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(slider);
        }

        @Override
        protected void renderChild(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            slider.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static final class PreviewSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final Consumer<Integer> valueChanged;
        private boolean canChangeValue;

        private PreviewSlider(int x, int y, int width, int height, Component message, int value, int min, int max,
                Consumer<Integer> valueChanged) {
            super(x, y, width, height, message, normalize(value, min, max));
            this.min = min;
            this.max = max;
            this.valueChanged = valueChanged;
            updateMessage();
        }

        private static double normalize(int value, int min, int max) {
            if (max <= min) {
                return 0.0;
            }
            return Mth.clamp((value - min) / (double) (max - min), 0.0, 1.0);
        }

        private int currentValue() {
            return Mth.clamp((int) Math.round(min + value * (max - min)), min, max);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(Integer.toString(currentValue())));
        }

        @Override
        protected void applyValue() {
            valueChanged.accept(currentValue());
        }

        private void setValue(double newValue) {
            double oldValue = value;
            value = Mth.clamp(newValue, 0.0, 1.0);
            if (oldValue != value) {
                applyValue();
            }
            updateMessage();
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setValue((mouseX - (getX() + 4.0)) / (getWidth() - 8.0));
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            setValue((mouseX - (getX() + 4.0)) / (getWidth() - 8.0));
            super.onDrag(mouseX, mouseY, dragX, dragY);
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) {
                canChangeValue = false;
            } else {
                InputType inputType = Minecraft.getInstance().getLastInputType();
                if (inputType == InputType.MOUSE || inputType == InputType.KEYBOARD_TAB) {
                    canChangeValue = true;
                }
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (net.minecraft.client.gui.navigation.CommonInputs.selected(keyCode)) {
                canChangeValue = !canChangeValue;
                return true;
            }
            if (canChangeValue && (keyCode == 263 || keyCode == 262)) {
                setValue(value + (keyCode == 263 ? -1.0 : 1.0) / (getWidth() - 8.0));
                return true;
            }
            return false;
        }
    }
}
