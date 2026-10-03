package com.sbancuz.plannh;

import static org.mockito.Mockito.mock;

import com.sbancuz.plannh.data.provider.gregtech.GTMachineSpec;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.logic.ProcessingSpec;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/** Test machines built from a spec, as the GregTech registry would supply them. */
final class GTSpecs {

    private GTSpecs() {}

    static GTMachineSpec machine(final ProcessingSpec spec) {
        return GTMachineSpec.of(spec);
    }

    /** GTRecipe's constructor requires a loaded game. The calculator reads only these fields. */
    static GTRecipe recipe(final long eut, final int duration, final int heat) {
        final GTRecipe recipe = mock(GTRecipe.class);
        recipe.mEUt = (int) eut;
        recipe.mDuration = duration;
        recipe.mSpecialValue = heat;
        return recipe;
    }

    /** Calculator for this machine on a chart node, before overrides, parallels and calculation. */
    static OverclockCalculator calculator(final ProcessingSpec spec, final StructureState state, final long recipeEUt,
        final int duration, final int recipeHeat) {
        return machine(spec).resolve(recipe(recipeEUt, duration, recipeHeat), state)
            .toCalculator();
    }
}
