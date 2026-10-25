package org.chatterjay.emiextend.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModLogger {
    private static final Logger LOG = LoggerFactory.getLogger("EmiLink");

    private ModLogger() {}

    public static void info(String msg, Object... args) {
        LOG.info(msg, args);
    }

    public static void warn(String msg, Object... args) {
        LOG.warn(msg, args);
    }

    public static void error(String msg, Object... args) {
        LOG.error(msg, args);
    }

    public static void debug(String msg, Object... args) {
        if (debugEnabled()) {
            LOG.info("[DEBUG] " + msg, args);
        }
    }

    public static boolean isDebugEnabled() {
        return debugEnabled();
    }

    public static void debugChat(ServerPlayer player, String msg, Object... args) {
        if (debugEnabled() && player != null) {
            var text = String.format("[EmiLink] " + msg, args);
            player.sendSystemMessage(Component.literal(text), true);
        }
    }

    /**
     * Debug logging is configured per side: the client option controls the client
     * log, the server option the dedicated server log. Only one of the two config
     * files is loaded on a given side, so the loaded one wins.
     */
    private static boolean debugEnabled() {
        Boolean client = readDebugMode(EmiLinkConfig.DEBUG_MODE);
        if (client != null) return client;
        Boolean server = readDebugMode(EmiLinkConfig.SERVER_DEBUG_MODE);
        return server != null && server;
    }

    private static Boolean readDebugMode(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (Throwable ignored) {
            // This side's config file is not loaded (or not loaded yet).
            return null;
        }
    }
}
