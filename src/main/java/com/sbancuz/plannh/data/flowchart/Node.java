package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.setting.Settings;
import com.sbancuz.plannh.nei.NEIPlanConfig;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.tileentity.TileEntityFurnace;

@Getter
public class Node extends GraphData {

    // needed for coloring to be machine specific
    private final String machineName;
    private int durationTicks = 0;

    // cant be final because of transient deserialization resulting in null
    private transient List<Port<?>> inputs = new ArrayList<>();
    private transient List<Port<?>> outputs = new ArrayList<>();
    private transient Map<RecipeProperty<?>, Object> properties = new HashMap<>();

    private final Recipe.RecipeId recipeId;
    private final MachineConfig machineConfig;

    private transient PropertyProvider extractor;
    private transient List<PropertyProvider> availableExtractors;
    @Setter
    private int extractorIndex;

    public Node(IRecipeHandler handler, int recipeIndex) {
        super(UUID.randomUUID());

        recipeId = Recipe.RecipeId.of(handler, recipeIndex);
        machineName = handler.getRecipeName()
            .trim();
        header = machineName;

        availableExtractors = RecipePropertyAPI.getExtractors(handler.getOverlayIdentifier());
        if (availableExtractors.isEmpty()) {
            extractor = null;
            machineConfig = new MachineConfig();
            return;
        }
        extractorIndex = 0;
        extractor = pickBestExtractor(handler, recipeIndex);

        String pid = extractor.getProfileId(handler, recipeIndex);
        if (pid != null && !MachineProfileRegistry.defaultId()
            .equals(pid)) {
            machineConfig = new MachineConfig(MachineProfileRegistry.get(pid));
        } else {
            machineConfig = new MachineConfig();
        }

        refresh();
    }

    private void refresh(){
        if (extractor == null) return;
        RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        IRecipeHandler handler = ref.handler;
        int recipeIndex = ref.recipeIndex;

        if(inputs == null) inputs = new ArrayList<>();
        else inputs.clear();
        if(outputs == null) outputs = new ArrayList<>();
        else outputs.clear();
        if(properties == null) properties = new HashMap<>();
        else properties.clear();

        List<PositionedStack> ins = handler.getIngredientStacks(recipeIndex);
        for (PositionedStack ps : ins)
            if (ps != null && ps.item != null && ps.item.stackSize > 0) inputs.add(Port.itemPort(ps));

        PositionedStack result = handler.getResultStack(recipeIndex);
        if (result != null && result.item != null)
            outputs.add(Port.itemPort(result));

        List<PositionedStack> others = handler.getOtherStacks(recipeIndex);
        for (PositionedStack ps : others) {
            if (ps != null && ps.item != null) {
                if (NEIClientConfig.getSetting(NEIPlanConfig.ConfigBurnableOverride.KEY)
                    .getIntValue(NEIPlanConfig.ConfigBurnableOverride.OFF) == NEIPlanConfig.ConfigBurnableOverride.ON) {
                    if (machineConfig.getString(Settings.BURNABLE_OVERRIDE.key())
                        .equals("IN")) {
                        inputs.add(Port.itemPort(ps));
                    } else if (machineConfig.getString(Settings.BURNABLE_OVERRIDE.key())
                        .equals("OUT")) {
                        outputs.add(Port.itemPort(ps));
                    }
                } else if (TileEntityFurnace.getItemBurnTime(ps.item) <= 0)
                    outputs.add(Port.itemPort(ps));
            }
        }

        Map<RecipeProperty<?>, Object> props = extractor.extract(this, handler, recipeIndex);
        if (props != null && !props.isEmpty()) {
            properties.putAll(props);
        }

        if (properties.containsKey(RecipePropertyAPI.DURATION_TICKS)) {
            durationTicks = (int) properties.get(RecipePropertyAPI.DURATION_TICKS);
        }

        deduplicate(inputs);
        deduplicate(outputs);
    }

    private static void deduplicate(List<Port<?>> ports) {
        List<Port<?>> aggregate = new ArrayList<>(ports);
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

    // TODO add button for this
    public void switchExtractor() {
        if (availableExtractors.size() < 2) return;
        extractorIndex = (extractorIndex + 1) % availableExtractors.size();
        extractor = availableExtractors.get(extractorIndex);

        RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        if (ref != null) {
            String pid = extractor.getProfileId(ref.handler, ref.recipeIndex);
            if (pid != null && !MachineProfileRegistry.defaultId()
                .equals(pid)) {
                machineConfig.setProfileId(pid);
            } else {
                machineConfig.setProfileId(MachineProfileRegistry.defaultId());
            }
        }

        refresh();
    }

    private PropertyProvider pickBestExtractor(IRecipeHandler handler, int recipeIndex) {
        for (PropertyProvider p : availableExtractors) {
            if (p.canCraft(handler, recipeIndex)) return p;
        }
        return availableExtractors.getFirst();
    }

    public void init() {
        RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        if (ref == null) {
            extractor = null;
            availableExtractors = List.of();
            return;
        }
        availableExtractors = RecipePropertyAPI.getExtractors(ref.handler.getOverlayIdentifier());
        if (extractorIndex >= availableExtractors.size()) {
            extractorIndex = 0;
        }
        extractor = availableExtractors.isEmpty() ? null : availableExtractors.get(extractorIndex);

        refresh();
    }

    @Override
    public String getType() {
        return "node";
    }
}
