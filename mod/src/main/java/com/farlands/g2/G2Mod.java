package com.farlands.g2;

import com.farlands.g2.runtime.Watchdog;
import com.farlands.g2.util.FarConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class G2Mod implements ModInitializer {
    public void onInitialize() {
        // Multiplayer is not supported: the vertical-window machinery relies on
        // same-process client/server sync. On a dedicated server, turn it off
        // loudly instead of running half a feature.
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            for (String key : new String[] {"farlands.y.window", "farlands.y.follow",
                "farlands.y.reprime", "farlands.y.diag"}) {
                if (Boolean.getBoolean(key)) {
                    System.clearProperty(key);
                }
            }
            System.out.println("[FarLands-G2] dedicated server detected: multiplayer is unsupported; "
                + "Y window features disabled");
        }
        // Load the global template BEFORE any world exists: configuration must be
        // usable before world creation, not invented when the world starts.
        FarConfig.loadGlobalTemplate(FabricLoader.getInstance().getConfigDir());
        System.out.println("[FarLands-G2] " + FarConfig.buildVersion()
            + " wide build (real coordinates)");
        Watchdog.start();
    }
}