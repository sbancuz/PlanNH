package com.sbancuz.plannh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.sbancuz.plannh.client.ChatHandler;
import com.sbancuz.plannh.client.ImportCommand;
import com.sbancuz.plannh.gui.FlowchartScreen;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

public class ClientProxy extends CommonProxy {

    private static final KeyBinding openFlowchartKey = new KeyBinding(
        "key.neiflowchart.open",
        Keyboard.KEY_F8,
        "key.categories.neiflowchart");

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(final FMLInitializationEvent event) {
        super.init(event);

        // Vanilla has to go first since the furnace handler is likely to be overwritten
        Compat.init();

        ClientRegistry.registerKeyBinding(openFlowchartKey);

        MinecraftForge.EVENT_BUS.register(new ChatHandler());
        ClientCommandHandler.instance.registerCommand(new ImportCommand());

        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void onKeyInput(final InputEvent.KeyInputEvent event) {
        if (openFlowchartKey.isPressed()) {
            final ModularContainer container = new ModularContainer();
            container.constructClientOnly();
            final FlowchartScreen screen = FlowchartScreen.create();
            Minecraft.getMinecraft()
                .displayGuiScreen(new GuiContainerWrapper(container, screen));
        }
    }
}
