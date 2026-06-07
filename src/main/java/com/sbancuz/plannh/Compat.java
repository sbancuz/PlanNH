package com.sbancuz.plannh;

import com.sbancuz.plannh.data.RecipePropertyExtractor;
import com.sbancuz.plannh.data.extractors.AE2Extractor;
import com.sbancuz.plannh.data.extractors.BotaniaExtractor;
import com.sbancuz.plannh.data.extractors.EnderIOExtractor;
import com.sbancuz.plannh.data.extractors.ForestryExtractor;
import com.sbancuz.plannh.data.extractors.GTExtractor;
import com.sbancuz.plannh.data.extractors.ThaumcraftExtractor;

import cpw.mods.fml.common.Loader;

public enum Compat {

    GREGTECH("gregtech", new GTExtractor()),
    ENDERIO("EnderIO", new EnderIOExtractor()),
    THAUMCRAFT("Thaumcraft", new ThaumcraftExtractor()),
    BOTANIA("Botania", new BotaniaExtractor()),
    FORESTRY("Forestry", new ForestryExtractor()),
    AE2("appliedenergistics2", new AE2Extractor())
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
