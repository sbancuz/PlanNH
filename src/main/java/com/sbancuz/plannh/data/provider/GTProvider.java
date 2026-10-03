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
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.machine.MachineVariants;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.properties.SummaryProperty;
import com.sbancuz.plannh.data.provider.gregtech.GTMachineIndex;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.logic.ModifierKind;
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
     * Recipe this node was extracted from, passed to GT's OverclockDescriber. Several describers discard
     * the template and rebuild from the recipe. Node.properties is rebuilt by refresh() and never
     * serialized, and the balancer aggregates only Number values, so storing it here costs nothing.
     */
    public static final RecipeProperty<GTRecipe> GT_RECIPE = RecipeProperty.<GTRecipe>builder("gt.recipe", null)
        .build();

    /**
     * NEI handler's tab title, which contains the name of the machine the recipe list belongs to. The
     * machine picker defaults to that machine, the one the player had open in NEI.
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
        // reset with the shared registries so the index and the MachineVariants memo keyed off it empty together
        GTMachineIndex.reset();
        MachineVariants.register(GTMachineIndex.SOURCE);
        GTSettings.registerChartMinimums();
    }

    /**
     * Machine picker plus the structure settings the chosen machine reads. Speed, EU discount, overclock
     * factors and heat are derived from the machine and appear as rows only when Advanced is ticked.
     */
    private static void machineDriven(final MachineProfile.Builder b) {
        b.setting(GTSettings.MACHINE_DEF.withVisibility(GTSettings.neverAsARow()));
        b.setting(GTSettings.VOLTAGE_DEF.withVisibility(GTSettings.voltageEditable()));
        b.setting(Settings.MACHINES.def());
        // Nearly every multiblock accepts more than one energy hatch, so amperage is a build choice and its
        // row appears without Advanced.
        b.setting(GTSettings.AMP_DEF.withVisibility(GTSettings.ampEditable()));
        b.setting(GTSettings.PARALLELS_DEF.withVisibility(GTSettings.parallelsEditable()));
        // One row per value a player builds or inserts, visible when the selected machine reads it.
        // GregTech registers its kinds as its machines load, before this profile is built, so adding a kind
        // requires no code here.
        for (final ModifierKind kind : ModifierKind.all()) {
            if (kind == ModifierKind.VOLTAGE || kind.source == ModifierKind.Source.RUNTIME) continue;
            b.setting(
                GTSettings.structureDef(kind)
                    .withVisibility(GTSettings.usesStructure(kind)));
        }
        b.setting(GTSettings.MODE_DEF.withVisibility(GTSettings.usesSetting(Settings.GT_MODE)));
        b.setting(GTSettings.ADVANCED_DEF);
    }

    /** Override rows, under Advanced, for charts tuned by hand. */
    private static void manual(final MachineProfile.Builder b) {
        // Each row reads the machine's value until the user stores one over it. The two overclock caps
        // have no machine counterpart (GT rarely sets them), so they're plain optional limits and an
        // empty value means no cap.
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
        .effect(
            Effects.durationFromHandler()
                .andThen(Effects.machineDriven()))
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
