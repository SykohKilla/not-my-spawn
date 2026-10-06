package io.github.sykohkilla.notmyspawn;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Tracks validity transitions independently for the saved Overworld and Nether respawns. */
final class RespawnHealthMonitor {
    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final Map<UUID, EnumMap<Slot, Snapshot>> LAST_STATE = new HashMap<>();
    private static final Set<UUID> CHECK_NEXT_TICK = new HashSet<>();

    private RespawnHealthMonitor() {
    }

    static boolean hasUsableRespawn(ServerPlayer player) {
        RespawnSlotData.RespawnPoint point = RespawnSlotManager.slots(player).get(player.level().dimension());
        return inspect(player, point, true).status() == Status.VALID;
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (CHECK_NEXT_TICK.remove(player.getUUID())) {
                check(player, false);
            } else if (player.tickCount % CHECK_INTERVAL_TICKS == 0) {
                check(player, false);
            }
        }
    }

    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Establish a baseline. Alerts are transition-based, so login itself is silent.
            LAST_STATE.put(player.getUUID(), inspectAll(player, true));
        }
    }

    static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_STATE.remove(player.getUUID());
            CHECK_NEXT_TICK.remove(player.getUUID());
            RespawnChangeGuard.clear(player);
        }
    }

    static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            check(player, true);
        }
    }

    static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            check(player, false);
        }
    }

    static void onPlayerSpawnSet(PlayerSetSpawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            CHECK_NEXT_TICK.add(player.getUUID());
        }
    }

    static void onBlockBroken(BlockEvent.BreakEvent event) {
        scheduleRelevantChecks(event.getLevel(), event.getPos());
    }

    static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        scheduleRelevantChecks(event.getLevel(), event.getPos());
    }

    private static void scheduleRelevantChecks(net.minecraft.world.level.LevelAccessor accessor, BlockPos changedPos) {
        if (!(accessor instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            RespawnSlotData data = RespawnSlotManager.slots(player);
            if (isNear(data.overworld(), level, changedPos) || isNear(data.nether(), level, changedPos)) {
                CHECK_NEXT_TICK.add(player.getUUID());
            }
        }
    }

    private static boolean isNear(
            RespawnSlotData.RespawnPoint point, ServerLevel level, BlockPos changedPos
    ) {
        return point != null
                && point.dimension().equals(level.dimension())
                && point.position().distManhattan(changedPos) <= 3;
    }

    private static void check(ServerPlayer player, boolean forceLoad) {
        EnumMap<Slot, Snapshot> current = inspectAll(player, forceLoad);
        EnumMap<Slot, Snapshot> previous = LAST_STATE.put(player.getUUID(), current);
        if (previous == null) {
            return;
        }

        for (Slot slot : Slot.values()) {
            Snapshot before = previous.get(slot);
            Snapshot after = current.get(slot);
            if (after != null && after.status() == Status.UNAVAILABLE && before != null) {
                // Do not forget the last known state merely because its chunk is unloaded.
                current.put(slot, before);
                continue;
            }
            if (before == null || after == null || !before.sameSpawn(after)) {
                continue;
            }
            if (before.status() == Status.VALID && after.status().isInvalid()) {
                player.displayClientMessage(Component.translatable("message.not_my_spawn.invalid"), true);
            } else if (before.status().isInvalid() && after.status() == Status.VALID) {
                player.displayClientMessage(Component.translatable("message.not_my_spawn.restored"), true);
            }
        }
    }

    private static EnumMap<Slot, Snapshot> inspectAll(ServerPlayer player, boolean forceLoad) {
        RespawnSlotData data = RespawnSlotManager.slots(player);
        EnumMap<Slot, Snapshot> snapshots = new EnumMap<>(Slot.class);
        snapshots.put(Slot.OVERWORLD, inspect(player, data.overworld(), forceLoad));
        snapshots.put(Slot.NETHER, inspect(player, data.nether(), forceLoad));
        return snapshots;
    }

    private static Snapshot inspect(
            ServerPlayer player, RespawnSlotData.RespawnPoint point, boolean forceLoad
    ) {
        if (point == null) {
            return Snapshot.none();
        }

        ServerLevel level = player.getServer().getLevel(point.dimension());
        if (level == null) {
            return new Snapshot(point.dimension(), point.position(), Status.UNAVAILABLE);
        }
        if (forceLoad) {
            level.getChunkAt(point.position());
        } else if (!level.hasChunkAt(point.position())) {
            // An unloaded chunk is unknown, not a validity transition.
            return new Snapshot(point.dimension(), point.position(), Status.UNAVAILABLE);
        }

        BlockState state = level.getBlockState(point.position());
        Status status;
        if (state.getBlock() instanceof BedBlock) {
            if (!BedBlock.canSetSpawn(level)) {
                status = Status.INVALID;
            } else {
                status = BedBlock.findStandUpPosition(
                        EntityType.PLAYER,
                        level,
                        point.position(),
                        state.getValue(BedBlock.FACING),
                        point.angle()
                ).isPresent() ? Status.VALID : Status.INVALID;
            }
        } else if (state.getBlock() instanceof RespawnAnchorBlock) {
            if (!RespawnAnchorBlock.canSetSpawn(level)
                    || state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
                status = Status.INVALID;
            } else {
                status = RespawnAnchorBlock.findStandUpPosition(
                        EntityType.PLAYER, level, point.position()
                ).isPresent() ? Status.VALID : Status.INVALID;
            }
        } else if (point.forced()) {
            boolean lower = state.getBlock().isPossibleToRespawnInThis(state);
            BlockState above = level.getBlockState(point.position().above());
            status = lower && above.getBlock().isPossibleToRespawnInThis(above)
                    ? Status.VALID
                    : Status.INVALID;
        } else {
            Optional<ServerPlayer.RespawnPosAngle> moddedRespawn = state.getRespawnPosition(
                    EntityType.PLAYER,
                    level,
                    point.position(),
                    point.angle()
            );
            status = moddedRespawn.isPresent() ? Status.VALID : Status.INVALID;
        }
        return new Snapshot(point.dimension(), point.position(), status);
    }

    private enum Slot {
        OVERWORLD,
        NETHER
    }

    private enum Status {
        NONE,
        VALID,
        INVALID,
        UNAVAILABLE;

        boolean isInvalid() {
            return this == INVALID;
        }
    }

    private record Snapshot(
            net.minecraft.resources.ResourceKey<Level> dimension, BlockPos position, Status status
    ) {
        static Snapshot none() {
            return new Snapshot(Level.OVERWORLD, null, Status.NONE);
        }

        boolean sameSpawn(Snapshot other) {
            return dimension.equals(other.dimension) && java.util.Objects.equals(position, other.position);
        }
    }
}
