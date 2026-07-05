package com.sbancuz.plannh.client;

import com.sbancuz.plannh.data.flowchart.Plan;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public class WorldHandler {

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Plan.unloadPlan();
    }
}
