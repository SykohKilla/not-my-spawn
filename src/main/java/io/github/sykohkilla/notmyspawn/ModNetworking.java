package io.github.sykohkilla.notmyspawn;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworking {
    private ModNetworking() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                OpenRespawnConfirmationPayload.TYPE,
                OpenRespawnConfirmationPayload.STREAM_CODEC,
                OpenRespawnConfirmationPayload::handle
        );
        registrar.playToServer(
                RespawnConfirmationResponsePayload.TYPE,
                RespawnConfirmationResponsePayload.STREAM_CODEC,
                RespawnConfirmationResponsePayload::handle
        );
    }
}
