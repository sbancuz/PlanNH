package com.sbancuz.plannh.config;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;

public class ConfigMain {

    public static void registerPlanNHConfigs() {
        ConfigurationManager.registerConfig(ConfigOverrides.class);
    }
}
