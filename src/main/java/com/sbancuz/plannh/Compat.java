package com.sbancuz.plannh;

import java.util.function.Supplier;

import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.provider.AE2Provider;
import com.sbancuz.plannh.data.provider.BotaniaProvider;
import com.sbancuz.plannh.data.provider.EnderIOProvider;
import com.sbancuz.plannh.data.provider.ForestryProvider;
import com.sbancuz.plannh.data.provider.GTProvider;
import com.sbancuz.plannh.data.provider.ThaumcraftProvider;

import cpw.mods.fml.common.Loader;
import lombok.Getter;

public enum Compat {

    AE2("appliedenergistics2", AE2Provider::new),
    BOTANIA("Botania", BotaniaProvider::new),
    ENDERIO("EnderIO", EnderIOProvider::new),
    FORESTRY("Forestry", ForestryProvider::new),
    GREGTECH("gregtech", GTProvider::new),
    THAUMCRAFT("Thaumcraft", ThaumcraftProvider::new);

    public final String modid;
    public final boolean isLoaded;

    @Getter
    private PropertyProvider extractor;
    private final Supplier<PropertyProvider> providerFactory;

    Compat(String modid, Supplier<PropertyProvider> providerFactory) {
        this.modid = modid;
        this.isLoaded = Loader.isModLoaded(modid);
        this.providerFactory = providerFactory;
    }

    public static void init() {
        for (Compat mod : values()) {
            if (mod.isLoaded) {
                mod.extractor = mod.providerFactory.get();
                mod.extractor.register();
            }
        }
    }

}
