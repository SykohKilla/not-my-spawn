package io.github.sykohkilla.notmyspawn;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

final class ClientConfirmationHandler {
    private ClientConfirmationHandler() {
    }

    static void handle(OpenRespawnConfirmationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            Screen previousScreen = minecraft.screen;
            Component message = Component.translatable(
                    "screen.not_my_spawn.confirm.message",
                    format(payload.currentSpawn()),
                    format(payload.newSpawn())
            );
            minecraft.setScreen(new ConfirmScreen(
                    accepted -> {
                        PacketDistributor.sendToServer(new RespawnConfirmationResponsePayload(accepted));
                        minecraft.setScreen(previousScreen);
                    },
                    Component.translatable("screen.not_my_spawn.confirm.title"),
                    message,
                    Component.translatable("screen.not_my_spawn.confirm.accept"),
                    Component.translatable("gui.cancel")
            ));
        });
    }

    private static String format(net.minecraft.core.BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
