package com.sbancuz.plannh.config;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.sbancuz.plannh.PlanNH;

@Config(modid = PlanNH.MODID, category = "Overrides")
@Config.LangKey("plannh.config.category.overrides")
public class ConfigOverrides {

    @Config.LangKey("plannh.config.overrides.show_burnable")
    @Config.DefaultBoolean(false)
    @Config.RequiresMcRestart
    public static boolean alwaysShowBurnableSetting;
}
