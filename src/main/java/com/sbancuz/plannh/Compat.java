package com.sbancuz.plannh;

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

    AE2("appliedenergistics2", AE2Provider.class),
    BOTANIA("Botania", BotaniaProvider.class),
    ENDERIO("EnderIO", EnderIOProvider.class),
    FORESTRY("Forestry", ForestryProvider.class),
    GREGTECH("gregtech", GTProvider.class),
    THAUMCRAFT("Thaumcraft", ThaumcraftProvider.class);

    public final String modid;
    public final boolean isLoaded;

    @Getter
    private PropertyProvider extractor;
    private final Class<? extends PropertyProvider> providerFactory;

    Compat(String modid, Class<? extends PropertyProvider> providerFactory) {
        this.modid = modid;
        this.isLoaded = Loader.isModLoaded(modid);
        this.providerFactory = providerFactory;
    }

    private static <T extends PropertyProvider> PropertyProvider create(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor()
                .newInstance();
        } catch (Exception | NoClassDefFoundError _) {}
        return null;
    }

    public static void init() {
        for (Compat mod : values()) {
            if (mod.isLoaded) {
                mod.extractor = create(mod.providerFactory);
                if (mod.extractor != null) {
                    mod.extractor.register();
                }
            }
        }
    }

}
