package org.chatterjay.emiextend.util;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AeAutocraftAmountOverride {
    private static final Map<UUID, Integer> NEXT_AMOUNTS = new ConcurrentHashMap<>();

    private AeAutocraftAmountOverride() {
    }

    public static void set(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        NEXT_AMOUNTS.put(player.getUUID(), amount);
        ModLogger.info("AE_EMI_CTRL_CRAFT amount-override set player={} amount={}",
                player.getGameProfile().getName(), amount);
    }

    public static int consume(ServerPlayer player, int fallback) {
        if (player == null) {
            return fallback;
        }
        Integer amount = NEXT_AMOUNTS.remove(player.getUUID());
        if (amount == null || amount <= 0) {
            return fallback;
        }
        ModLogger.info("AE_EMI_CTRL_CRAFT amount-override consume player={} original={} override={}",
                player.getGameProfile().getName(), fallback, amount);
        return amount;
    }
}
