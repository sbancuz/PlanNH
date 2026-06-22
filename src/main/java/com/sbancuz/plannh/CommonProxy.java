package com.sbancuz.plannh;

import com.sbancuz.plannh.client.ImportCommand;

import com.sbancuz.plannh.config.ConfigMain;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(final FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        ConfigMain.registerPlanNHConfigs();
    }

    public void init(final FMLInitializationEvent event) {}

    public void postInit(final FMLPostInitializationEvent event) {}

    public void serverStarting(final FMLServerStartingEvent event) {
        if (!event.getServer()
            .isDedicatedServer()) {
            event.registerServerCommand(new ImportCommand());
        }
    }
}
