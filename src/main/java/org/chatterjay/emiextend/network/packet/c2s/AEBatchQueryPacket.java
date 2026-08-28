package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.network.AE2GridQueryUtil;
import org.chatterjay.emiextend.network.packet.s2c.AEBatchQueryResponsePacket;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch AE network query — client sends multiple items at once, server responds
 * with count and optionally craftability for each. Delegates query logic to AE2GridQueryUtil.
 */
public record AEBatchQueryPacket(List<ItemStack> stacks, boolean queryCraftability,
        boolean durabilityCompatible) implements CustomPacketPayload {
    public static final Type<AEBatchQueryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_batch_query"));

    public AEBatchQueryPacket(List<ItemStack> stacks) {
        this(stacks, true, false);
    }

    public AEBatchQueryPacket(List<ItemStack> stacks, boolean queryCraftability) {
        this(stacks, queryCraftability, false);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AEBatchQueryPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(RegistryFriendlyByteBuf buf, AEBatchQueryPacket packet) {
                    buf.writeVarInt(packet.stacks().size());
                    for (var stack : packet.stacks()) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                    }
                    buf.writeBoolean(packet.queryCraftability());
                    buf.writeBoolean(packet.durabilityCompatible());
                }

                @Override
                public AEBatchQueryPacket decode(RegistryFriendlyByteBuf buf) {
                    int size = buf.readVarInt();
                    var stacks = new ArrayList<ItemStack>(size);
                    for (int i = 0; i < size; i++) {
                        stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    }
                    boolean queryCraftability = buf.readBoolean();
                    // Keep accepting packets from older EmiLink servers that
                    // did not have the optional BOM durability flag.
                    boolean durabilityCompatible = buf.readableBytes() > 0 && buf.readBoolean();
                    return new AEBatchQueryPacket(stacks, queryCraftability, durabilityCompatible);
                }
            };

    private void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null || stacks == null || stacks.isEmpty()) return;
        if (!AE2Proxy.isLoaded()) return;

        var results = new ArrayList<AEBatchQueryResponsePacket.Entry>();

        try {
            var menu = player.containerMenu;
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            Object grid = aeBaseMenuClass.isInstance(menu)
                    ? AE2GridQueryUtil.resolveGrid(aeBaseMenuClass, menu)
                    : null;
            if (grid == null) {
                // A direct refresh can happen from a normal inventory screen.
                // In that case use the player's wireless terminal instead of
                // requiring an AEBaseMenu just to query one changed item.
                grid = AEDepositPacket.resolveGridFromWirelessTerminal(player);
            }
            if (grid == null) return;

            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");

            for (var stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;

                long count = 0;
                boolean craftable = false;

                try {
                    Object aeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, stack);
                    if (aeKey != null) {
                        count = durabilityCompatible && stack.isDamageableItem()
                                ? AE2GridQueryUtil.queryItemCountForBom(grid, aeKey, stack)
                                : AE2GridQueryUtil.queryItemCount(grid, aeKey);
                        craftable = queryCraftability && AE2GridQueryUtil.queryCraftability(grid, aeKey);
                    }
                } catch (Exception e) {
                    // skip item
                }

                results.add(new AEBatchQueryResponsePacket.Entry(stack, count, craftable, queryCraftability));
            }
        } catch (Exception e) {
            ModLogger.debug("AEBatchQuery: error resolving grid: {}", e.getMessage());
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            ModLogger.debug("AEBatchQuery: sending response entries={} queryCraftability={} durabilityCompatible={} stacks={} menu={}",
                    results.size(), queryCraftability, durabilityCompatible, stacks.size(),
                    player.containerMenu.getClass().getName());
            PacketDistributor.sendToPlayer(sp, new AEBatchQueryResponsePacket(results));
        }
    }

    public static void handle(final AEBatchQueryPacket packet, final IPayloadContext context) {
        if (packet != null && context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
