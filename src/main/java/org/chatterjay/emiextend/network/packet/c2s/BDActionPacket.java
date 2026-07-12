package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.BDProxy;
import org.chatterjay.emiextend.network.PacketHelper;

public record BDActionPacket(ItemStack targetStack, int action) implements CustomPacketPayload {
    // action: 0 = extract from network to player inventory
    // action: 1 = mass craft (Space+Click on result slot)
    // action: 2 = single craft (Ctrl+Click on result slot or B key)
    // action: 3 = BoM quick craft: make BD return crafting-grid leftovers to network
    // action: 4 = BoM quick craft: restore BD crafting-grid return direction
    // action: 5 = deposit matching player-inventory stacks to BD network
    // action: 6 = clean BD crafting-grid leftovers to BD network
    // action: 7 = single craft result directly into BD network

    public static final Type<BDActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "bd_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BDActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BDActionPacket::targetStack,
                    ByteBufCodecs.VAR_INT,
                    BDActionPacket::action,
                    BDActionPacket::new
            );

    private void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null) return;

        switch (action) {
            case 0 -> {
                if (targetStack == null || targetStack.isEmpty()) return;
                BDProxy.extractFromNetwork(player, targetStack);
            }
            case 1 -> {
                BDProxy.massCraft(player);
            }
            case 2 -> {
                BDProxy.singleCraft(player);
            }
            case 3 -> {
                BDProxy.beginQuickCraftReturnToInventory(player);
            }
            case 4 -> {
                BDProxy.restoreQuickCraftReturnDirection(player);
            }
            case 5 -> {
                if (targetStack == null || targetStack.isEmpty()) return;
                BDProxy.depositMatchingToNetwork(player, targetStack);
            }
            case 6 -> {
                BDProxy.cleanCraftSlotsToNetwork(player);
            }
            case 7 -> {
                BDProxy.singleCraftToNetwork(player);
            }
        }
    }

    public static void handle(final BDActionPacket packet, final IPayloadContext context) {
        PacketHelper.handleServerBound(context, () -> packet.handleInServer(context));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
