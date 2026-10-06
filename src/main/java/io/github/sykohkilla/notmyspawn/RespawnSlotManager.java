package io.github.sykohkilla.notmyspawn;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Keeps vanilla's active spawn synchronized with a dimension-specific saved slot. */
final class RespawnSlotManager {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES,
                    NotMySpawn.MOD_ID);
    private static final Supplier<AttachmentType<RespawnSlotData>> RESPAWN_SLOTS = ATTACHMENTS.register(
            "respawn_slots",
            () -> AttachmentType.serializable(RespawnSlotData::new).copyOnDeath().build()
    );
    private static final Set<UUID> INTERNAL_CHANGES = new HashSet<>();
    private static final Set<UUID> CAPTURE_NEXT_TICK = new HashSet<>();
    private static final Set<UUID> SUPPRESS_CHANGES = new HashSet<>();

    private RespawnSlotManager() {
    }

    static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    static void suppressNextSpawnChange(ServerPlayer player) {
        SUPPRESS_CHANGES.add(player.getUUID());
    }

    static void finishSuppression(ServerPlayer player) {
        SUPPRESS_CHANGES.remove(player.getUUID());
    }

    static RespawnSlotData slots(ServerPlayer player) {
        return player.getData(RESPAWN_SLOTS);
    }

    static void onPlayerSpawnSet(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();
        if (SUPPRESS_CHANGES.remove(playerId)) {
            event.setCanceled(true);
            return;
        }
        if (!INTERNAL_CHANGES.contains(playerId)) {
            CAPTURE_NEXT_TICK.add(playerId);
        }
    }

    static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && CAPTURE_NEXT_TICK.remove(player.getUUID())) {
            captureActiveSpawn(player);
        }
    }

    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RespawnSlotData data = player.getData(RESPAWN_SLOTS);
            if (data.isEmpty()) {
                captureActiveSpawn(player);
            }
            activateSlot(player, player.level().dimension());
        }
    }

    static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            activateSlot(player, event.getTo());
        }
    }

    static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerId = player.getUUID();
            INTERNAL_CHANGES.remove(playerId);
            CAPTURE_NEXT_TICK.remove(playerId);
            SUPPRESS_CHANGES.remove(playerId);
        }
    }

    private static void captureActiveSpawn(ServerPlayer player) {
        RespawnSlotData data = player.getData(RESPAWN_SLOTS);
        BlockPos position = player.getRespawnPosition();
        if (position == null) {
            data.clear(player.level().dimension());
            return;
        }
        data.set(new RespawnSlotData.RespawnPoint(
                player.getRespawnDimension(),
                position,
                player.getRespawnAngle(),
                player.isRespawnForced()
        ));
    }

    private static void activateSlot(ServerPlayer player, net.minecraft.resources.ResourceKey<Level> dimension) {
        RespawnSlotData.RespawnPoint point = player.getData(RESPAWN_SLOTS).get(dimension);
        UUID playerId = player.getUUID();
        INTERNAL_CHANGES.add(playerId);
        try {
            if (point == null) {
                player.setRespawnPosition(dimension, null, 0.0F, false, false);
            } else {
                player.setRespawnPosition(
                        point.dimension(), point.position(), point.angle(), point.forced(), false
                );
            }
        } finally {
            INTERNAL_CHANGES.remove(playerId);
        }
    }
}
