package com.sbancuz.plannh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.sbancuz.plannh.data.provider.VanillaProvider;
import com.sbancuz.plannh.gui.FlowchartGuiContainer;
import com.sbancuz.plannh.gui.FlowchartScreen;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

public class ClientProxy extends CommonProxy {

    private static KeyBinding openFlowchartKey;

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        Compat.init();
        new VanillaProvider().register();

        openFlowchartKey = new KeyBinding("key.neiflowchart.open", Keyboard.KEY_F8, "key.categories.neiflowchart");
        ClientRegistry.registerKeyBinding(openFlowchartKey);

        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openFlowchartKey != null && openFlowchartKey.isPressed()) {
            ModularContainer container = new ModularContainer();
            container.constructClientOnly();
            FlowchartScreen screen = FlowchartScreen.create();
            FlowchartGuiContainer wrapper = new FlowchartGuiContainer(container, screen);
            Minecraft.getMinecraft()
                .displayGuiScreen(wrapper);
        }
    }
}
