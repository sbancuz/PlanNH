package com.sbancuz.plannh;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.provider.BotaniaProvider;
import com.sbancuz.plannh.data.provider.EFRProvider;
import com.sbancuz.plannh.data.provider.EnderIOProvider;
import com.sbancuz.plannh.data.provider.ForestryProvider;
import com.sbancuz.plannh.data.provider.ThaumcraftProvider;
import com.sbancuz.plannh.data.provider.VanillaProvider;
import com.sbancuz.plannh.data.provider.gregtech.GTProvider;

import cpw.mods.fml.common.Loader;
import lombok.Getter;

public enum Compat {

    BOTANIA("Botania", BotaniaProvider.class),
    ENDERIO("EnderIO", EnderIOProvider.class),
    FORESTRY("Forestry", ForestryProvider.class),
    GREGTECH("gregtech", GTProvider.class),
    THAUMCRAFT("Thaumcraft", ThaumcraftProvider.class),
    EFR("etfuturum", EFRProvider.class);

    public final String modid;
    public final boolean isLoaded;

    @Nullable
    @Getter
    private PropertyProvider extractor;
    @Nonnull
    private final Class<? extends PropertyProvider> providerFactory;

    Compat(final String modid, final Class<? extends PropertyProvider> providerFactory) {
        this.modid = modid;
        this.isLoaded = Loader.isModLoaded(modid);
        this.providerFactory = providerFactory;
    }

    @Nullable
    private static <T extends PropertyProvider> PropertyProvider create(@Nonnull final Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor()
                .newInstance();
        } catch (Exception | NoClassDefFoundError _) {}
        return null;
    }

    public static void init() {
        MachineProfileRegistry.reset();
        RecipePropertyAPI.reset();
        new VanillaProvider().register();
        for (final Compat mod : values()) {
            if (mod.isLoaded) {
                mod.extractor = create(mod.providerFactory);
                if (mod.extractor != null) {
                    mod.extractor.register();
                }
            }
        }
    }

}
