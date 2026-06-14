package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.FluidPort;
import com.sbancuz.plannh.data.flowchart.ItemPort;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.recipe.Sievert;
import gregtech.common.items.ItemFluidDisplay;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.GTNEIDefaultHandler.CachedDefaultRecipe;

public class GTProvider implements PropertyProvider {

    public static final RecipeProperty<Integer> SPECIAL_VALUE = RecipeProperty
        .intProperty("specialValue", "Special Value", 0);
    static final RecipeProperty<Integer> GLASS_TIER = RecipeProperty
        .intProperty("bartworks.glassTier", "Glass Tier", 3);
    public static final RecipeProperty<Integer> SIEVERT = RecipeProperty.intProperty("bartworks.sievert", "Sievert", 0);
    public static final RecipeProperty<Boolean> SIEVERT_EXACT = RecipeProperty
        .boolProperty("bartworks.sievertExact", "Exact Sievert", false);
    public static final RecipeProperty<Integer> MASS = RecipeProperty.intProperty("bartworks.mass", "Mass", 0);

    @Override
    @Nonnull
    public String getModId() {
        return Compat.GREGTECH.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(SPECIAL_VALUE);
        RecipePropertyAPI.registerProperty(GLASS_TIER);
        RecipePropertyAPI.registerProperty(SIEVERT);
        RecipePropertyAPI.registerProperty(SIEVERT_EXACT);
        RecipePropertyAPI.registerProperty(MASS);

        for (final MachineProfile p : PROFILES) {
            MachineProfileRegistry.register(p);
        }
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
            .setting(Settings.FUEL_EFFICIENCY.def())
            .setting(Settings.PARALLELS.def())
            .setting(Settings.MACHINES.def())
            .effect((s, ctx) -> {
                int p = MachineProfile.getInt(s, Settings.PARALLELS.key(), 1);
                int m = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
                int eff = MachineProfile.getInt(s, Settings.FUEL_EFFICIENCY.key(), 100);
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

        static ProfileMatcher keyword(final String profileId, final String... keywords) {
            return id -> {
                for (final String kw : keywords) {
                    if (id.contains(kw)) return profileId;
                }
                return null;
            };
        }

        static ProfileMatcher exact(final String profileId, final String... exacts) {
            return id -> {
                for (final String ex : exacts) {
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
        ProfileMatcher.keyword("gregtech:basic", "furnace", "alloysmelter"),
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
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (handler instanceof final FurnaceRecipeHandler fh) {
            final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(fh);
            if (recipeIndex < 0 || recipeIndex >= recipes.size()) return null;
            final TemplateRecipeHandler.CachedRecipe recipe = recipes.get(recipeIndex);
            if (recipe == null) return null;
            return "gregtech:basic";
        }
        if (!(handler instanceof final GTNEIDefaultHandler gth)) return null;
        final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(gth);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return null;
        final CachedDefaultRecipe cached = (CachedDefaultRecipe) recipes.get(recipeIndex);
        final GTRecipe r = cached.mRecipe;
        if (r == null) return null;

        final String id = gth.getOverlayIdentifier();
        if (id == null) return "gregtech:basic";
        for (final ProfileMatcher m : PROFILE_MATCHERS) {
            final String p = m.match(id);
            if (p != null) return p;
        }
        return "gregtech:basic";
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler, final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof final GTNEIDefaultHandler gth)) return props;

        final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(gth);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        final CachedDefaultRecipe cached = (CachedDefaultRecipe) recipes.get(recipeIndex);
        final GTRecipe r = cached.mRecipe;
        if (r == null) return props;

        final int duration = r.mDuration;
        final int eut = r.mEUt;

        props.put(RecipePropertyAPI.DURATION_TICKS, duration);
        props.put(RecipePropertyAPI.EU_PER_TICK, (long) eut);
        props.put(RecipePropertyAPI.TOTAL_EU, (long) eut * duration);

        final int glassTier = r.getMetadataOrDefault(GTRecipeConstants.GLASS, 3);
        if (glassTier != 3) {
            props.put(GLASS_TIER, glassTier);
        }

        final Sievert sievert = r.getMetadataOrDefault(GTRecipeConstants.SIEVERT, new Sievert(0, false));
        if (sievert.sievert > 0 || sievert.isExact) {
            props.put(SIEVERT, sievert.sievert);
            if (sievert.isExact) props.put(SIEVERT_EXACT, true);
        }

        final int mass = r.getMetadataOrDefault(GTRecipeConstants.MASS, 0);
        if (mass > 0) {
            props.put(MASS, mass);
        }

        if (r.mSpecialValue != 0) {
            props.put(SPECIAL_VALUE, r.mSpecialValue);
        }

        if (r.mInputChances != null) {
            for (int i = 0; i < r.mInputs.length && i < node.inputs.size(); i++) {
                final Port port = node.inputs.get(i);
                if (port instanceof final ItemPort ip) {
                    ip.setChance(r.mInputChances[i] / 10000.0f);
                }
            }
        }
        if (r.mOutputChances != null) {
            for (int i = 0; i < r.mOutputs.length && i < node.outputs.size(); i++) {
                final Port port = node.outputs.get(i);
                if (port instanceof final ItemPort ip) {
                    ip.setChance(r.mOutputChances[i] / 10000.0f);
                }
            }
        }

        for (int i = 0; i < r.mFluidInputs.length; i++) {
            node.inputs.add(
                new FluidPort(
                    r.mFluidInputs[i],
                    r.mFluidInputChances != null ? r.mFluidInputChances[i] / 10000.0f : 1.f));
        }
        for (int i = 0; i < r.mFluidOutputs.length; i++) {
            node.outputs.add(
                new FluidPort(
                    r.mFluidOutputs[i],
                    r.mFluidOutputChances != null ? r.mFluidOutputChances[i] / 10000.0f : 1.f));
        }

        node.inputs.removeIf(
            p -> p instanceof final ItemPort ip && ip.getStack() != null
                && ip.getStack().getItem() instanceof ItemFluidDisplay);
        node.outputs.removeIf(
            p -> p instanceof final ItemPort ip && ip.getStack() != null
                && ip.getStack().getItem() instanceof ItemFluidDisplay);

        return props;
    }

    // ── EffectBuilder ──

    public static class EffectBuilder implements MachineProfile.EffectComputer {

        private boolean forceHeat;
        private boolean forceLaserOC;
        private boolean forcePerfectOC;

        @Nonnull
        public EffectBuilder withHeat() {
            this.forceHeat = true;
            return this;
        }

        @Nonnull
        public EffectBuilder withPerfectOC() {
            this.forcePerfectOC = true;
            return this;
        }

        @Nonnull
        public EffectBuilder withLaserOC() {
            this.forceLaserOC = true;
            return this;
        }

        @Override
        @Nonnull
        public MachineProfile.EffectResult compute(final Map<String, Object> s,
            final MachineProfile.RecipeContext ctx) {
            final int parallels = MachineProfile.getInt(s, Settings.PARALLELS.key(), 1);
            final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);

            if (ctx.recipeEUt() <= 0 || ctx.recipeDuration() <= 0
                || MachineProfile.getString(s, Settings.VOLTAGE.key(), "OFF")
                    .equals("OFF")) {
                return new MachineProfile.EffectResult(ctx.recipeDuration(), ctx.recipeEUt(), parallels * machines);
            }

            final OverclockCalculator calc = buildGtCalc(s, ctx);

            if (forcePerfectOC) calc.enablePerfectOC();
            if (forceLaserOC) calc.setLaserOC(true);

            if (forceHeat) {
                final int machineHeat = MachineProfile.getInt(s, Settings.MACHINE_HEAT.key(), 0);
                if (MachineProfile.getBool(s, Settings.HEAT_OC.key(), true) && machineHeat > 0) {
                    final int recipeHeat = MachineProfile.getInt(s, Settings.RECIPE_HEAT.key(), 0);
                    calc.setHeatOC(true)
                        .setRecipeHeat(recipeHeat > 0 ? recipeHeat : machineHeat)
                        .setMachineHeat(machineHeat);
                    if (MachineProfile.getBool(s, Settings.HEAT_DISCOUNT.key(), false)) calc.setHeatDiscount(true);
                    final int hdMult = MachineProfile.getInt(s, Settings.HEAT_DISCOUNT_MULT.key(), 100);
                    if (hdMult != 100) calc.setHeatDiscountMultiplier(hdMult / 100.0);
                }
            }

            calc.calculate();
            return new MachineProfile.EffectResult(calc.getDuration(), calc.getConsumption(), parallels * machines);
        }
    }

    // ── GT helpers ──

    @Nonnull
    private static OverclockCalculator buildGtCalc(final Map<String, Object> s,
        final MachineProfile.RecipeContext ctx) {
        final long voltage = MachineProfile
            .tierNameToVoltage(MachineProfile.getString(s, Settings.VOLTAGE.key(), "OFF"));
        final long amp = MachineProfile.getInt(s, Settings.AMP.key(), 1);
        final int speed = MachineProfile.getInt(s, Settings.SPEED.key(), 100);
        final int parallels = MachineProfile.getInt(s, Settings.PARALLELS.key(), 1);

        final OverclockCalculator calc = new OverclockCalculator().setRecipeEUt(ctx.recipeEUt())
            .setEUt(voltage)
            .setDuration(ctx.recipeDuration())
            .setAmperage(amp)
            .setDurationModifier(100.0 / speed)
            .setParallel(parallels)
            .setAmperageOC(true);

        if (MachineProfile.getBool(s, Settings.PERFECT_OC.key(), false)) calc.enablePerfectOC();
        if (MachineProfile.getBool(s, Settings.LASER_OC.key(), false)) calc.setLaserOC(true);
        if (MachineProfile.getBool(s, Settings.NO_OVERCLOCK.key(), false)) calc.setNoOverclock(true);

        final int eutDisc = MachineProfile.getInt(s, Settings.EUT_DISCOUNT.key(), 0);
        if (eutDisc > 0) calc.setEUtDiscount(eutDisc / 100.0);

        final int ocMult = MachineProfile.getInt(s, Settings.EUT_INCREASE_PER_OC.key(), 400);
        if (ocMult != 400) calc.setEUtIncreasePerOC(ocMult / 100.0);

        final int durMult = MachineProfile.getInt(s, Settings.DURATION_DECREASE_PER_OC.key(), 200);
        if (durMult != 200) calc.setDurationDecreasePerOC(durMult / 100.0);

        final int maxOc = MachineProfile.getInt(s, Settings.MAX_OVERCLOCKS.key(), 0);
        if (maxOc > 0) calc.setMaxOverclocks(maxOc);

        final int maxReg = MachineProfile.getInt(s, Settings.MAX_REGULAR_OC.key(), 0);
        if (maxReg > 0) calc.setMaxRegularOverclocks(maxReg);

        final int skips = MachineProfile.getInt(s, Settings.MAX_TIER_SKIPS.key(), 0);
        if (skips > 0) calc.setMaxTierSkips(skips);

        if (MachineProfile.getBool(s, Settings.UNLIMITED_SKIPS.key(), false)) calc.setUnlimitedTierSkips();

        return calc;
    }

}
