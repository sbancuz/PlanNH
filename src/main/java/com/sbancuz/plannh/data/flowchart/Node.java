package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.provider.DefaultProvider;

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

    public Recipe.RecipeId recipeId;
    public int handlerRecipeIndex;

    public final MachineConfig machineConfig;
    public final Map<RecipeProperty<?>, Object> properties = new HashMap<>();

    /**
     * Target production rates by output port index, in ingredient units per second. A target is
     * a pin: AUTO holds the machine's extent so the targeted output hits the rate exactly, and
     * the rest of the chart follows. With several targets on one machine the largest implied
     * extent wins - parallel outputs share one extent, so only the tightest can be exact.
     */
    public final Map<Integer, Double> targetOutputRates = new LinkedHashMap<>();

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

        this.availableExtractors = extractorsFor(handler, recipeIndex);
        this.extractorIndex = 0;
        // First, because extractorsFor has already put the providers that claim this recipe in front.
        this.extractor = availableExtractors.isEmpty() ? DefaultProvider.INSTANCE : availableExtractors.getFirst();

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

        final Map<RecipeProperty<?>, Object> props = extractor.extract(this, handler, recipeIndex);
        if (props != null && !props.isEmpty()) {
            this.properties.putAll(props);
        }

        deduplicate(inputs);
        deduplicate(outputs);
    }

    private static void deduplicate(final List<Port<?>> ports) {
        final List<Port<?>> aggregate = new ArrayList<>(ports);
        for (int i = 0; i < aggregate.size(); i++) {
            for (int j = i + 1; j < aggregate.size(); j++) {
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

    /**
     * The providers offered for this recipe. Extractors register against a NEI handler class, so every
     * GregTech recipe is offered every GregTech provider - including the steam one, which would let a
     * player switch a Large Chemical Reactor onto steam. {@code canCraft} is the provider's own answer
     * about this recipe, so it decides what is offered rather than only which is picked first.
     *
     * <p>
     * A recipe no provider claims keeps the unfiltered list: something has to extract it, and a wrong
     * provider reads better than a node with no properties at all.
     */
    private static List<PropertyProvider> extractorsFor(final IRecipeHandler handler, final int recipeIndex) {
        final List<PropertyProvider> all = RecipePropertyAPI.getExtractors(handler.getClass());
        final List<PropertyProvider> claimed = new ArrayList<>();
        for (final PropertyProvider p : all) {
            if (p.canCraft(handler, recipeIndex)) claimed.add(p);
        }
        return claimed.isEmpty() ? all : List.copyOf(claimed);
    }

    public int getRecipeDuration() {
        return (int) properties.getOrDefault(RecipePropertyAPI.DURATION_TICKS, 0);
    }

    public void initExtractor() {
        final RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
        if (ref == null) {
            this.extractor = null;
            this.availableExtractors = List.of();
            return;
        }
        this.availableExtractors = extractorsFor(ref.handler, ref.recipeIndex);
        if (this.extractorIndex >= this.availableExtractors.size()) {
            this.extractorIndex = 0;
        }
        this.extractor = this.availableExtractors.isEmpty() ? DefaultProvider.INSTANCE
            : this.availableExtractors.get(this.extractorIndex);
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
