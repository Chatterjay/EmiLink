package org.chatterjay.emiextend.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class

ModLogger {
    private static final Logger LOG = LoggerFactory.getLogger("EmiLink");
    public static boolean DEBUG = true;

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
        if (DEBUG) {
            LOG.info("[DEBUG] " + msg, args);
        }
    }

    public static void debugChat(ServerPlayer player, String msg, Object... args) {
        if (DEBUG && player != null) {
            var text = String.format("[EmiLink] " + msg, args);
            player.sendSystemMessage(Component.literal(text), true);
        }
    }
}
