package io.github.sykohkilla.notmyspawn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Stops only interactions that vanilla would use to replace an existing,
 * currently usable bed or anchor spawn. Confirmation replays the exact server
 * interaction through {@code ServerPlayerGameMode}, so vanilla remains the
 * authority for every bed and anchor edge case.
 */
final class RespawnChangeGuard {
    private static final long REQUEST_LIFETIME_TICKS = 200L;
    private static final double MAX_REPLAY_DISTANCE_SQUARED = 64.0;
    private static final Map<UUID, PendingInteraction> PENDING = new HashMap<>();
    private static final Map<UUID, BlockPos> ALLOW_ONCE = new HashMap<>();

    private RespawnChangeGuard() {
    }

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }

        BlockPos clickedPos = event.getPos();
        BlockPos allowedPos = ALLOW_ONCE.get(player.getUUID());
        if (clickedPos.equals(allowedPos)) {
            ALLOW_ONCE.remove(player.getUUID());
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(clickedPos);
        BlockPos spawnTarget = replacementTarget(player, state, clickedPos, event.getHand());
        if (spawnTarget == null || !RespawnHealthMonitor.hasUsableRespawn(player)) {
            return;
        }

        BlockPos currentSpawn = player.getRespawnPosition();
        if (currentSpawn == null
                || player.getRespawnDimension().equals(level.dimension()) && currentSpawn.equals(spawnTarget)) {
            return;
        }

        PendingInteraction pending = new PendingInteraction(
                level.dimension(),
                clickedPos.immutable(),
                spawnTarget.immutable(),
                state,
                event.getHand(),
                event.getHitVec(),
                level.getGameTime() + REQUEST_LIFETIME_TICKS
        );
        PENDING.put(player.getUUID(), pending);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        PacketDistributor.sendToPlayer(player, new OpenRespawnConfirmationPayload(
                currentSpawn,
                spawnTarget,
                level.dimension().equals(net.minecraft.world.level.Level.NETHER)
        ));
    }

    static void handleResponse(ServerPlayer player, boolean accepted) {
        PendingInteraction pending = PENDING.remove(player.getUUID());
        if (pending == null || !isStillValid(player, pending)) {
            return;
        }

        if (!accepted) {
            if (pending.clickedState().getBlock() instanceof BedBlock) {
                replayInteraction(player, pending, true);
            }
            return;
        }

        replayInteraction(player, pending, false);
    }

    private static void replayInteraction(ServerPlayer player, PendingInteraction pending, boolean preserveSpawn) {
        ALLOW_ONCE.put(player.getUUID(), pending.clickedPos());
        if (preserveSpawn) {
            RespawnSlotManager.suppressNextSpawnChange(player);
        }
        try {
            ItemStack heldItem = player.getItemInHand(pending.hand());
            player.gameMode.useItemOn(player, player.serverLevel(), heldItem, pending.hand(), pending.hitResult());
        } finally {
            ALLOW_ONCE.remove(player.getUUID());
            if (preserveSpawn) {
                RespawnSlotManager.finishSuppression(player);
            }
        }
    }

    static void clear(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        ALLOW_ONCE.remove(player.getUUID());
    }

    private static BlockPos replacementTarget(
            ServerPlayer player, BlockState state, BlockPos clickedPos, InteractionHand hand
    ) {
        if (state.getBlock() instanceof BedBlock) {
            if (!BedBlock.canSetSpawn(player.level()) || state.getValue(BedBlock.OCCUPIED)) {
                return null;
            }
            if (player.isSecondaryUseActive()
                    && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
                return null;
            }
            return state.getValue(BedBlock.PART) == BedPart.HEAD
                    ? clickedPos
                    : clickedPos.relative(state.getValue(BedBlock.FACING));
        }

        if (state.getBlock() instanceof RespawnAnchorBlock) {
            int charge = state.getValue(RespawnAnchorBlock.CHARGE);
            if (charge == 0 || !RespawnAnchorBlock.canSetSpawn(player.level())) {
                return null;
            }
            boolean canCharge = charge < RespawnAnchorBlock.MAX_CHARGES;
            if (canCharge && (isGlowstone(player.getItemInHand(hand))
                    || hand == InteractionHand.MAIN_HAND && isGlowstone(player.getOffhandItem()))) {
                return null;
            }
            if (player.isSecondaryUseActive()
                    && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
                return null;
            }
            return clickedPos;
        }

        return null;
    }

    private static boolean isGlowstone(ItemStack stack) {
        return stack.is(Items.GLOWSTONE);
    }

    private static boolean isStillValid(ServerPlayer player, PendingInteraction pending) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(pending.dimension())
                || level.getGameTime() > pending.expiresAt()
                || player.distanceToSqr(pending.clickedPos().getCenter()) > MAX_REPLAY_DISTANCE_SQUARED
                || !level.getBlockState(pending.clickedPos()).equals(pending.clickedState())) {
            return false;
        }
        return replacementTarget(player, pending.clickedState(), pending.clickedPos(), pending.hand())
                != null;
    }

    private record PendingInteraction(
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            BlockPos clickedPos,
            BlockPos spawnTarget,
            BlockState clickedState,
            InteractionHand hand,
            BlockHitResult hitResult,
            long expiresAt
    ) {
    }
}
