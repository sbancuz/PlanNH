package com.sbancuz.plannh;

import com.sbancuz.plannh.client.ImportCommand;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(final FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
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
