package io.github.sykohkilla.notmyspawn;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

record OpenRespawnConfirmationPayload(BlockPos currentSpawn, BlockPos newSpawn, boolean nether)
        implements CustomPacketPayload {
    static final Type<OpenRespawnConfirmationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NotMySpawn.MOD_ID, "open_respawn_confirmation")
    );

    static final StreamCodec<RegistryFriendlyByteBuf, OpenRespawnConfirmationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeBlockPos(payload.currentSpawn());
                        buffer.writeBlockPos(payload.newSpawn());
                        buffer.writeBoolean(payload.nether());
                    },
                    buffer -> new OpenRespawnConfirmationPayload(
                            buffer.readBlockPos(), buffer.readBlockPos(), buffer.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(OpenRespawnConfirmationPayload payload, IPayloadContext context) {
        // This handler only runs on the receiving client. Keeping the client call
        // in the method body prevents dedicated servers from resolving GUI classes
        // while registering the shared payload type.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientConfirmationHandler.handle(payload, context);
        }
    }
}
