package com.farlands.g2;

import com.farlands.g2.runtime.Watchdog;
import com.farlands.g2.util.FarConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class G2Mod implements ModInitializer {
    public void onInitialize() {
        // Load the global template BEFORE any world exists: configuration must be
        // usable before world creation, not invented when the world starts.
        FarConfig.loadGlobalTemplate(FabricLoader.getInstance().getConfigDir());
        System.out.println("[FarLands-G2] " + FarConfig.buildVersion()
            + " wide build (real coordinates)");
        Watchdog.start();
    }
}