package org.chatterjay.emilink.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.chatterjay.emilink.Config;

public final class EmilinkConfigScreen {
    private EmilinkConfigScreen() {}

    public static Screen create(Screen parent) {
        try {
            var builderClass = Class.forName("me.shedaniel.clothconfig2.api.ConfigBuilder");
            var createMethod = builderClass.getMethod("create");
            Object builder = createMethod.invoke(null);

            builderClass.getMethod("setParentScreen", Screen.class).invoke(builder, parent);
            builderClass.getMethod("setTitle", Component.class).invoke(builder,
                    Component.literal("EmiLink Config"));
            builderClass.getMethod("setSavingRunnable", Runnable.class).invoke(builder,
                    (Runnable) () -> Config.SPEC.save());

            var getOrCreateCategory = builderClass.getMethod("getOrCreateCategory", Component.class);
            var entryBuilder = builderClass.getMethod("entryBuilder").invoke(builder);

            addBool(entryBuilder, getOrCreateCategory, builder, "Debug Mode",
                    Config.DEBUG_MODE, false);
            addLongField(entryBuilder, getOrCreateCategory, builder, "Cache TTL (ms)",
                    Config.CACHE_TTL_MS, 5000L);
            addLongField(entryBuilder, getOrCreateCategory, builder, "Batch Flush (ms)",
                    Config.BATCH_FLUSH_MS, 5000L);
            addBool(entryBuilder, getOrCreateCategory, builder, "Wrap Book",
                    Config.ENABLE_WRAP_BOOK, true);
            addBool(entryBuilder, getOrCreateCategory, builder, "WB Fill Input Grid",
                    Config.WB_FILL_INPUT_GRID, false);
            addBool(entryBuilder, getOrCreateCategory, builder, "AE Deposit",
                    Config.ENABLE_AE_DEPOSIT, true);
            addBool(entryBuilder, getOrCreateCategory, builder, "Network Badges",
                    Config.ENABLE_NETWORK_BADGES, false);
            addBool(entryBuilder, getOrCreateCategory, builder, "Bulk Transfer",
                    Config.ENABLE_BULK_TRANSFER, true);
            addBool(entryBuilder, getOrCreateCategory, builder, "Drag Fill",
                    Config.ENABLE_DRAG_FILL, true);
            addEnumSel(entryBuilder, getOrCreateCategory, builder, "Extract Modifier",
                    Config.ExtractTrigger.class, Config.getExtractModifier(),
                    v -> Config.EXTRACT_MODIFIER.set(((Enum<?>) v).name()));
            addEnumSel(entryBuilder, getOrCreateCategory, builder, "Deposit Batch Modifier",
                    Config.ExtractTrigger.class, Config.getDepositBatchModifier(),
                    v -> Config.DEPOSIT_BATCH_MODIFIER.set(((Enum<?>) v).name()));

            return (Screen) builderClass.getMethod("build").invoke(builder);
        } catch (Exception e) {
            org.chatterjay.emilink.util.ModLogger.warn("Failed to create config screen: {}", e.getMessage());
            return null;
        }
    }

    private static void addBool(Object eb, java.lang.reflect.Method getOrCreateCategory,
                                 Object builder, String name,
                                 net.minecraftforge.common.ForgeConfigSpec.BooleanValue cfg,
                                 boolean defaultVal) throws Exception {
        var category = getOrCreateCategory.invoke(builder, Component.literal("General"));
        var toggle = eb.getClass().getMethod("startBooleanToggle", Component.class, boolean.class)
                .invoke(eb, Component.literal(name), cfg.get());
        toggle.getClass().getMethod("setDefaultValue", boolean.class).invoke(toggle, defaultVal);
        toggle.getClass().getMethod("setSaveConsumer", java.util.function.Consumer.class)
                .invoke(toggle, (java.util.function.Consumer<Boolean>) v -> cfg.set(v));
        var entry = toggle.getClass().getMethod("build").invoke(toggle);
        category.getClass().getMethod("addEntry", Class.forName("me.shedaniel.clothconfig2.api.AbstractConfigListEntry"))
                .invoke(category, entry);
    }

    private static void addLongField(Object eb, java.lang.reflect.Method getOrCreateCategory,
                                      Object builder, String name,
                                      net.minecraftforge.common.ForgeConfigSpec.LongValue cfg,
                                      long defaultVal) throws Exception {
        var category = getOrCreateCategory.invoke(builder, Component.literal("General"));
        var field = eb.getClass().getMethod("startLongField", Component.class, long.class)
                .invoke(eb, Component.literal(name), cfg.get());
        field.getClass().getMethod("setDefaultValue", long.class).invoke(field, defaultVal);
        field.getClass().getMethod("setSaveConsumer", java.util.function.Consumer.class)
                .invoke(field, (java.util.function.Consumer<Long>) v -> cfg.set(v));
        var entry = field.getClass().getMethod("build").invoke(field);
        category.getClass().getMethod("addEntry", Class.forName("me.shedaniel.clothconfig2.api.AbstractConfigListEntry"))
                .invoke(category, entry);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addEnumSel(Object eb, java.lang.reflect.Method getOrCreateCategory,
                                    Object builder, String name,
                                    Class<? extends Enum> enumClass, Enum<?> current,
                                    java.util.function.Consumer<?> setter) throws Exception {
        var category = getOrCreateCategory.invoke(builder, Component.literal("General"));
        var selector = eb.getClass().getMethod("startEnumSelector", Component.class, Class.class, Enum.class)
                .invoke(eb, Component.literal(name), enumClass, current);
        selector.getClass().getMethod("setSaveConsumer", java.util.function.Consumer.class)
                .invoke(selector, setter);
        var entry = selector.getClass().getMethod("build").invoke(selector);
        category.getClass().getMethod("addEntry", Class.forName("me.shedaniel.clothconfig2.api.AbstractConfigListEntry"))
                .invoke(category, entry);
    }
}
