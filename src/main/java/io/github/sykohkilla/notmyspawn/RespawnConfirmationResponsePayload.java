package io.github.sykohkilla.notmyspawn;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

record RespawnConfirmationResponsePayload(boolean accepted) implements CustomPacketPayload {
    static final Type<RespawnConfirmationResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NotMySpawn.MOD_ID, "respawn_confirmation_response")
    );

    static final StreamCodec<RegistryFriendlyByteBuf, RespawnConfirmationResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    RespawnConfirmationResponsePayload::accepted,
                    RespawnConfirmationResponsePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(RespawnConfirmationResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                RespawnChangeGuard.handleResponse(player, payload.accepted());
            }
        });
    }
}
