package org.chatterjay.emiextend.network.packet.c2s;

import appeng.api.stacks.AEItemKey;
import appeng.helpers.ICraftingGridMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.network.PacketHelper;
import org.chatterjay.emiextend.util.AeAutocraftAmountOverride;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.List;

public record AEAutocraftRequestPacket(ItemStack template, int amount) implements CustomPacketPayload {
    public static final Type<AEAutocraftRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_autocraft_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEAutocraftRequestPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, AEAutocraftRequestPacket::template,
                    ByteBufCodecs.VAR_INT, AEAutocraftRequestPacket::amount,
                    AEAutocraftRequestPacket::new
            );

    void handleInServer(final IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || template == null || template.isEmpty() || amount <= 0) {
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

    public static void handle(final AEAutocraftRequestPacket packet, final IPayloadContext context) {
        PacketHelper.handleServerBound(context, () -> packet.handleInServer(context));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
