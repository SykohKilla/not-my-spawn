package io.github.sykohkilla.notmyspawn;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NotMySpawn.MOD_ID)
public final class NotMySpawn {
    public static final String MOD_ID = "not_my_spawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NotMySpawn() {
        LOGGER.info("Not My Spawn! initialized");
    }
}
