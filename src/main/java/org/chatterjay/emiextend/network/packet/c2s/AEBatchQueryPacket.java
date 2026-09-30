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

        List<AEBatchQueryResponsePacket.Entry> results = List.of();
        String source = "none";

        try {
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            var menu = player.containerMenu;
            Object grid = aeBaseMenuClass.isInstance(menu)
                    ? AE2GridQueryUtil.resolveGrid(aeBaseMenuClass, menu)
                    : null;

            if (grid != null) {
                results = queryBatch(grid, stacks, queryCraftability, durabilityCompatible);
                source = "menu:" + menu.getClass().getSimpleName();
            }

            if (!hasAnyData(results)) {
                // A machine menu can resolve to a grid that is not the network the
                // player works with (subnets, unconnected machines). Answering with
                // those zeros would blank out every badge until the terminal is
                // reopened, so retry through the wireless terminal instead.
                Object wireless = AEDepositPacket.resolveGridFromWirelessTerminal(player);
                if (wireless != null && wireless != grid) {
                    var fallback = queryBatch(wireless, stacks, queryCraftability, durabilityCompatible);
                    if (hasAnyData(fallback)) {
                        results = fallback;
                        source = "wireless";
                    } else if (results.isEmpty()) {
                        results = fallback;
                        source = "wireless:empty";
                    }
                }
            }
        } catch (Exception e) {
            ModLogger.debug("AEBatchQuery: error resolving grid: {}", e.getMessage());
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            ModLogger.debug("AEBatchQuery: sending response source={} entries={} queryCraftability={} durabilityCompatible={} stacks={} menu={}",
                    source, results.size(), queryCraftability, durabilityCompatible, stacks.size(),
                    player.containerMenu.getClass().getName());
            PacketDistributor.sendToPlayer(sp, new AEBatchQueryResponsePacket(results));
        }
    }

    /** Run one batch against a resolved grid. Entries that fail are skipped, never cached as zero. */
    private static List<AEBatchQueryResponsePacket.Entry> queryBatch(Object grid, List<ItemStack> stacks,
                                                                    boolean queryCraftability,
                                                                    boolean durabilityCompatible) {
        var results = new ArrayList<AEBatchQueryResponsePacket.Entry>();
        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
            var itemKeyOf = aeItemKeyClass.getMethod("of", ItemStack.class);
            var unwrapGenericStack = genericStackClass.getMethod("unwrapItemStack", ItemStack.class);
            var genericStackWhat = genericStackClass.getMethod("what");

            for (var stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;

                long count = 0;
                boolean craftable = false;

                try {
                    // Non-item AE keys (fluids, chemicals, ...) reach the server
                    // wrapped in an ItemStack so the client can cache them
                    // without holding AE2 types. Unwrap them back into a key.
                    Object genericStack = unwrapGenericStack.invoke(null, stack);
                    if (genericStack != null) {
                        Object aeKey = genericStackWhat.invoke(genericStack);
                        if (aeKey != null) {
                            count = AE2GridQueryUtil.queryItemCount(grid, aeKey);
                            craftable = queryCraftability && AE2GridQueryUtil.queryCraftability(grid, aeKey);
                        }
                    } else {
                        Object aeKey = itemKeyOf.invoke(null, stack);
                        if (aeKey != null) {
                            count = durabilityCompatible && stack.isDamageableItem()
                                    ? AE2GridQueryUtil.queryItemCountForBom(grid, aeKey, stack)
                                    : AE2GridQueryUtil.queryItemCount(grid, aeKey);
                            craftable = queryCraftability && AE2GridQueryUtil.queryCraftability(grid, aeKey);
                        }
                    }
                } catch (Exception e) {
                    // Skip the failed entry: reporting zero here would poison the
                    // client cache with a value that is not known to be correct.
                    continue;
                }

                results.add(new AEBatchQueryResponsePacket.Entry(stack, count, craftable, queryCraftability));
            }
        } catch (Exception e) {
            ModLogger.debug("AEBatchQuery: key reflection unavailable: {}", e.getMessage());
        }
        return results;
    }

    private static boolean hasAnyData(List<AEBatchQueryResponsePacket.Entry> entries) {
        for (var entry : entries) {
            if (entry.count() > 0 || entry.craftable()) return true;
        }
        return false;
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
