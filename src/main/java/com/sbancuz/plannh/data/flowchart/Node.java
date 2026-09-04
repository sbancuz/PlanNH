package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.provider.DefaultProvider;
import com.sbancuz.plannh.mixins.PositionedStackAccessor;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import gregtech.api.util.GTUtility;
import lombok.Getter;
import lombok.Setter;

@Getter
public class Node extends GraphData {

    // cant be final because of transient deserialization resulting in null
    private transient List<Port<?>> inputs;
    private transient List<Port<?>> outputs;
    private final Map<Integer, ItemStack> inputConfigurations = new HashMap<>();

    // needed for coloring to be machine specific
    private final String machineName;
    private final Recipe.RecipeId recipeId;

    private final MachineConfig machineConfig;
    private transient Map<RecipeProperty<?>, Object> properties;

    @Setter
    private boolean machineCountFixed;

    /**
     * Target production rates by output port index, in ingredient units per second. A target is
     * a pin: AUTO holds the machine's extent so the targeted output hits the rate exactly, and
     * the rest of the chart follows. With several targets on one machine the largest implied
     * extent wins - parallel outputs share one extent, so only the tightest can be exact.
     */
    private final Map<Integer, Double> targetOutputRates = new LinkedHashMap<>();

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

        availableExtractors = RecipePropertyAPI.getExtractors(handler.getClass());
        extractorIndex = 0;
        if (availableExtractors.isEmpty()) {
            extractor = DefaultProvider.INSTANCE;
        } else {
            extractor = pickBestExtractor(handler, recipeIndex);
        }

        String pid = extractor.getProfileId(handler, recipeIndex);
        if (pid != null && !MachineProfileRegistry.defaultId()
            .equals(pid)) {
            machineConfig = new MachineConfig(MachineProfileRegistry.get(pid));
        } else {
            machineConfig = new MachineConfig();
        }

        refresh(false);
    }

    private void refresh(boolean init) {
        if (extractor == null) return;
        RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        IRecipeHandler handler = ref.handler;
        int recipeIndex = ref.recipeIndex;

        if (inputs == null) inputs = new ArrayList<>();
        inputs.clear();
        if (outputs == null) outputs = new ArrayList<>();
        outputs.clear();
        if (properties == null) properties = new HashMap<>();
        properties.clear();
        if (!init) inputConfigurations.clear();

        Map<RecipeProperty<?>, Object> props = extractor.extract(this, handler, recipeIndex);
        if (props != null && !props.isEmpty()) properties.putAll(props);

        deduplicate(inputs);
        deduplicate(outputs);
        machineConfig.seedRouteDefaults(properties); // todo test if this works

        if (init) {
            // sanitise input configs
            inputConfigurations.keySet()
                .removeIf(
                    index -> inputs.get(index)
                        .getAllStacks()
                        .getFirst().items.length <= 1);
            inputConfigurations.forEach(this::setConfiguration);
        }
    }

    @SuppressWarnings("unchecked")
    private void setConfiguration(int index, ItemStack itemStack) {
        Port<?> port = inputs.get(index);
        RecipeProperty<?> type = port.getType();
        if (type == RecipePropertyAPI.ITEM) ((Port<ItemStack>) port).setValue(itemStack.copy());

        if (type == RecipePropertyAPI.FLUID && Compat.GREGTECH.isLoaded) ((Port<FluidStack>) port).setValue(
            GTUtility.getFluidFromDisplayStack(itemStack)
                .copy());

        port.getAllStacks()
            .forEach(ps -> {
                PositionedStackAccessor psa = (PositionedStackAccessor) ps;
                psa.setPermutated(true);
                ps.setPermutationToRender(itemStack);
                psa.setPermutated(false);
            });
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

        refresh(false);
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
        availableExtractors = RecipePropertyAPI.getExtractors(ref.handler.getClass());
        if (extractorIndex >= availableExtractors.size()) {
            extractorIndex = 0;
        }
        extractor = availableExtractors.isEmpty() ? DefaultProvider.INSTANCE : availableExtractors.get(extractorIndex);

        refresh(true);
    }

    @Override
    public String getType() {
        return "node";
    }

    @Override
    public boolean invalid() {
        return super.invalid() || machineName == null
            || inputConfigurations == null
            || recipeId == null
            || machineConfig == null
            || targetOutputRates == null;
    }
}
