package org.chatterjay.emiextend.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
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
        if (EmiLinkConfig.DEBUG_MODE.get()) {
            LOG.info("[DEBUG] " + msg, args);
        }
    }

    public static void debugChat(ServerPlayer player, String msg, Object... args) {
        if (EmiLinkConfig.DEBUG_MODE.get() && player != null) {
            var text = String.format("[EmiLink] " + msg, args);
            player.sendSystemMessage(Component.literal(text), true);
        }
    }
}
