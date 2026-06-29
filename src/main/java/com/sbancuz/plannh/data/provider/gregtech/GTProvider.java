package com.sbancuz.plannh.data.provider.gregtech;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.maps.FurnaceBackend;
import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;

import org.jetbrains.annotations.NotNull;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.ProfileMatcher;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.recipe.Sievert;
import gregtech.common.items.ItemFluidDisplay;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.GTNEIDefaultHandler.CachedDefaultRecipe;

public class GTProvider implements PropertyProvider {

    public static final RecipeProperty<Integer> SPECIAL_VALUE = RecipeProperty.<Integer>builder("special_value", 0)
        .build();
    static final RecipeProperty<Integer> GLASS_TIER = RecipeProperty.<Integer>builder("bartworks.glass_tier", 3)
        .build();
    public static final RecipeProperty<Integer> SIEVERT = RecipeProperty.<Integer>builder("bartworks.sievert", 0)
        .build();
    public static final RecipeProperty<Boolean> SIEVERT_EXACT = RecipeProperty
        .<Boolean>builder("bartworks.sievert_exact", false)
        .build();
    public static final RecipeProperty<Integer> MASS = RecipeProperty.<Integer>builder("bartworks.mass", 0)
        .build();

    public static final RecipeProperty<Long> TOTAL_EU = RecipeProperty.<Long>builder("total_eu", 0L)
        .build();
    public static final RecipeProperty<Long> EU_PER_TICK = RecipeProperty.<Long>builder("eu_per_tick", 0L)
        .build();

    @Override
    public void register() {
        // Add all recipes, even the furnace ones
        RecipePropertyAPI.registerExtractor(new FurnaceRecipeHandler().getOverlayIdentifier(), this);
        RecipeMap.ALL_RECIPE_MAPS.keySet()
            .forEach(key -> RecipePropertyAPI.registerExtractor(key, this));

        for (final MachineProfile p : PROFILES) {
            MachineProfileRegistry.register(p);
        }
    }

    public static void base(MachineProfile.Builder b) {
        b.setting(Settings.VOLTAGE.def());
        b.setting(Settings.AMP.def());
        b.setting(Settings.SPEED.def());
        b.setting(Settings.PARALLELS.def());
        b.setting(Settings.MACHINES.def());
        b.setting(Settings.PERFECT_OC.def());
    }

    public static void heat(MachineProfile.Builder b) {
        b.setting(Settings.MACHINE_HEAT.def());
        b.setting(Settings.RECIPE_HEAT.def());
        b.setting(Settings.HEAT_OC.def());
        b.setting(Settings.HEAT_DISCOUNT.def());
        b.setting(Settings.HEAT_DISCOUNT_MULT.def());
    }

    public static void advanced(MachineProfile.Builder b) {
        b.setting(Settings.LASER_OC.def());
        b.setting(Settings.EUT_DISCOUNT.def());
        b.setting(Settings.EUT_INCREASE_PER_OC.def());
        b.setting(Settings.DURATION_DECREASE_PER_OC.def());
        b.setting(Settings.MAX_OVERCLOCKS.def());
        b.setting(Settings.MAX_REGULAR_OC.def());
        b.setting(Settings.MAX_TIER_SKIPS.def());
        b.setting(Settings.UNLIMITED_SKIPS.def());
        b.setting(Settings.NO_OVERCLOCK.def());
    }

    private static final List<MachineProfile> PROFILES = List.of(
        MachineProfile.builder("gregtech:basic", "GT Basic")
            .settings(GTProvider::base)
            .effect(new GTEffect())
            .build(),
        MachineProfile.builder("gregtech:ebf", "GT EBF")
            .settings(GTProvider::base)
            .settings(GTProvider::heat)
            .effect(new GTEffect().withHeat())
            .build(),
        MachineProfile.builder("gregtech:laser", "GT Laser")
            .settings(GTProvider::base)
            .settings(GTProvider::advanced)
            .effect(new GTEffect().withLaserOC())
            .build(),
        MachineProfile.builder("gregtech:fusion", "GT Fusion")
            .settings(GTProvider::base)
            .settings(GTProvider::advanced)
            .effect(new GTEffect().withPerfectOC())
            .build(),
        MachineProfile.builder("gregtech:plasmaforge", "GT Plasma Forge")
            .settings(GTProvider::base)
            .settings(GTProvider::advanced)
            .settings(GTProvider::heat)
            .effect(new GTEffect().withHeat())
            .build(),
        MachineProfile.builder("gregtech:cracker", "GT Cracker")
            .settings(GTProvider::base)
            .settings(GTProvider::advanced)
            .effect(new GTEffect())
            .build(),
        MachineProfile.builder("gregtech:lcr", "GT LCR")
            .settings(GTProvider::base)
            .settings(GTProvider::advanced)
            .effect(new GTEffect())
            .build(),
        MachineProfile.builder("gregtech:distillationtower", "GT Distillation Tower")
            .settings(GTProvider::base)
            .settings(GTProvider::advanced)
            .effect(new GTEffect())
            .build(),
        MachineProfile.builder("gregtech:fake", "GT Fake (pass-through)")
            .effect(new GTEffect())
            .build(),
        MachineProfile.builder("tectech:eyeofharmony", "Eye of Harmony")
            .settings(GTProvider::base)
            .settings(GTProvider::advanced)
            .effect(new GTEffect().withPerfectOC())
            .build());

    // spotless:off
    private static final List<ProfileMatcher> PROFILE_MATCHERS = List.of(
        ProfileMatcher.keyword( "gregtech:ebf",
            "blastfurnace", "vacfurnace", "alloyblastsmelter", "vacuumfurnace", "digester", "nanochip"),
        ProfileMatcher.keyword("gregtech:basic", "furnace", "alloysmelter"),
        ProfileMatcher.keyword("gregtech:plasmaforge", "plasmaforge", "fog_"),
        ProfileMatcher.keyword("gregtech:fusion", "fusion"),
        ProfileMatcher.keyword("gregtech:laser", "laserengraver", "precise_assembler"),
        ProfileMatcher.keyword("gregtech:cracker", "cracker", "craker"),
        ProfileMatcher.keyword("gregtech:lcr", "largechemicalreactor"),
        ProfileMatcher.keyword("gregtech:distillationtower", "distillationtower"),
        ProfileMatcher.exact("gregtech:generator", "gt.recipe.create-condensate"),
        ProfileMatcher.keyword( "gregtech:generator",
            "fuel", "generator", "turbine", "boiler", "RTG", "rocketengine", "htgr", "solartower", "lftr", "condensate"),
        ProfileMatcher.keyword( "gregtech:fake",
            "scanner", "massfab", "fake", "assemblyline", "research", "upgrade", "nuke",
            "computer", "foundry_module", "spaceProject", "nanoforge", "pcbfactory", "purification"),
        ProfileMatcher.keyword("tectech:eyeofharmony", "eyeofharmony"));
    // spotless:on

    @Override
    @Nullable
    public String getProfileId(final @NotNull IRecipeHandler handler, final int recipeIndex) {
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
    public Map<RecipeProperty<?>, Object> extract(final @NotNull Node node, final @NotNull IRecipeHandler handler, final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        GTRecipe r;

        if (handler instanceof final FurnaceRecipeHandler fh) {
            final List<TemplateRecipeHandler.CachedRecipe> fRecipes = RecipeHandlerAccess.getArecipes(fh);
            props.put(RecipePropertyAPI.DURATION_TICKS, 200);

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
            r = RecipeMaps.furnaceRecipes.findRecipeQuery()
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
            if (r == null) return props;

        } else {
            return props;
        }

        props.put(RecipePropertyAPI.DURATION_TICKS, r.mDuration);
        props.put(EU_PER_TICK, (long) r.mEUt);
        props.put(TOTAL_EU, (long) r.mEUt * r.mDuration);

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

        // Reset everything to not ignore burnables
        node.inputs.clear();
        node.outputs.clear();

        for (int i = 0; i < r.mInputs.length; i++) {
            if (r.mInputs[i].stackSize <= 0) continue;
            node.inputs.add(
                new Port<>(
                    RecipePropertyAPI.ITEM,
                    r.mInputs[i],
                    r.mInputChances != null ? r.mInputChances[i] / 100.0f : 1.f));
        }
        for (int i = 0; i < r.mOutputs.length; i++) {
            node.outputs.add(
                new Port<>(
                    RecipePropertyAPI.ITEM,
                    r.mOutputs[i],
                    r.mOutputChances != null ? r.mOutputChances[i] / 100.0f : 1.f));
        }
        for (int i = 0; i < r.mFluidInputs.length; i++) {
            if (r.mFluidInputs[i].amount <= 0) continue;
            node.inputs.add(
                new Port<>(
                    RecipePropertyAPI.FLUID,
                    r.mFluidInputs[i],
                    r.mFluidInputChances != null ? r.mFluidInputChances[i] / 100.0f : 1.f));
        }
        for (int i = 0; i < r.mFluidOutputs.length; i++) {
            node.outputs.add(
                new Port<>(
                    RecipePropertyAPI.FLUID,
                    r.mFluidOutputs[i],
                    r.mFluidOutputChances != null ? r.mFluidOutputChances[i] / 100.0f : 1.f));
        }

        node.inputs.removeIf(p -> p.getValue() instanceof ItemStack stack && stack.getItem() instanceof ItemFluidDisplay);
        node.outputs.removeIf(p -> p.getValue() instanceof ItemStack stack && stack.getItem() instanceof ItemFluidDisplay);

        return props;
    }

}
