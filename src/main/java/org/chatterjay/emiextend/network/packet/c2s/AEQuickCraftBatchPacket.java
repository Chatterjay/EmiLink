package org.chatterjay.emiextend.network.packet.c2s;

import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import appeng.menu.slot.CraftingTermSlot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.network.PacketHelper;
import org.chatterjay.emiextend.network.packet.s2c.AEQuickCraftBatchResponsePacket;
import org.chatterjay.emiextend.util.EmiCraftHelper;
import org.chatterjay.emiextend.util.ModLogger;

/**
 * Execute an exact number of single crafts in the open AE crafting terminal.
 * The recipe has already been filled by EMI with Destination.NONE, so this
 * packet never repeats recipe filling and returns the number actually made.
 * The overflow flag still tries the player inventory first.
 */
public record AEQuickCraftBatchPacket(int containerId, int slotIndex, int amount,
        boolean allowNetworkOverflow, long requestId)
        implements CustomPacketPayload {

    public static final Type<AEQuickCraftBatchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_quick_craft_batch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEQuickCraftBatchPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AEQuickCraftBatchPacket::containerId,
                    ByteBufCodecs.VAR_INT, AEQuickCraftBatchPacket::slotIndex,
                    ByteBufCodecs.VAR_INT, AEQuickCraftBatchPacket::amount,
                    ByteBufCodecs.BOOL, AEQuickCraftBatchPacket::allowNetworkOverflow,
                    ByteBufCodecs.VAR_LONG, AEQuickCraftBatchPacket::requestId,
                    AEQuickCraftBatchPacket::new
            );

    private void handleInServer(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (amount <= 0 || amount > 256) return;
        if (player.containerMenu == null || player.containerMenu.containerId != containerId) return;
        if (!(player.containerMenu instanceof AEBaseMenu menu)) return;
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;

        var slot = menu.getSlot(slotIndex);
        if (!(slot instanceof CraftingTermSlot craftingSlot)) return;

        int crafted = 0;
        int delivered = 0;
        boolean resultSlotEmpty = false;
        for (int i = 0; i < amount; i++) {
            if (craftingSlot.getItem().isEmpty()) {
                resultSlotEmpty = true;
                ModLogger.debug("AE_EMI_CTRL_CRAFT batch stopped before index={} because result slot is empty requestId={} networkOverflow={}",
                        i, requestId, allowNetworkOverflow);
                break;
            }

            EmiCraftHelper.markSingleCraft();
            if (allowNetworkOverflow) {
                EmiCraftHelper.markSingleCraftToNetwork();
            }
            try {
                craftingSlot.doClick(InventoryAction.CRAFT_SHIFT, player);
                if (EmiCraftHelper.consumeSingleCraftProduced()) {
                    crafted++;
                }
                delivered += EmiCraftHelper.consumeSingleCraftDelivered();
            } finally {
                EmiCraftHelper.clear();
            }
        }
        player.containerMenu.broadcastChanges();
        ModLogger.debug("AE_EMI_CTRL_CRAFT batch result container={} slot={} requested={} crafted={} delivered={} resultSlotEmpty={} networkOverflow={} requestId={}",
                containerId, slotIndex, amount, crafted, delivered, resultSlotEmpty,
                allowNetworkOverflow, requestId);
        PacketDistributor.sendToPlayer(player,
                new AEQuickCraftBatchResponsePacket(requestId, crafted, delivered, resultSlotEmpty));
    }

    public static void handle(AEQuickCraftBatchPacket packet, IPayloadContext context) {
        if (packet != null) {
            PacketHelper.handleServerBound(context, () -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
