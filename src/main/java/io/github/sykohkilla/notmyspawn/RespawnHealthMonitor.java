package io.github.sykohkilla.notmyspawn;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
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

/** Tracks health transitions, rather than sending repeated invalid-state warnings. */
final class RespawnHealthMonitor {
    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final Map<UUID, Snapshot> LAST_STATE = new HashMap<>();
    private static final java.util.Set<UUID> CHECK_NEXT_TICK = new HashSet<>();

    private RespawnHealthMonitor() {
    }

    static boolean hasUsableRespawn(ServerPlayer player) {
        return inspect(player, true).status() == Status.VALID;
    }

    static void recordCurrentState(ServerPlayer player) {
        Snapshot snapshot = inspect(player, false);
        if (snapshot.status() == Status.NONE) {
            LAST_STATE.remove(player.getUUID());
        } else {
            LAST_STATE.put(player.getUUID(), snapshot);
        }
    }

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (CHECK_NEXT_TICK.remove(player.getUUID())) {
                check(player, false, false);
            } else if (player.tickCount % CHECK_INTERVAL_TICKS == 0) {
                check(player, false, false);
            }
        }
    }

    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            check(player, true, true);
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
            check(player, true, true);
        }
    }

    static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            check(player, false, false);
        }
    }

    static void onPlayerSpawnSet(PlayerSetSpawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
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
            BlockPos spawn = player.getRespawnPosition();
            if (spawn != null
                    && player.getRespawnDimension().equals(level.dimension())
                    && spawn.distManhattan(changedPos) <= 3) {
                CHECK_NEXT_TICK.add(player.getUUID());
            }
        }
    }

    private static void check(ServerPlayer player, boolean notifyInvalidInitialState, boolean forceLoad) {
        Snapshot current = inspect(player, forceLoad);
        Snapshot previous = LAST_STATE.put(player.getUUID(), current);

        if (current.status() == Status.NONE) {
            LAST_STATE.remove(player.getUUID());
            return;
        }

        boolean sameSpawn = previous != null && previous.sameSpawn(current);
        if (!sameSpawn) {
            if (notifyInvalidInitialState && current.status().isInvalid()) {
                notify(player, current.status());
            }
            return;
        }

        if (previous.status() != current.status()) {
            if (current.status().isInvalid()) {
                notify(player, current.status());
            } else if (current.status() == Status.VALID && previous.status().isInvalid()) {
                player.displayClientMessage(Component.translatable("message.not_my_spawn.restored"), true);
            }
        }
    }

    private static void notify(ServerPlayer player, Status status) {
        player.displayClientMessage(Component.translatable(status.translationKey), true);
    }

    private static Snapshot inspect(ServerPlayer player, boolean forceLoad) {
        BlockPos spawn = player.getRespawnPosition();
        if (spawn == null) {
            return new Snapshot(player.getRespawnDimension(), null, Status.NONE);
        }

        ServerLevel level = player.getServer().getLevel(player.getRespawnDimension());
        if (level == null) {
            return new Snapshot(player.getRespawnDimension(), spawn, Status.UNAVAILABLE);
        }
        if (forceLoad) {
            level.getChunkAt(spawn);
        } else if (!level.hasChunkAt(spawn)) {
            return new Snapshot(player.getRespawnDimension(), spawn, Status.UNAVAILABLE);
        }

        BlockState state = level.getBlockState(spawn);
        Status status;
        if (state.getBlock() instanceof BedBlock) {
            if (!BedBlock.canSetSpawn(level)) {
                status = Status.DESTROYED;
            } else {
                status = BedBlock.findStandUpPosition(
                        EntityType.PLAYER,
                        level,
                        spawn,
                        state.getValue(BedBlock.FACING),
                        player.getRespawnAngle()
                ).isPresent() ? Status.VALID : Status.OBSTRUCTED;
            }
        } else if (state.getBlock() instanceof RespawnAnchorBlock) {
            if (!RespawnAnchorBlock.canSetSpawn(level)) {
                status = Status.DESTROYED;
            } else if (state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
                status = Status.DEPLETED;
            } else {
                status = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, spawn).isPresent()
                        ? Status.VALID
                        : Status.OBSTRUCTED;
            }
        } else if (player.isRespawnForced()) {
            boolean lower = state.getBlock().isPossibleToRespawnInThis(state);
            BlockState above = level.getBlockState(spawn.above());
            status = lower && above.getBlock().isPossibleToRespawnInThis(above)
                    ? Status.VALID
                    : Status.DESTROYED;
        } else {
            Optional<ServerPlayer.RespawnPosAngle> moddedRespawn = state.getRespawnPosition(
                    EntityType.PLAYER,
                    level,
                    spawn,
                    player.getRespawnAngle()
            );
            status = moddedRespawn.isPresent() ? Status.VALID : Status.DESTROYED;
        }
        return new Snapshot(player.getRespawnDimension(), spawn.immutable(), status);
    }

    private enum Status {
        NONE(""),
        VALID(""),
        DESTROYED("message.not_my_spawn.destroyed"),
        OBSTRUCTED("message.not_my_spawn.obstructed"),
        DEPLETED("message.not_my_spawn.anchor_depleted"),
        UNAVAILABLE("");

        private final String translationKey;

        Status(String translationKey) {
            this.translationKey = translationKey;
        }

        boolean isInvalid() {
            return this == DESTROYED || this == OBSTRUCTED || this == DEPLETED;
        }
    }

    private record Snapshot(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos position, Status status) {
        boolean sameSpawn(Snapshot other) {
            return dimension.equals(other.dimension) && java.util.Objects.equals(position, other.position);
        }
    }
}
