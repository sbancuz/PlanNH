package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.effect.steps.GTOverclockStep;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.properties.SummaryProperty;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

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

    /** GT5u stores a chance as 1..10000, where 10000 is 100%. */
    private static final float GT_CHANCE_SCALE = 10_000f;

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

    /**
     * The recipe this node was extracted from, kept so GT's own OverclockDescriber can be asked what
     * a machine would do with it - several describers discard the template and rebuild from the
     * recipe. Node.properties is rebuilt by refresh() and never serialized, and the balancer only
     * aggregates Number values, so holding it costs nothing.
     */
    public static final RecipeProperty<GTRecipe> GT_RECIPE = RecipeProperty.<GTRecipe>builder("gt.recipe", null)
        .build();

    /**
     * The NEI handler's own tab title, which names the machine the recipe list belongs to. The
     * machine picker uses it to default to the machine the player was actually looking at.
     */
    public static final RecipeProperty<String> NEI_TITLE = RecipeProperty.<String>builder("gt.nei_title", "")
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

    /**
     * The machine picker plus the structure knobs the chosen machine actually reads. Speed, EU
     * discount, overclock factors and heat are all derived from the machine, and reappear as rows
     * only once the user ticks Advanced.
     */
    private static void machineDriven(final MachineProfile.Builder b) {
        b.setting(GTSettings.MACHINE_DEF.withVisibility(GTSettings.neverAsARow()));
        b.setting(GTSettings.VOLTAGE_DEF.withVisibility(GTSettings.voltageEditable()));
        b.setting(Settings.MACHINES.def());
        // Nearly every multiblock takes more than one energy hatch, so how many amps reach it is a
        // build decision rather than an advanced override.
        b.setting(GTSettings.AMP_DEF.withVisibility(GTSettings.ampEditable()));
        b.setting(
            GTSettings.PARALLELS_DEF.withVisibility(
                GTSettings.parallelsEditable()
                    .and((ctx, s) -> !isEoH(ctx))));
        // Every structure knob is the same row with a different def: offered when the selected machine
        // reads it, absent otherwise. Listing them one by one only invited the two lists to diverge.
        for (final Settings knob : StructureState.KNOBS) {
            b.setting(
                GTSettings.knobDef(knob)
                    .withVisibility(GTSettings.usesKnob(knob)));
        }
        b.setting(
            Settings.CATALYST_ASTRAL_ARRAYS.def()
                .withVisibility((ctx, s) -> isEoH(ctx)));
        b.setting(GTSettings.ADVANCED_DEF);
    }

    /** The pre-picker rows, kept so a hand-tuned chart can still be edited exactly as before. */
    private static void manual(final MachineProfile.Builder b) {
        // Each row reads the machine's own value until the user stores one over it. The two
        // overclock caps have no machine counterpart - GT rarely sets them - so they stay plain
        // optional limits where nothing stored means no cap.
        for (final SettingDef<?> def : List.of(
            GTSettings.SPEED_DEF,
            GTSettings.PERFECT_OC_DEF,
            Settings.LASER_OC.def(),
            Settings.NO_OVERCLOCK.def(),
            GTSettings.UNLIMITED_SKIPS_DEF,
            GTSettings.EUT_DISCOUNT_DEF,
            GTSettings.EUT_PER_OC_DEF,
            GTSettings.DURATION_PER_OC_DEF,
            Settings.MAX_OVERCLOCKS.def(),
            Settings.MAX_REGULAR_OC.def(),
            GTSettings.MAX_TIER_SKIPS_DEF,
            GTSettings.MACHINE_HEAT_DEF,
            GTSettings.RECIPE_HEAT_DEF,
            GTSettings.HEAT_OC_DEF,
            GTSettings.HEAT_DISCOUNT_DEF,
            GTSettings.HEAT_DISCOUNT_MULT_DEF,
            GTSettings.MULTIBLOCK_DEF)) {
            b.setting(def.withVisibility(GTSettings.advancedOnly()));
        }
    }

    private static final MachineProfile PROFILE = MachineProfile.builder("gregtech:unified", "GT Unified")
        .settings(GTProvider::machineDriven)
        .settings(GTProvider::manual)
        // Per-recipemap overclock defaults are not listed here: GTMachinePresets derives them from
        // the machine class, so a second table keyed on the recipemap would be a rival authority.
        // What stays is the genuinely recipe-driven cases, which no machine can report.
        .effect(
            Effects.durationFromHandler()
                .andThen(
                    GTOverclockStep.create()
                        .machineDriven()
                        .applyIf(GTProvider::hasHeat, GTOverclockStep::withHeat)
                        .applyIf(
                            ctx -> ctx.properties()
                                .containsKey(FUSION_THRESHOLD),
                            GTOverclockStep::withPerfectOC)
                        .route(
                            "gt.recipe.eyeofharmony",
                            step -> step.withCatalyst(
                                (SettingDef<Integer>) Settings.CATALYST_ASTRAL_ARRAYS.def(),
                                v -> (int) Math
                                    .pow(2, (int) Math.floor(Math.log(8.0 * Math.min(v, 8637)) / Math.log(1.7)))))))
        .onLoad(GTSettings::migrateLegacyNode)
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
            // Kept out of gthMap: that variable also gates the fuel-backend and heating-coil
            // branches below, which the furnace maps must not take.
            props.put(RECIPE_MAP, recipeMap);

        } else if (handler instanceof final GTNEIDefaultHandler gth) {
            final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(gth);
            if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

            final CachedDefaultRecipe cached = (CachedDefaultRecipe) recipes.get(recipeIndex);
            r = cached.mRecipe;
            if (r == null) return props;

            gthMap = gth.getRecipeMap();

        } else {
            return props;
        }

        props.put(GT_RECIPE, r);
        props.put(
            NEI_TITLE,
            handler.getRecipeName()
                .trim());
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

        // Reset everything to not ignore burnables
        node.inputs.clear();
        node.outputs.clear();

        for (int i = 0; i < r.mInputs.length; i++) {
            if (r.mInputs[i].stackSize <= 0) continue;
            node.inputs.add(
                new Port<>(
                    RecipePropertyAPI.ITEM,
                    r.mInputs[i],
                    r.mInputChances != null ? r.mInputChances[i] / GT_CHANCE_SCALE : 1.f));
        }
        for (int i = 0; i < r.mOutputs.length; i++) {
            node.outputs.add(
                new Port<>(
                    RecipePropertyAPI.ITEM,
                    r.mOutputs[i],
                    r.mOutputChances != null ? r.mOutputChances[i] / GT_CHANCE_SCALE : 1.f));
        }
        for (int i = 0; i < r.mFluidInputs.length; i++) {
            if (r.mFluidInputs[i].amount <= 0) continue;
            node.inputs.add(
                new Port<>(
                    RecipePropertyAPI.FLUID,
                    r.mFluidInputs[i],
                    r.mFluidInputChances != null ? r.mFluidInputChances[i] / GT_CHANCE_SCALE : 1.f));
        }
        for (int i = 0; i < r.mFluidOutputs.length; i++) {
            node.outputs.add(
                new Port<>(
                    RecipePropertyAPI.FLUID,
                    r.mFluidOutputs[i],
                    r.mFluidOutputChances != null ? r.mFluidOutputChances[i] / GT_CHANCE_SCALE : 1.f));
        }

        node.inputs.removeIf(p -> p.getValue() instanceof ItemStack stack && stack.getItem() instanceof ItemFluidDisplay);
        node.outputs.removeIf(p -> p.getValue() instanceof ItemStack stack && stack.getItem() instanceof ItemFluidDisplay);

        return props;
    }

}
