package io.github.sykohkilla.vanillasafeguards;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(VanillaSafeguards.MOD_ID)
public final class VanillaSafeguards {
    public static final String MOD_ID = "vanilla_safeguards";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VanillaSafeguards() {
        LOGGER.info("Vanilla Safeguards initialized");
    }
}
