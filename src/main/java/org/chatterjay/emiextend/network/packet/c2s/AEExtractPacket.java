package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.network.PacketHelper;
import org.chatterjay.emiextend.util.ModLogger;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server: Extract items from AE network into player inventory.
 * Used after QuickCraft to retrieve missing final goal items.
 */
public record AEExtractPacket(ItemStack template, int count) implements CustomPacketPayload {
    public static final Type<AEExtractPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_extract"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEExtractPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, AEExtractPacket::template,
                    ByteBufCodecs.VAR_INT, AEExtractPacket::count,
                    AEExtractPacket::new
            );

    void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null || template == null || template.isEmpty() || count <= 0) return;

        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
            Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
            Object modulate = actionableClass.getField("MODULATE").get(null);
            Object actionSource = actionSourceClass.getMethod("ofPlayer", Player.class).invoke(null, player);

            Object inventory = AEDepositPacket.resolveInventoryFromMenu(player);
            if (inventory == null) {
                inventory = AEDepositPacket.resolveInventoryFromWirelessTerminal(player, aeItemKeyClass);
            }
            if (inventory == null) {
                ModLogger.warn("AEExtract: grid不可用, 无法提取 {} x{}", template.getHoverName().getString(), count);
                return;
            }

            Object aeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, template);
            if (aeKey == null) return;

            var extractMethod = inventory.getClass().getMethod("extract", aeKeyClass, long.class, actionableClass, actionSourceClass);
            long extracted = (long) extractMethod.invoke(inventory, aeKey, (long) count, modulate, actionSource);
            if (extracted <= 0) return;

            var toStack = aeItemKeyClass.getMethod("toStack");
            ItemStack result = (ItemStack) toStack.invoke(aeKey);
            result.setCount((int) extracted);

            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
            player.containerMenu.broadcastChanges();
            ModLogger.debug("AEExtract: 从AE提取 {} x{} 到背包", template.getHoverName().getString(), extracted);
        } catch (Exception e) {
            ModLogger.warn("AEExtract: error: {}", e.getMessage());
        }
    }

    public static void handle(final AEExtractPacket packet, final IPayloadContext context) {
        PacketHelper.handleServerBound(context, () -> packet.handleInServer(context));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
