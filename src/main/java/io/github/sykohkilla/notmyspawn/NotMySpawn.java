package io.github.sykohkilla.notmyspawn;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(NotMySpawn.MOD_ID)
public final class NotMySpawn {
    public static final String MOD_ID = "not_my_spawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NotMySpawn(IEventBus modEventBus) {
        RespawnSlotManager.register(modEventBus);
        modEventBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.addListener(RespawnChangeGuard::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(RespawnSlotManager::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RespawnSlotManager::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(RespawnSlotManager::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(RespawnSlotManager::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(RespawnSlotManager::onPlayerSpawnSet);
        NeoForge.EVENT_BUS.addListener(RespawnHealthMonitor::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(RespawnHealthMonitor::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(RespawnHealthMonitor::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(RespawnHealthMonitor::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(RespawnHealthMonitor::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(RespawnHealthMonitor::onPlayerSpawnSet);
        NeoForge.EVENT_BUS.addListener(RespawnHealthMonitor::onBlockBroken);
        NeoForge.EVENT_BUS.addListener(RespawnHealthMonitor::onBlockPlaced);
        LOGGER.info("Not My Spawn! initialized");
    }
}
