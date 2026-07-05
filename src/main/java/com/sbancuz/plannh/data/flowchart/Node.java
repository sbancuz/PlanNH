package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.tileentity.TileEntityFurnace;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.nei.NEIPlanConfig;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import lombok.Getter;
import lombok.Setter;

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

    @Getter
    private transient PropertyProvider extractor;
    @Getter
    private transient List<PropertyProvider> availableExtractors = List.of();
    @Getter
    @Setter
    private int extractorIndex;

    public Node(final IRecipeHandler handler, final int recipeIndex, final int x, final int y) {
        this.id = UUID.randomUUID();
        this.x = x;
        this.y = y;

        this.machineName = handler.getRecipeName()
            .trim();
        this.recipeId = Recipe.RecipeId.of(handler, recipeIndex);
        this.handlerRecipeIndex = recipeIndex;

        this.availableExtractors = RecipePropertyAPI.getExtractors(handler.getOverlayIdentifier());
        if (availableExtractors.isEmpty()) {
            this.extractor = null;
            this.machineConfig = new MachineConfig(this);
            return;
        }
        this.extractorIndex = 0;
        this.extractor = pickBestExtractor(handler, recipeIndex);

        final String pid = this.extractor.getProfileId(handler, recipeIndex);
        if (pid != null && !MachineProfileRegistry.defaultId()
            .equals(pid)) {
            this.machineConfig = new MachineConfig(this, MachineProfileRegistry.get(pid));
        } else {
            this.machineConfig = new MachineConfig(this);
        }

        refresh();
    }

    public void refresh() {
        if (extractor == null) return;
        final RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        final IRecipeHandler handler = ref.handler;
        final int recipeIndex = ref.recipeIndex;
        inputs.clear();
        outputs.clear();
        properties.clear();

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
            if (ps != null && ps.item != null) {
                if (NEIClientConfig.getSetting(NEIPlanConfig.ConfigBurnableOverride.KEY)
                    .getIntValue(NEIPlanConfig.ConfigBurnableOverride.OFF) == NEIPlanConfig.ConfigBurnableOverride.ON) {
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

        final Map<RecipeProperty<?>, Object> props = extractor.extract(this, handler, recipeIndex);
        if (props != null && !props.isEmpty()) {
            for (final var entry : props.entrySet()) {
                this.properties.put(entry.getKey(), entry.getValue());
            }
        }

        if (this.properties.containsKey(RecipePropertyAPI.DURATION_TICKS)) {
            this.durationTicks = (int) this.properties.get(RecipePropertyAPI.DURATION_TICKS);
        }

        deduplicate(inputs);
        deduplicate(outputs);
    }

    private static void deduplicate(final List<Port<?>> ports) {
        final List<Port<?>> aggregate = new ArrayList<>(ports);
        for (int i = 0; i < aggregate.size(); i++) {
            for (int j = i + 1; j < aggregate.size(); j++) { // 1. Start at i + 1 to avoid self-merging
                if (aggregate.get(i)
                    .canConnect(aggregate.get(j))) {
                    aggregate.get(i)
                        .merge(aggregate.get(j));
                    aggregate.remove(j);
                    j--;
                }
            }
        }

        ports.clear();
        ports.addAll(aggregate);
    }

    public void switchExtractor() {
        if (availableExtractors.size() < 2) return;
        extractorIndex = (extractorIndex + 1) % availableExtractors.size();
        this.extractor = availableExtractors.get(extractorIndex);

        final RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        if (ref != null) {
            final String pid = extractor.getProfileId(ref.handler, ref.recipeIndex);
            if (pid != null && !MachineProfileRegistry.defaultId()
                .equals(pid)) {
                this.machineConfig.profileId = pid;
            } else {
                this.machineConfig.profileId = MachineProfileRegistry.defaultId();
            }
        }

        refresh();
    }

    private PropertyProvider pickBestExtractor(final IRecipeHandler handler, final int recipeIndex) {
        for (final PropertyProvider p : availableExtractors) {
            if (p.canCraft(handler, recipeIndex)) return p;
        }
        return availableExtractors.getFirst();
    }

    public void initExtractor() {
        final RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        if (ref == null) {
            this.extractor = null;
            this.availableExtractors = List.of();
            return;
        }
        this.availableExtractors = RecipePropertyAPI.getExtractors(ref.handler.getOverlayIdentifier());
        if (this.extractorIndex >= this.availableExtractors.size()) {
            this.extractorIndex = 0;
        }
        this.extractor = this.availableExtractors.isEmpty() ? null : this.availableExtractors.get(this.extractorIndex);
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
