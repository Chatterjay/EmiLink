package org.chatterjay.emilink.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.client.handler.AENetworkCache;
import org.chatterjay.emilink.util.ModLogger;

@Mod.EventBusSubscriber(modid = Emilink.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModCommands {
    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("emilink")
                        .then(Commands.literal("debug")
                                .executes(ctx -> {
                                    boolean current = Config.DEBUG_MODE.get();
                                    Config.DEBUG_MODE.set(!current);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink debug mode: " + (!current ? "ON" : "OFF")),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("wb")
                                .executes(ctx -> {
                                    boolean current = Config.ENABLE_WRAP_BOOK.get();
                                    Config.ENABLE_WRAP_BOOK.set(!current);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink wrap book mode: " + (!current ? "ON" : "OFF")),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("clearcache")
                                .executes(ctx -> {
                                    AENetworkCache.clearAll();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink network cache cleared"),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("shiftclick")
                                .executes(ctx -> {
                                    String current = Config.EXTRACT_MODIFIER.get();
                                    String next;
                                    switch (current.toUpperCase(java.util.Locale.ROOT)) {
                                        case "SHIFT" -> next = "CONTROL";
                                        case "CONTROL" -> next = "ALT";
                                        case "ALT" -> next = "OFF";
                                        default -> next = "SHIFT";
                                    }
                                    Config.EXTRACT_MODIFIER.set(next);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("EmiLink extract modifier: " + next),
                                            false
                                    );
                                    ModLogger.info("Extract modifier set to {}", next);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("config")
                                .then(Commands.literal("list")
                                        .executes(ctx -> {
                                            ctx.getSource().sendSuccess(() -> Component.literal("§6=== EmiLink Config ==="), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§edebugMode: §f" + Config.DEBUG_MODE.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§ecacheTTLMs: §f" + Config.CACHE_TTL_MS.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§ebatchFlushMs: §f" + Config.BATCH_FLUSH_MS.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§eenableWrapBook: §f" + Config.ENABLE_WRAP_BOOK.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§ewbFillInputGrid: §f" + Config.WB_FILL_INPUT_GRID.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§eenableAeDeposit: §f" + Config.ENABLE_AE_DEPOSIT.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§eextractModifier: §f" + Config.EXTRACT_MODIFIER.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§edepositBatchModifier: §f" + Config.DEPOSIT_BATCH_MODIFIER.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§eenableBulkTransfer: §f" + Config.ENABLE_BULK_TRANSFER.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§eenableDragFill: §f" + Config.ENABLE_DRAG_FILL.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§eenableNetworkBadges: §f" + Config.ENABLE_NETWORK_BADGES.get()), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§7--- Config file: emilink-common.toml"), false);
                                            ctx.getSource().sendSuccess(() -> Component.literal("§7Use /emilink config set <key> <value> to change"), false);
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            String key = StringArgumentType.getString(ctx, "key");
                                                            String value = StringArgumentType.getString(ctx, "value");
                                                            return setConfigValue(ctx.getSource(), key, value);
                                                        })
                                                )
                                        )
                                )
                        )
        );
    }

    private static int setConfigValue(CommandSourceStack source, String key, String value) {
        try {
            switch (key) {
                case "debugMode" -> Config.DEBUG_MODE.set(Boolean.parseBoolean(value));
                case "cacheTTLMs" -> Config.CACHE_TTL_MS.set(Long.parseLong(value));
                case "batchFlushMs" -> Config.BATCH_FLUSH_MS.set(Long.parseLong(value));
                case "enableWrapBook" -> Config.ENABLE_WRAP_BOOK.set(Boolean.parseBoolean(value));
                case "wbFillInputGrid" -> Config.WB_FILL_INPUT_GRID.set(Boolean.parseBoolean(value));
                case "enableAeDeposit" -> Config.ENABLE_AE_DEPOSIT.set(Boolean.parseBoolean(value));
                case "extractModifier" -> Config.EXTRACT_MODIFIER.set(value.toUpperCase(java.util.Locale.ROOT));
                case "depositBatchModifier" -> Config.DEPOSIT_BATCH_MODIFIER.set(value.toUpperCase(java.util.Locale.ROOT));
                case "enableBulkTransfer" -> Config.ENABLE_BULK_TRANSFER.set(Boolean.parseBoolean(value));
                case "enableDragFill" -> Config.ENABLE_DRAG_FILL.set(Boolean.parseBoolean(value));
                case "enableNetworkBadges" -> Config.ENABLE_NETWORK_BADGES.set(Boolean.parseBoolean(value));
                default -> {
                    source.sendFailure(Component.literal("Unknown config key: " + key + ". Use /emilink config list"));
                    return 0;
                }
            }
            source.sendSuccess(() -> Component.literal("§aSet " + key + " = " + value), false);
            source.getServer().execute(() -> {
                try {
                    Config.SPEC.save();
                } catch (Exception e) {
                    ModLogger.warn("Failed to save config: {}", e.getMessage());
                }
            });
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError setting " + key + " = " + value + ": " + e.getMessage()));
            return 0;
        }
    }
}
