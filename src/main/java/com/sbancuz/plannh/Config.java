package com.sbancuz.plannh;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class Config {

    public static void synchronizeConfiguration(final File configFile) {
        final Configuration configuration = new Configuration(configFile);

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
