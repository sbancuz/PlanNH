package com.sbancuz.plannh;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class Config {

    /** Dev diagnostic: log a headless repro of every arrow-routing recompute. */
    public static boolean debugRouteDump = false;

    public static void synchronizeConfiguration(final File configFile) {
        final Configuration configuration = new Configuration(configFile);

        debugRouteDump = configuration.getBoolean(
            "debugRouteDump",
            "debug",
            false,
            "Log a replayable dump of the arrow-routing input on every route recompute");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
