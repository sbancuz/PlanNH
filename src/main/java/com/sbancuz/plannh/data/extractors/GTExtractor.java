package com.sbancuz.plannh.data.extractors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.FlowchartNode;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;
import com.sbancuz.plannh.data.SettingDef;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.common.items.ItemFluidDisplay;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.GTNEIDefaultHandler.CachedDefaultRecipe;
import it.unimi.dsi.fastutil.objects.ObjectFloatImmutablePair;

public class GTExtractor implements RecipePropertyExtractor {

    public static final RecipeProperty<Integer> SPECIAL_VALUE = RecipeProperty
        .intProperty("specialValue", "Special Value", 0);

    @Override
    public String getModId() {
        return Compat.GREGTECH.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(SPECIAL_VALUE);

        for (MachineProfile p : PROFILES) {
            MachineProfileRegistry.register(p);
        }

        new BartWorksExtractor().register();
    }

    // ── declarative profile definition ──

    private static final List<MachineProfile> PROFILES = List.of(
        MachineProfile.builder("gregtech:basic", "GT Basic")
            .baseSettings()
            .advancedSettings()
            .effect(new EffectBuilder())
            .build(),
        MachineProfile.builder("gregtech:ebf", "GT EBF")
            .baseSettings()
            .advancedSettings()
            .heatSettings()
            .effect(new EffectBuilder().withHeat())
            .build(),
        MachineProfile.builder("gregtech:laser", "GT Laser")
            .baseSettings()
            .advancedSettings()
            .effect(new EffectBuilder().withLaserOC())
            .build(),
        MachineProfile.builder("gregtech:fusion", "GT Fusion")
            .baseSettings()
            .advancedSettings()
            .effect(new EffectBuilder().withPerfectOC())
            .build(),
        MachineProfile.builder("gregtech:plasmaforge", "GT Plasma Forge")
            .baseSettings()
            .advancedSettings()
            .heatSettings()
            .effect(new EffectBuilder().withHeat())
            .build(),
        MachineProfile.builder("gregtech:cracker", "GT Cracker")
            .baseSettings()
            .advancedSettings()
            .effect(new EffectBuilder())
            .build(),
        MachineProfile.builder("gregtech:lcr", "GT LCR")
            .baseSettings()
            .advancedSettings()
            .effect(new EffectBuilder())
            .build(),
        MachineProfile.builder("gregtech:distillationtower", "GT Distillation Tower")
            .baseSettings()
            .advancedSettings()
            .effect(new EffectBuilder())
            .build(),
        MachineProfile.builder("gregtech:generator", "GT Generator")
            .setting(SettingDef.intDef("fuelEfficiency", "Fuel Eff.%", 100, 1, 1000))
            .setting(SettingDef.intDef("parallels", "Par", 1, 1, 4096))
            .setting(SettingDef.intDef("machines", "Mach", 1, 1, 4096))
            .effect((s, ctx) -> {
                int p = MachineProfile.getInt(s, "parallels", 1);
                int m = MachineProfile.getInt(s, "machines", 1);
                int eff = MachineProfile.getInt(s, "fuelEfficiency", 100);
                int dur = Math.max(1, Math.round(ctx.recipeDuration() * 100.0f / eff));
                return new MachineProfile.EffectResult(dur, ctx.recipeEUt(), p * m);
            })
            .build(),
        MachineProfile.builder("gregtech:fake", "GT Fake (pass-through)")
            .effect((s, ctx) -> new MachineProfile.EffectResult(ctx.recipeDuration(), ctx.recipeEUt(), 1))
            .build(),
        MachineProfile.builder("tectech:eyeofharmony", "Eye of Harmony")
            .baseSettings()
            .advancedSettings()
            .effect(new EffectBuilder().withPerfectOC())
            .build());

    // ── declarative overlay-id → profile matchers ──

    @FunctionalInterface
    private interface ProfileMatcher {

        String match(String overlayId);

        static ProfileMatcher keyword(String profileId, String... keywords) {
            return id -> {
                for (String kw : keywords) {
                    if (id.contains(kw)) return profileId;
                }
                return null;
            };
        }

        static ProfileMatcher exact(String profileId, String... exacts) {
            return id -> {
                for (String ex : exacts) {
                    if (id.equals(ex)) return profileId;
                }
                return null;
            };
        }
    }

    private static final List<ProfileMatcher> PROFILE_MATCHERS = List.of(
        ProfileMatcher.keyword(
            "gregtech:ebf",
            "blastfurnace",
            "vacfurnace",
            "alloyblastsmelter",
            "vacuumfurnace",
            "digester",
            "nanochip"),
        ProfileMatcher.keyword("gregtech:plasmaforge", "plasmaforge", "fog_"),
        ProfileMatcher.keyword("gregtech:fusion", "fusion"),
        ProfileMatcher.keyword("gregtech:laser", "laserengraver", "precise_assembler"),
        ProfileMatcher.keyword("gregtech:cracker", "cracker", "craker"),
        ProfileMatcher.keyword("gregtech:lcr", "largechemicalreactor"),
        ProfileMatcher.keyword("gregtech:distillationtower", "distillationtower"),
        ProfileMatcher.exact("gregtech:generator", "gt.recipe.create-condensate"),
        ProfileMatcher.keyword(
            "gregtech:generator",
            "fuel",
            "generator",
            "turbine",
            "boiler",
            "RTG",
            "rocketengine",
            "htgr",
            "solartower",
            "lftr",
            "condensate"),
        ProfileMatcher.keyword(
            "gregtech:fake",
            "scanner",
            "massfab",
            "fake",
            "assemblyline",
            "research",
            "upgrade",
            "nuke",
            "computer",
            "foundry_module",
            "spaceProject",
            "nanoforge",
            "pcbfactory",
            "purification"),
        ProfileMatcher.keyword("tectech:eyeofharmony", "eyeofharmony"));

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        if (!(handler instanceof GTNEIDefaultHandler gth)) return null;
        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(gth);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return null;
        CachedDefaultRecipe cached = (CachedDefaultRecipe) recipes.get(recipeIndex);
        GTRecipe r = cached.mRecipe;
        if (r == null) return null;

        String id = gth.getOverlayIdentifier();
        if (id == null) return "gregtech:basic";
        for (ProfileMatcher m : PROFILE_MATCHERS) {
            String p = m.match(id);
            if (p != null) return p;
        }
        return "gregtech:basic";
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        if (recipeOwner == null) return false;
        return recipeOwner.startsWith("gt.recipe") || recipeOwner.startsWith("gtpp.recipe")
            || recipeOwner.startsWith("bw.recipe")
            || recipeOwner.startsWith("bw.fuels")
            || recipeOwner.startsWith("gg.recipe")
            || recipeOwner.startsWith("gtnhlanth.recipe")
            || recipeOwner.startsWith("kubatech");
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(FlowchartNode node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof GTNEIDefaultHandler gth)) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(gth);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        CachedDefaultRecipe cached = (CachedDefaultRecipe) recipes.get(recipeIndex);
        GTRecipe r = cached.mRecipe;
        if (r == null) return props;

        int duration = r.mDuration;
        int eut = r.mEUt;

        props.put(RecipePropertyAPI.DURATION_TICKS, duration);
        props.put(RecipePropertyAPI.EU_PER_TICK, (long) eut);
        props.put(RecipePropertyAPI.TOTAL_EU, (long) eut * duration);

        if (r.mSpecialValue != 0) {
            props.put(SPECIAL_VALUE, r.mSpecialValue);
        }

        if (r.mInputChances != null) {
            for (int i = 0; i < r.mInputs.length; i++) {
                node.inputs.set(
                    i,
                    new ObjectFloatImmutablePair<>(
                        node.inputs.get(i)
                            .left(),
                        r.mInputChances[i] / 10000.0f));
            }
        }
        if (r.mOutputChances != null) {
            for (int i = 0; i < r.mOutputs.length; i++) {
                node.outputs.set(
                    i,
                    new ObjectFloatImmutablePair<>(
                        node.outputs.get(i)
                            .left(),
                        r.mOutputChances[i] / 10000.0f));
            }
        }

        for (int i = 0; i < r.mFluidInputs.length; i++) {
            node.fluidInputs.add(
                i,
                new ObjectFloatImmutablePair<>(
                    r.mFluidInputs[i],
                    r.mFluidInputChances != null ? r.mFluidInputChances[i] / 10000.0f : 1.f));
        }
        for (int i = 0; i < r.mFluidOutputs.length; i++) {
            node.fluidOutputs.add(
                i,
                new ObjectFloatImmutablePair<>(
                    r.mFluidOutputs[i],
                    r.mFluidOutputChances != null ? r.mFluidOutputChances[i] / 10000.0f : 1.f));
        }

        node.inputs.removeIf(
            p -> p.left()
                .getItem() instanceof ItemFluidDisplay);
        node.outputs.removeIf(
            p -> p.left()
                .getItem() instanceof ItemFluidDisplay);

        return props;
    }

    // ── EffectBuilder ──

    public static class EffectBuilder implements MachineProfile.EffectComputer {

        private boolean forceHeat;
        private boolean forceLaserOC;
        private boolean forcePerfectOC;

        public EffectBuilder withHeat() {
            this.forceHeat = true;
            return this;
        }

        public EffectBuilder withPerfectOC() {
            this.forcePerfectOC = true;
            return this;
        }

        public EffectBuilder withLaserOC() {
            this.forceLaserOC = true;
            return this;
        }

        @Override
        public MachineProfile.EffectResult compute(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
            int parallels = MachineProfile.getInt(s, "parallels", 1);
            int machines = MachineProfile.getInt(s, "machines", 1);

            if (ctx.recipeEUt() <= 0 || ctx.recipeDuration() <= 0
                || MachineProfile.getString(s, "voltage", "OFF")
                    .equals("OFF")) {
                return new MachineProfile.EffectResult(ctx.recipeDuration(), ctx.recipeEUt(), parallels * machines);
            }

            OverclockCalculator calc = buildGtCalc(s, ctx);

            if (forcePerfectOC) calc.enablePerfectOC();
            if (forceLaserOC) calc.setLaserOC(true);

            if (forceHeat) {
                int machineHeat = MachineProfile.getInt(s, "machineHeat", 0);
                if (MachineProfile.getBool(s, "heatOC", true) && machineHeat > 0) {
                    int recipeHeat = MachineProfile.getInt(s, "recipeHeat", 0);
                    calc.setHeatOC(true)
                        .setRecipeHeat(recipeHeat > 0 ? recipeHeat : machineHeat)
                        .setMachineHeat(machineHeat);
                    if (MachineProfile.getBool(s, "heatDiscount", false)) calc.setHeatDiscount(true);
                    int hdMult = MachineProfile.getInt(s, "heatDiscountMult", 100);
                    if (hdMult != 100) calc.setHeatDiscountMultiplier(hdMult / 100.0);
                }
            }

            calc.calculate();
            return new MachineProfile.EffectResult(calc.getDuration(), calc.getConsumption(), parallels * machines);
        }
    }

    // ── GT helpers ──

    private static OverclockCalculator buildGtCalc(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        long voltage = MachineProfile.tierNameToVoltage(MachineProfile.getString(s, "voltage", "OFF"));
        long amp = MachineProfile.getInt(s, "amp", 1);
        int speed = MachineProfile.getInt(s, "speed", 100);
        int parallels = MachineProfile.getInt(s, "parallels", 1);

        OverclockCalculator calc = new OverclockCalculator().setRecipeEUt(ctx.recipeEUt())
            .setEUt(voltage)
            .setDuration(ctx.recipeDuration())
            .setAmperage(amp)
            .setDurationModifier(100.0 / speed)
            .setParallel(parallels)
            .setAmperageOC(true);

        if (MachineProfile.getBool(s, "perfectOC", false)) calc.enablePerfectOC();
        if (MachineProfile.getBool(s, "laserOC", false)) calc.setLaserOC(true);
        if (MachineProfile.getBool(s, "noOverclock", false)) calc.setNoOverclock(true);

        int eutDisc = MachineProfile.getInt(s, "eutDiscount", 0);
        if (eutDisc > 0) calc.setEUtDiscount(eutDisc / 100.0);

        int ocMult = MachineProfile.getInt(s, "eutIncreasePerOC", 400);
        if (ocMult != 400) calc.setEUtIncreasePerOC(ocMult / 100.0);

        int durMult = MachineProfile.getInt(s, "durationDecreasePerOC", 200);
        if (durMult != 200) calc.setDurationDecreasePerOC(durMult / 100.0);

        int maxOc = MachineProfile.getInt(s, "maxOverclocks", 0);
        if (maxOc > 0) calc.setMaxOverclocks(maxOc);

        int maxReg = MachineProfile.getInt(s, "maxRegularOc", 0);
        if (maxReg > 0) calc.setMaxRegularOverclocks(maxReg);

        int skips = MachineProfile.getInt(s, "maxTierSkips", 0);
        if (skips > 0) calc.setMaxTierSkips(skips);

        if (MachineProfile.getBool(s, "unlimitedSkips", false)) calc.setUnlimitedTierSkips();

        return calc;
    }

}
