package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.network.AE2GridQueryUtil;
import org.chatterjay.emiextend.network.PacketHelper;
import org.chatterjay.emiextend.util.ModLogger;

/**
 * Client→Server: Deposit an item into the AE2 network.
 * slotIndex = -1 for cursor item, >= 0 for inventory slot (batch deposit).
 * After insert, the server clears the source slot to prevent duplication.
 */
public record AEDepositPacket(ItemStack stack, int slotIndex, boolean dropOnFailure) implements CustomPacketPayload {

    public AEDepositPacket(ItemStack stack, int slotIndex) {
        this(stack, slotIndex, false);
    }
    public static final Type<AEDepositPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEDepositPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, AEDepositPacket::stack,
                    ByteBufCodecs.VAR_INT, AEDepositPacket::slotIndex,
                    ByteBufCodecs.BOOL, AEDepositPacket::dropOnFailure,
                    AEDepositPacket::new
            );

    void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null || stack == null || stack.isEmpty()) return;

        ItemStack source = getSource(player, slotIndex);
        if (source.isEmpty() || !ItemStack.isSameItemSameComponents(source, stack)) {
            ModLogger.debug("AEDeposit: source changed before deposit slot={} requested={}", slotIndex,
                    stack.getHoverName().getString());
            return;
        }

        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
            Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
            Object modulate = actionableClass.getField("MODULATE").get(null);
            Object actionSource = actionSourceClass.getMethod("ofPlayer", Player.class).invoke(null, player);

            Object inventory = resolveInventoryFromMenu(player);
            if (inventory == null) {
                inventory = resolveInventoryFromWirelessTerminal(player, aeItemKeyClass);
            }
            if (inventory == null) {
                ModLogger.warn("AEDeposit: no grid available");
                if (dropOnFailure) dropIfRequested(player, source, slotIndex, "no grid");
                return;
            }

            int requested = Math.min(source.getCount(), stack.getCount());
            ItemStack requestedStack = source.copyWithCount(requested);
            Object aeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, requestedStack);
            if (aeKey == null) {
                ModLogger.warn("AEDeposit: failed to create AEKey for {}", stack);
                if (dropOnFailure) dropIfRequested(player, source, slotIndex, "AE key unavailable");
                return;
            }

            var insertMethod = inventory.getClass().getMethod("insert", aeKeyClass, long.class, actionableClass, actionSourceClass);
            long inserted = (long) insertMethod.invoke(inventory, aeKey, (long) requested, modulate, actionSource);
            int accepted = (int) Math.max(0, Math.min(requested, inserted));
            ItemStack remaining = source.copy();
            remaining.shrink(accepted);

            // The server owns the source slot. The client must not clear it
            // optimistically because a full AE network may accept nothing.
            if (slotIndex == -1) {
                player.containerMenu.setCarried(remaining);
                player.containerMenu.broadcastChanges();
            } else if (slotIndex >= 0 && slotIndex < player.getInventory().items.size()) {
                player.getInventory().setItem(slotIndex, remaining);
                player.containerMenu.broadcastChanges();
            }

            ModLogger.debug("AEDeposit: item={} requested={} accepted={} remaining={} dropOnFailure={} slot={}",
                    stack.getHoverName().getString(), requested, accepted, remaining.getCount(),
                    dropOnFailure, slotIndex);

            if (dropOnFailure && !remaining.isEmpty()) {
                dropIfRequested(player, remaining, slotIndex, "AE capacity");
            }

        } catch (Exception e) {
            ModLogger.warn("AEDeposit: error: {}", e.getMessage());
            if (dropOnFailure) dropIfRequested(player, source, slotIndex, "exception");
        }
    }

    private static ItemStack getSource(Player player, int slotIndex) {
        if (slotIndex == -1) return player.containerMenu.getCarried();
        if (slotIndex >= 0 && slotIndex < player.getInventory().items.size()) {
            return player.getInventory().getItem(slotIndex);
        }
        return ItemStack.EMPTY;
    }

    private static void dropIfRequested(Player player, ItemStack stack, int slotIndex, String reason) {
        if (stack == null || stack.isEmpty()) return;
        ModLogger.debug("AEDeposit: dropping undelivered item={} count={} slot={} reason={}",
                stack.getHoverName().getString(), stack.getCount(), slotIndex, reason);
        player.drop(stack.copy(), false);
        if (slotIndex == -1) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        } else if (slotIndex >= 0 && slotIndex < player.getInventory().items.size()) {
            player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
        }
        player.containerMenu.broadcastChanges();
    }

    static Object resolveInventoryFromMenu(Player player) {
        try {
            var menu = player.containerMenu;
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(menu)) return null;

            Object grid = AE2GridQueryUtil.resolveGrid(aeBaseMenuClass, menu);
            if (grid == null) return null;

            Object storageSvc = AE2GridQueryUtil.callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            if (storageSvc == null) return null;

            return AE2GridQueryUtil.callMethodOnBestMatch(storageSvc, "getInventory");
        } catch (Exception e) {
            return null;
        }
    }

    static Object resolveInventoryFromWirelessTerminal(Player player, Class<?> aeItemKeyClass) {
        try {
            Object grid = resolveGridFromWirelessTerminal(player);
            if (grid == null) return null;

            Object storageSvc = AE2GridQueryUtil.callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            if (storageSvc == null) return null;

            return AE2GridQueryUtil.callMethodOnBestMatch(storageSvc, "getInventory");
        } catch (Exception e) {
            ModLogger.warn("AEDeposit: wireless terminal error: {}", e.getMessage());
            return null;
        }
    }

    /** Resolve the AE grid linked to the player's wireless terminal. */
    static Object resolveGridFromWirelessTerminal(Player player) {
        try {
            ItemStack terminal = findWirelessTerminal(player);
            if (terminal == null || terminal.isEmpty()) return null;

            java.util.function.Consumer<?> noop = msg -> {};
            return terminal.getItem().getClass()
                    .getMethod("getLinkedGrid", ItemStack.class, net.minecraft.world.level.Level.class, java.util.function.Consumer.class)
                    .invoke(terminal.getItem(), terminal, player.level(), noop);
        } catch (Exception e) {
            ModLogger.warn("AEDeposit: wireless grid error: {}", e.getMessage());
            return null;
        }
    }

    private static ItemStack findWirelessTerminal(Player player) {
        try {
            Class<?> wtClass = Class.forName("appeng.items.tools.powered.WirelessTerminalItem");
            var inv = player.getInventory();
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.getItem(i);
                if (wtClass.isInstance(s.getItem())) return s;
            }
            if (wtClass.isInstance(player.getOffhandItem().getItem())) {
                return player.getOffhandItem();
            }
            // Check curios slots
            ItemStack curiosStack = findInCurios(player, wtClass);
            if (!curiosStack.isEmpty()) {
                return curiosStack;
            }
        } catch (Exception e) {
            ModLogger.warn("AEDeposit: findWirelessTerminal error: {}", e.getMessage());
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findInCurios(Player player, Class<?> targetClass) {
        try {
            if (ModList.get() == null || !ModList.get().isLoaded("curios")) return ItemStack.EMPTY;
            var curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCuriosInventory = curiosApi.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            Optional<?> opt = (Optional<?>) getCuriosInventory.invoke(null, player);
            if (opt.isEmpty()) return ItemStack.EMPTY;
            Object handler = opt.get();
            // ICuriosItemHandler.findFirstCurio(Predicate<ItemStack>) → Optional<SlotResult>
            java.util.function.Predicate<ItemStack> predicate = s -> !s.isEmpty() && targetClass.isInstance(s.getItem());
            Optional<?> result = (Optional<?>) handler.getClass()
                    .getMethod("findFirstCurio", java.util.function.Predicate.class)
                    .invoke(handler, predicate);
            if (result.isPresent()) {
                Object slotResult = result.get();
                return (ItemStack) slotResult.getClass().getMethod("stack").invoke(slotResult);
            }
        } catch (Exception e) {
            ModLogger.warn("AEDeposit: findInCurios error: {}", e.getMessage());
        }
        return ItemStack.EMPTY;
    }

    public static void handle(final AEDepositPacket packet, final IPayloadContext context) {
        PacketHelper.handleServerBound(context, () -> packet.handleInServer(context));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
