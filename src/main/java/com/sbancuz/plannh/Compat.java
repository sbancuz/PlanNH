package com.sbancuz.plannh;

import com.sbancuz.plannh.data.RecipePropertyExtractor;
import com.sbancuz.plannh.data.extractors.EnderIOExtractor;
import com.sbancuz.plannh.data.extractors.GTExtractor;

import cpw.mods.fml.common.Loader;

public enum Compat {

    GREGTECH("gregtech", new GTExtractor()),
    ENDERIO("EnderIO", new EnderIOExtractor())
    //
    ;

    public final String modid;
    public final boolean isLoaded;
    public final RecipePropertyExtractor extractor;

    Compat(String modid, RecipePropertyExtractor extractor) {
        this.modid = modid;
        this.isLoaded = Loader.isModLoaded(modid);
        this.extractor = extractor;
    }

    public static void init() {
        for (var mod : values()) {
            if (mod.isLoaded) {
                mod.extractor.register();
            }
        }
    }
}
