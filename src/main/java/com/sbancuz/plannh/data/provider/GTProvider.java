package com.sbancuz.plannh.data.provider;

import static codechicken.nei.PositionedStack.CHANCE_FULL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.effect.steps.GTOverclockStep;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.properties.SummaryProperty;
import com.sbancuz.plannh.data.setting.IntegerSettingDef;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.maps.FuelBackend;
import gregtech.api.recipe.maps.LargeBoilerFuelBackend;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.recipe.Sievert;
import gregtech.common.items.ItemFluidDisplay;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.GTNEIDefaultHandler.CachedDefaultRecipe;
import gregtech.nei.formatter.HeatingCoilSpecialValueFormatter;

public class GTProvider implements PropertyProvider {

    // Vanilla furnace: base cook time of 200 ticks (10 seconds)
    private static final int FURNACE_COOK_TICKS = 200;

    public static final RecipeProperty<Integer> SPECIAL_VALUE = RecipeProperty.<Integer>builder("gt.special_value", 0)
        .build();
    static final RecipeProperty<Integer> GLASS_TIER = RecipeProperty.<Integer>builder("gt.bartworks.glass_tier", 3)
        .build();
    public static final RecipeProperty<Integer> SIEVERT = RecipeProperty.<Integer>builder("gt.bartworks.sievert", 0)
        .build();
    public static final RecipeProperty<Boolean> SIEVERT_EXACT = RecipeProperty
        .<Boolean>builder("gt.bartworks.sievert_exact", false)
        .build();
    public static final RecipeProperty<Integer> MASS = RecipeProperty.<Integer>builder("gt.bartworks.mass", 0)
        .build();

    public static final RecipeProperty<Long> TOTAL_EU = SummaryProperty.<Long>builder("gt.total_eu", 0L)
        .build();
    public static final RecipeProperty<Long> EU_PER_TICK = SummaryProperty.<Long>builder("gt.eu_per_tick", 0L)
        .perSec(true)
        .build();

    public static final RecipeProperty<Integer> COIL_HEAT = RecipeProperty.<Integer>builder("gt.coil_heat", 0)
        .build();
    public static final RecipeProperty<Long> FUSION_THRESHOLD = RecipeProperty.<Long>builder("gt.fusion_threshold", 0L)
        .build();

    public static final RecipeProperty<RecipeMap<?>> RECIPE_MAP = RecipeProperty
        .<RecipeMap<?>>builder("gt.recipe_map", null)
        .build();

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(FurnaceRecipeHandler.class, this);
        if (Compat.EFR.isLoaded) {
            EFRProvider.registerHandlers(this);
        }

        RecipePropertyAPI.registerExtractor(GTNEIDefaultHandler.class, this);

        MachineProfileRegistry.register(PROFILE);
        new GTSteamProvider().register();
    }

    static BiPredicate<RecipeContext, Map<String, Object>> multiblockOnly() {
        return (ctx, s) -> MachineProfile.getBool(s, Settings.GT_MULTIBLOCK.key(), false);
    }

    private static boolean hasHeat(final RecipeContext ctx) {
        return ctx.properties()
            .containsKey(GLASS_TIER)
            || ctx.properties()
                .containsKey(COIL_HEAT);
    }

    private static boolean isEoH(final RecipeContext ctx) {
        final RecipeMap<?> map = ctx.getOrDefault(RECIPE_MAP, null);
        return map != null && "gt.recipe.eyeofharmony".equals(map.unlocalizedName);
    }

    private static final MachineProfile PROFILE = MachineProfile.builder("gregtech:unified", "GT Unified")
        .setting(Settings.VOLTAGE.def())
        .setting(Settings.AMP.def())
        .setting(Settings.SPEED.def())
        .setting(
            Settings.PARALLELS.def()
                .withVisibility((ctx, s) -> !isEoH(ctx)))
        .setting(Settings.MACHINES.def())
        .setting(Settings.PERFECT_OC.def())
        .setting(Settings.GT_MULTIBLOCK.def())
        .setting(
            Settings.LASER_OC.def()
                .withVisibility(multiblockOnly()))
        .setting(
            Settings.EUT_DISCOUNT.def()
                .withVisibility(multiblockOnly()))
        .setting(
            Settings.EUT_INCREASE_PER_OC.def()
                .withVisibility(multiblockOnly()))
        .setting(
            Settings.DURATION_DECREASE_PER_OC.def()
                .withVisibility(multiblockOnly()))
        .setting(
            Settings.MAX_OVERCLOCKS.def()
                .withVisibility(multiblockOnly()))
        .setting(
            Settings.MAX_REGULAR_OC.def()
                .withVisibility(multiblockOnly()))
        .setting(
            Settings.MAX_TIER_SKIPS.def()
                .withVisibility(multiblockOnly()))
        .setting(
            Settings.UNLIMITED_SKIPS.def()
                .withVisibility(multiblockOnly()))
        .setting(
            Settings.NO_OVERCLOCK.def()
                .withVisibility(multiblockOnly()))
        .setting(
            Settings.HEAT_OC.def()
                .withVisibility((ctx, s) -> hasHeat(ctx)))
        .setting(
            Settings.MACHINE_HEAT.def()
                .withVisibility(multiblockOnly().and((ctx, s) -> hasHeat(ctx))))
        .setting(
            Settings.RECIPE_HEAT.def()
                .withVisibility(multiblockOnly().and((ctx, s) -> hasHeat(ctx))))
        .setting(
            Settings.HEAT_DISCOUNT.def()
                .withVisibility(multiblockOnly().and((ctx, s) -> hasHeat(ctx))))
        .setting(
            Settings.HEAT_DISCOUNT_MULT.def()
                .withVisibility(multiblockOnly().and((ctx, s) -> hasHeat(ctx))))
        .setting(
            Settings.CATALYST_ASTRAL_ARRAYS.def()
                .withVisibility((ctx, s) -> isEoH(ctx)))

        .effect(
            Effects.durationFromHandler()
                .andThen(
                    GTOverclockStep.create()
                        .applyIf(GTProvider::hasHeat, GTOverclockStep::withHeat)
                        .applyIf(
                            ctx -> ctx.properties()
                                .containsKey(FUSION_THRESHOLD),
                            GTOverclockStep::withPerfectOC)
                        .route(
                            "gt.recipe.eyeofharmony",
                            step -> step.withCatalyst(
                                (IntegerSettingDef) Settings.CATALYST_ASTRAL_ARRAYS.def(),
                                v -> (int) Math
                                    .pow(2, (int) Math.floor(Math.log(8.0 * Math.min(v, 8637)) / Math.log(1.7)))))
                        // Perfect OC defaults
                        .withDefault("gt.recipe.largechemicalreactor", Settings.PERFECT_OC.key(), true)
                        .withDefault("gtpp.recipe.flotationcell", Settings.PERFECT_OC.key(), true)
                        .withDefault("gg.recipe.naquadah_fuel_refine_factory", Settings.PERFECT_OC.key(), true)
                        .withDefault("gtpp.recipe.matterfab2", Settings.PERFECT_OC.key(), true)
                        .withDefault("gtpp.recipe.oremill", Settings.PERFECT_OC.key(), true)
                        .withDefault("bw.recipe.cal", Settings.PERFECT_OC.key(), true)
                        .withDefault("gtnhlanth.recipe.digester", Settings.PERFECT_OC.key(), true)
                        .withDefault("gt.recipe.nanoforge", Settings.PERFECT_OC.key(), true)
                        .withDefault("gt.recipe.plasmaforge", Settings.PERFECT_OC.key(), true)
                        // Unlimited tier skip defaults
                        .withDefault("gg.recipe.naquadah_fuel_refine_factory", Settings.UNLIMITED_SKIPS.key(), true)
                        .withDefault("gt.recipe.nanoforge", Settings.UNLIMITED_SKIPS.key(), true)
                        .withDefault("gt.recipe.plasmaforge", Settings.UNLIMITED_SKIPS.key(), true)
                        .withDefault("gtpp.recipe.alloyblastsmelter", Settings.UNLIMITED_SKIPS.key(), true)
                        .withDefault("gt.recipe.transcendentplasmamixerrecipes", Settings.UNLIMITED_SKIPS.key(), true)
                        // No-overclock defaults
                        .withDefault("gt.recipe.transcendentplasmamixerrecipes", Settings.NO_OVERCLOCK.key(), true)
                        .withDefault("gtpp.recipe.algae_pond", Settings.NO_OVERCLOCK.key(), true)))
        .build();

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        if (handler instanceof FurnaceRecipeHandler) return true;
        return handler instanceof GTNEIDefaultHandler;
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (handler instanceof FurnaceRecipeHandler) return PROFILE.id();
        if (handler instanceof GTNEIDefaultHandler) return PROFILE.id();
        return null;
    }

    private static <T> void extractMD(final Map<RecipeProperty<?>, Object> props, final GTRecipe r,
        final RecipeProperty<T> prop, final RecipeMetadataKey<T> key, final T defaultVal) {
        final T val = r.getMetadataOrDefault(key, defaultVal);
        if (!Objects.equals(val, defaultVal)) {
            props.put(prop, val);
        }
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        return extractGTRecipe(node, handler, recipeIndex);
    }

    public static Map<RecipeProperty<?>, Object> extractGTRecipe(final Node node, final IRecipeHandler handler,
                                                                 final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        GTRecipe r;
        RecipeMap<?> gthMap = null;
        List<PositionedStack> inputStacks = new ArrayList<>();
        List<PositionedStack> outputStacks = new ArrayList<>();

        if (handler instanceof final FurnaceRecipeHandler fh) {
            final List<TemplateRecipeHandler.CachedRecipe> fRecipes = RecipeHandlerAccess.getArecipes(fh);
            props.put(RecipePropertyAPI.DURATION_TICKS, FURNACE_COOK_TICKS);

            if (recipeIndex < 0 || recipeIndex >= fRecipes.size()) {
                return props;
            }
            final TemplateRecipeHandler.CachedRecipe cr = fRecipes.get(recipeIndex);
            if (cr == null) {
                return props;
            }
            final List<PositionedStack> ingredients = cr.getIngredients();
            if (ingredients == null || ingredients.isEmpty() || ingredients.getFirst().item == null) {
                return props;
            }

            final String overlay = fh.getOverlayIdentifier();
            final RecipeMap<?> recipeMap;
            if (EFRProvider.SMOKER_OVERLAY.equals(overlay)) {
                recipeMap = RecipeMaps.efrSmokingRecipes;
            } else if (EFRProvider.BLAST_FURNACE_OVERLAY.equals(overlay)) {
                recipeMap = RecipeMaps.efrBlastingRecipes;
            } else {
                recipeMap = RecipeMaps.furnaceRecipes;
            }

            r = recipeMap.findRecipeQuery()
                .items(ingredients.getFirst().item)
                .find();
            if (r == null) {
                return props;
            }

        } else if (handler instanceof final GTNEIDefaultHandler gth) {
            final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(gth);
            if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

            final CachedDefaultRecipe cached = (CachedDefaultRecipe) recipes.get(recipeIndex);
            r = cached.mRecipe;

            inputStacks = cached.mInputs;
            outputStacks = cached.mOutputs;

            if (r == null) return props;

            gthMap = gth.getRecipeMap();

        } else {
            return props;
        }

        props.put(RecipePropertyAPI.DURATION_TICKS, r.mDuration);
        props.put(EU_PER_TICK, (long) r.mEUt);
        props.put(TOTAL_EU, (long) r.mEUt * r.mDuration);

        if (gthMap != null && (gthMap.getBackend() instanceof FuelBackend
            || gthMap.getBackend() instanceof LargeBoilerFuelBackend)) {
            props.put(EU_PER_TICK, 0L);
            props.put(TOTAL_EU, 0L);
        }

        extractMD(props, r, GLASS_TIER, GTRecipeConstants.GLASS, 3);
        extractMD(props, r, COIL_HEAT, GTRecipeConstants.COIL_HEAT, 0);

        if (gthMap != null && !props.containsKey(COIL_HEAT)
            && gthMap.getFrontend().getNEIProperties().neiSpecialInfoFormatter instanceof HeatingCoilSpecialValueFormatter
            && r.mSpecialValue > 0) {
            props.put(COIL_HEAT, r.mSpecialValue);
        }

        if (gthMap != null) {
            props.put(RECIPE_MAP, gthMap);
        }

        extractMD(props, r, FUSION_THRESHOLD, GTRecipeConstants.FUSION_THRESHOLD, 0L);

        final Sievert sievert = r.getMetadataOrDefault(GTRecipeConstants.SIEVERT, new Sievert(0, false));
        if (sievert.sievert > 0 || sievert.isExact) {
            props.put(SIEVERT, sievert.sievert);
            if (sievert.isExact) props.put(SIEVERT_EXACT, true);
        }

        extractMD(props, r, MASS, GTRecipeConstants.MASS, 0);

        if (r.mSpecialValue != 0) {
            props.put(SPECIAL_VALUE, r.mSpecialValue);
        }

        if (!node.getInputs().isEmpty() || !node.getOutputs().isEmpty())
            throw new RuntimeException("inputs or outputs were initialized");  // todo needed?

        List<Port<?>> inputs = node.getInputs();
        List<Port<?>> outputs = node.getOutputs();

        for (PositionedStack ps : inputStacks) {
            if (ps instanceof GTNEIDefaultHandler.FixedPositionedStack fps && fps.isFluid())
                inputs.add(
                    new Port<>(
                        RecipePropertyAPI.FLUID,
                        fps.getFluidAlternatives().getFirst().copy(),
                        (float) fps.getChance() / CHANCE_FULL,
                        ps));
            else if(ps.item != null) inputs.add(Port.itemPort(ps));
        }

        for (PositionedStack ps : outputStacks) {
            if (ps instanceof GTNEIDefaultHandler.FixedPositionedStack fps && fps.isFluid())
                outputs.add(
                    new Port<>(
                        RecipePropertyAPI.FLUID,
                        fps.getFluidAlternatives().getFirst().copy(),
                        (float) fps.getChance() / CHANCE_FULL,
                        ps));
            else outputs.add(Port.itemPort(ps));
        }

        if (node.getInputs().stream().anyMatch(p -> p.getValue() instanceof ItemStack stack && stack.getItem() instanceof ItemFluidDisplay)
         || node.getOutputs().stream().anyMatch(p -> p.getValue() instanceof ItemStack stack && stack.getItem() instanceof ItemFluidDisplay))
            throw new RuntimeException("illegal itemStack found"); // todo needed?

        return props;
    }
}
