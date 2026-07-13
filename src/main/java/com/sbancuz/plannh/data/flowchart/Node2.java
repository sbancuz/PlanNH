package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import lombok.Getter;
import net.minecraft.tileentity.TileEntityFurnace;

@Getter
public class Node2 extends GraphData {

    private final Recipe.RecipeId recipeId;
    // needed for coloring to be machine specific
    private final String machineName;

    // cant be final because of transient deserialization resulting in null
    private transient List<PositionedStack> inputs = new ArrayList<>();
    private transient List<PositionedStack> outputs = new ArrayList<>();

    public Node2(IRecipeHandler handler, int recipeIndex) {
        super(UUID.randomUUID());

        recipeId = Recipe.RecipeId.of(handler, recipeIndex);
        machineName = handler.getRecipeName()
            .trim();
        header = machineName;

        refresh();
    }

    public void refresh(){
        RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        IRecipeHandler handler = ref.handler;
        int recipeIndex = ref.recipeIndex;

        if(inputs == null) inputs = new ArrayList<>();
        else inputs.clear();
        if(outputs == null) outputs = new ArrayList<>();
        else outputs.clear();

        List<PositionedStack> ins = handler.getIngredientStacks(recipeIndex);
        for (PositionedStack ps : ins)
            if (ps != null && ps.item != null && ps.item.stackSize > 0) this.inputs.add(ps);

        PositionedStack result = handler.getResultStack(recipeIndex);
        if (result != null && result.item != null)
            this.outputs.add(result);

        List<PositionedStack> others = handler.getOtherStacks(recipeIndex);
        for (PositionedStack ps : others) {
            if (ps != null && ps.item != null) {
                // TODO
                /*if (NEIClientConfig.getSetting(NEIPlanConfig.ConfigBurnableOverride.KEY)
                    .getIntValue(NEIPlanConfig.ConfigBurnableOverride.OFF) == NEIPlanConfig.ConfigBurnableOverride.ON) {
                    if (this.machineConfig.getString(Settings.BURNABLE_OVERRIDE.key())
                        .equals("IN")) {
                        this.inputs.add(new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), 1.f));
                    } else if (this.machineConfig.getString(Settings.BURNABLE_OVERRIDE.key())
                        .equals("OUT")) {
                        this.outputs.add(new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), 1.f));
                    }
                } else */
                if (TileEntityFurnace.getItemBurnTime(ps.item) <= 0)
                    this.outputs.add(ps);

            }
        }
    }

    @Override
    public String getType() {
        return "node";
    }
}
