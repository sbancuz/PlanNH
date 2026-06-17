package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.tileentity.TileEntityFurnace;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.config.ConfigOverrides;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.Settings;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;

public class Node {

    public final UUID id;
    public int x;
    public int y;

    public final List<Port<?>> inputs = new ArrayList<>();
    public final List<Port<?>> outputs = new ArrayList<>();

    public String machineName;
    public int durationTicks = 0;

    public Recipe.RecipeId recipeId;
    public int handlerRecipeIndex;

    public final MachineConfig machineConfig;
    public final Map<RecipeProperty<?>, Object> properties = new HashMap<>();

    public Node(final IRecipeHandler handler, final int recipeIndex, final int x, final int y) {
        this.id = UUID.randomUUID();
        this.x = x;
        this.y = y;

        this.machineName = handler.getRecipeName()
            .trim();
        this.recipeId = Recipe.RecipeId.of(handler, recipeIndex);

        final PropertyProvider ex = RecipePropertyAPI.getExtractor(handler.getOverlayIdentifier());
        assert ex != null;

        final String pid = ex.getProfileId(handler, recipeIndex);
        if (pid != null && !MachineProfileRegistry.defaultId()
            .equals(pid)) {
            this.machineConfig = new MachineConfig(this, MachineProfileRegistry.get(pid));
        } else {
            this.machineConfig = new MachineConfig(this);
        }

        refresh();
    }

    public void refresh() {
        final RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        final IRecipeHandler handler = ref.handler;
        final int recipeIndex = ref.recipeIndex;
        inputs.clear();
        outputs.clear();

        final PropertyProvider ex = RecipePropertyAPI.getExtractor(handler.getOverlayIdentifier());
        assert ex != null;

        final List<PositionedStack> ins = handler.getIngredientStacks(recipeIndex);
        for (final PositionedStack ps : ins) {
            if (ps != null && ps.item != null && ps.item.stackSize > 0) {
                this.inputs.add(new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), 1.f));
            }
        }

        final PositionedStack result = handler.getResultStack(recipeIndex);
        if (result != null && result.item != null) {
            this.outputs.add(new Port<>(RecipePropertyAPI.ITEM, result.item.copy(), 1.f));
        }
        final List<PositionedStack> others = handler.getOtherStacks(recipeIndex);
        for (final PositionedStack ps : others) {
            // Some mods (GT *coff* *coff*) with recipes that output multiple things put the results in otherStacks even
            // if by documentation only fuels and such should go there
            if (ps != null && ps.item != null) {
                if (ConfigOverrides.alwaysShowBurnableSetting) {
                    if (this.machineConfig.getString(Settings.BURNABLE_OVERRIDE.key())
                        .equals("IN")) {
                        this.inputs.add(new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), 1.f));
                    } else if (this.machineConfig.getString(Settings.BURNABLE_OVERRIDE.key())
                        .equals("OUT")) {
                            this.outputs.add(new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), 1.f));
                        }
                } else if (TileEntityFurnace.getItemBurnTime(ps.item) <= 0) {
                    this.outputs.add(new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), 1.f));
                }
            }
        }

        final Map<RecipeProperty<?>, Object> props = ex.extract(this, handler, recipeIndex);
        if (props != null && !props.isEmpty()) {
            for (final var entry : props.entrySet()) {
                this.properties.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        if (this.properties.containsKey(RecipePropertyAPI.DURATION_TICKS)) {
            this.durationTicks = (int) this.properties.get(RecipePropertyAPI.DURATION_TICKS);
        }
    }

    /**
     * To be used only for serialization/deserialization
     */
    public Node(final UUID id, final int x, final int y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.machineConfig = new MachineConfig(this);
    }
}
