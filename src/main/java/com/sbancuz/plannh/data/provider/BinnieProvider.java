package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.effect.steps.CoFHCompat;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;

import binnie.extratrees.machines.lumbermill.Lumbermill;
import binnie.extratrees.nei.NEIHandlerLumbermill;
import binnie.genetics.api.IIncubatorRecipe;
import binnie.genetics.genetics.Engineering;
import binnie.genetics.machine.analyser.Analyser;
import binnie.genetics.machine.genepool.Genepool;
import binnie.genetics.machine.incubator.Incubator;
import binnie.genetics.machine.inoculator.Inoculator;
import binnie.genetics.machine.isolator.Isolator;
import binnie.genetics.machine.polymeriser.Polymeriser;
import binnie.genetics.machine.splicer.Splicer;
import binnie.genetics.nei.AcclimatiserRecipeHandler;
import binnie.genetics.nei.AnalyserRecipeHandler;
import binnie.genetics.nei.DatabaseRecipeHandler;
import binnie.genetics.nei.GenepoolRecipeHandler;
import binnie.genetics.nei.IncubatorRecipeHandler;
import binnie.genetics.nei.InoculatorRecipeHandler;
import binnie.genetics.nei.IsolatorRecipeHandler;
import binnie.genetics.nei.PolymeriserRecipeHandler;
import binnie.genetics.nei.SequencerRecipeHandler;
import binnie.genetics.nei.SplicerRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

public final class BinnieProvider implements PropertyProvider {

    private static final String PROFILE_ID = "binnie:general";

    // TODO: Make a PR to expose these values
    // Sequencer: base process length (ticks) from SequencerComponentLogic.getProcessLength()
    private static final int SEQUENCER_BASE_LENGTH = 19200;
    // Sequencer: RF/t from SequencerComponentLogic.getProcessEnergy()
    private static final int SEQUENCER_RF_PER_TICK = 20;
    // Sequencer: getSequenceStrength() = 1.0 - (1.0 - (damage%6)/5.0)^2 * 0.75
    private static final float SEQUENCER_DAMAGE_MOD = 6.0f;
    private static final float SEQUENCER_STRENGTH_DIVISOR = 5.0f;
    private static final float SEQUENCER_STRENGTH_FACTOR = 0.75f;

    // Polymeriser: catalyst multiplier when gold present (getCatalyst())
    private static final float POLYMERISER_GOLD_FACTOR = 0.2f;

    // Splicer: gene scaling factor in getProcessLength/getProcessEnergy
    private static final float SPLICER_GENE_FACTOR = 0.5f;

    // Acclimatiser: estimated total RF (indefinite process, no RF_COST constant in mod)
    private static final int ACCLIMATISER_ESTIMATED_RF = 200;

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(GenepoolRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(IsolatorRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(SequencerRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(PolymeriserRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(InoculatorRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(AnalyserRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(IncubatorRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(AcclimatiserRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(SplicerRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(DatabaseRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(NEIHandlerLumbermill.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder(PROFILE_ID, "Binnie")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.durationFromHandler()
                        .amortizeCost(CoFHCompat.RF_COST)
                        .applyParallelism())
                .build());
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return getProfileId(handler, recipeIndex) != null;
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return switch (handler) {
            case GenepoolRecipeHandler _, IsolatorRecipeHandler _, SequencerRecipeHandler _,
                 PolymeriserRecipeHandler _, InoculatorRecipeHandler _, AnalyserRecipeHandler _,
                 IncubatorRecipeHandler _, AcclimatiserRecipeHandler _, SplicerRecipeHandler _,
                 DatabaseRecipeHandler _, NEIHandlerLumbermill _ -> PROFILE_ID;
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));

        switch (handler) {
            case GenepoolRecipeHandler _ -> {
                props.put(RecipePropertyAPI.DURATION_TICKS, Genepool.TIME_PERIOD);
                props.put(CoFHCompat.RF_COST, (long) Genepool.RF_COST);
            }
            case IsolatorRecipeHandler _ -> {
                props.put(RecipePropertyAPI.DURATION_TICKS, Isolator.TIME_PERIOD);
                props.put(CoFHCompat.RF_COST, (long) Isolator.RF_COST);
            }
            case AnalyserRecipeHandler _ -> {
                props.put(RecipePropertyAPI.DURATION_TICKS, Analyser.TIME_PERIOD);
                props.put(CoFHCompat.RF_COST, (long) Analyser.RF_COST);
            }
            case NEIHandlerLumbermill _ -> {
                props.put(RecipePropertyAPI.DURATION_TICKS, Lumbermill.TIME_PERIOD);
                props.put(CoFHCompat.RF_COST, (long) Lumbermill.RF_COST);
            }
            case SequencerRecipeHandler r ->
                extractSequencer(props, r, recipeIndex);
            case PolymeriserRecipeHandler r ->
                extractPolymeriser(props, r, recipeIndex);
            case InoculatorRecipeHandler r ->
                extractInoculator(props, r, recipeIndex);
            case SplicerRecipeHandler r ->
                extractSplicer(props, r, recipeIndex);
            case IncubatorRecipeHandler r ->
                extractIncubator(props, r, recipeIndex);
            case AcclimatiserRecipeHandler _ ->
                props.put(CoFHCompat.RF_COST, (long) ACCLIMATISER_ESTIMATED_RF);
            default -> {}
        }

        return props;
    }

    private static void extractSequencer(final Map<RecipeProperty<?>, Object> props,
        final TemplateRecipeHandler handler, final int recipeIndex) {
        final var recipes = RecipeHandlerAccess.getArecipes(handler);
        if (recipeIndex >= recipes.size()) return;
        final var cached = (SequencerRecipeHandler.CachedSequencerRecipe) recipes.get(recipeIndex);
        if (cached.seq == null || cached.seq.item == null) return;
        final float temp = 1.0f
            - (cached.seq.item.getItemDamage() % (int) SEQUENCER_DAMAGE_MOD) / SEQUENCER_STRENGTH_DIVISOR;
        final float strength = 1.0f - temp * temp * SEQUENCER_STRENGTH_FACTOR;
        final int ticks = Math.max(1, (int) (SEQUENCER_BASE_LENGTH * strength));
        props.put(RecipePropertyAPI.DURATION_TICKS, ticks);
        props.put(CoFHCompat.RF_COST, (long) ticks * SEQUENCER_RF_PER_TICK);
    }

    private static void extractPolymeriser(final Map<RecipeProperty<?>, Object> props,
        final TemplateRecipeHandler handler, final int recipeIndex) {
        final var recipes = RecipeHandlerAccess.getArecipes(handler);
        if (recipeIndex >= recipes.size()) return;
        final var cached = (PolymeriserRecipeHandler.CachedPolymeriserRecipe) recipes.get(recipeIndex);
        if (cached.input == null || cached.input.item == null) return;
        final int geneCount = Engineering.getGenes(cached.input.item).length;
        final boolean hasGold = cached.goldNugget != null && cached.goldNugget.item != null;
        final double factor = Math.max(1, geneCount) * (hasGold ? POLYMERISER_GOLD_FACTOR : 1.0);
        props.put(RecipePropertyAPI.DURATION_TICKS, Math.max(1, (int) (Polymeriser.TIME_PERIOD * factor)));
        props.put(CoFHCompat.RF_COST, (long) Math.max(1, (int) (Polymeriser.RF_COST * factor)));
    }

    private static void extractInoculator(final Map<RecipeProperty<?>, Object> props,
        final TemplateRecipeHandler handler, final int recipeIndex) {
        final var recipes = RecipeHandlerAccess.getArecipes(handler);
        if (recipeIndex >= recipes.size()) return;
        final var cached = (InoculatorRecipeHandler.CachedInoculatorRecipe) recipes.get(recipeIndex);
        final int factor = Math.max(
            1,
            cached.serum != null && cached.serum.item != null ? Engineering.getGenes(cached.serum.item).length : 1);
        props.put(RecipePropertyAPI.DURATION_TICKS, Math.max(1, Inoculator.TIME_PERIOD * factor));
        props.put(CoFHCompat.RF_COST, (long) Math.max(1, Inoculator.RF_COST * factor));
    }

    private static void extractSplicer(final Map<RecipeProperty<?>, Object> props, final TemplateRecipeHandler handler,
        final int recipeIndex) {
        final var recipes = RecipeHandlerAccess.getArecipes(handler);
        if (recipeIndex >= recipes.size()) return;
        final var cached = (SplicerRecipeHandler.CachedSplicer) recipes.get(recipeIndex);
        final double factor = 1.0 + (Math.max(
            1,
            cached.serum != null && cached.serum.item != null ? Engineering.getGenes(cached.serum.item).length : 1) - 1)
            * SPLICER_GENE_FACTOR;
        props.put(RecipePropertyAPI.DURATION_TICKS, Math.max(1, (int) (Splicer.TIME_PERIOD * factor)));
        props.put(CoFHCompat.RF_COST, (long) Math.max(1, (int) (Splicer.RF_COST * factor)));
    }

    private static void extractIncubator(final Map<RecipeProperty<?>, Object> props,
        final TemplateRecipeHandler handler, final int recipeIndex) {
        final var recipes = RecipeHandlerAccess.getArecipes(handler);
        if (recipeIndex >= recipes.size()) return;
        final var cached = (IncubatorRecipeHandler.CachedIncubatorRecipe) recipes.get(recipeIndex);

        float tickChance = 1.0f;
        if (cached.input != null && cached.input.item != null) {
            for (final IIncubatorRecipe recipe : Incubator.RECIPES) {
                final ItemStack recipeInput = recipe.getInputStack();
                if (recipeInput != null && recipeInput.isItemEqual(cached.input.item)) {
                    tickChance = recipe.getChance();
                    break;
                }
            }
        }
        if (tickChance <= 0) tickChance = 1.0f;
        final int ticks = Math.max(1, (int) (1.0f / tickChance));
        props.put(RecipePropertyAPI.DURATION_TICKS, ticks);
        props.put(CoFHCompat.RF_COST, (long) (Incubator.ENERGY_PER_TICK / tickChance));
    }
}
