package org.chatterjay.emiextend.network.packet.c2s;

import appeng.api.stacks.AEItemKey;
import appeng.helpers.ICraftingGridMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emiextend.network.PacketHelper;
import org.chatterjay.emiextend.util.AeAutocraftAmountOverride;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.List;
import java.util.function.Supplier;

public record AEAutocraftRequestPacket(ItemStack template, int amount) {

    public static void encode(AEAutocraftRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.template);
        buf.writeVarInt(msg.amount);
    }

    public static AEAutocraftRequestPacket decode(FriendlyByteBuf buf) {
        ItemStack template = buf.readItem();
        int amount = buf.readVarInt();
        return new AEAutocraftRequestPacket(template, amount);
    }

    void handleInServer(ServerPlayer player) {
        if (template == null || template.isEmpty() || amount <= 0) {
            return;
        }
        if (!(player.containerMenu instanceof ICraftingGridMenu menu)) {
            ModLogger.debug("AE_EMI_CTRL_CRAFT direct-autocraft skip reason=not_crafting_terminal menu={}",
                    player.containerMenu == null ? "null" : player.containerMenu.getClass().getName());
            return;
        }

        AEItemKey key = AEItemKey.of(template);
        if (key == null) {
            return;
        }

        AeAutocraftAmountOverride.set(player, amount);
        menu.startAutoCrafting(List.of(new ICraftingGridMenu.AutoCraftEntry(key, List.of(0))));
        ModLogger.debug("AE_EMI_CTRL_CRAFT direct-autocraft open item={} amount={}",
                template.getHoverName().getString(), amount);
    }

    public static void handle(AEAutocraftRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        PacketHelper.handleServerBound(ctx, () -> {
            if (ctx.get().getSender() instanceof ServerPlayer sp) {
                msg.handleInServer(sp);
            }
        });
    }
}
