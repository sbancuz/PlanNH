package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
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

public class Node implements FlowData {

    public final UUID id;
    public int x;
    public int y;

    @Getter
    public final List<Port<?>> inputs = new ArrayList<>();
    @Getter
    public final List<Port<?>> outputs = new ArrayList<>();

    public String machineName;

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

        this.availableExtractors = RecipePropertyAPI.getExtractors(handler.getClass());
        this.extractorIndex = 0;
        if (availableExtractors.isEmpty()) {
            this.extractor = DefaultProvider.INSTANCE;
        } else {
            this.extractor = pickBestExtractor(handler, recipeIndex);
        }

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

    @Override
    public float secondsPerCycle() {
        final var eff = machineConfig.computeEffect(properties);
        return (float) eff.durationTicks() / 20f;
    }

    @Override
    public Map<Integer, Float> effectiveOutputs(final Balancer.BalanceResult balance) {
        final Balancer.NodeBalance nb = balance.nodeBalances()
            .get(id);
        return nb == null ? Map.of() : nb.effectiveOutputs;
    }

    @Override
    public Map<Integer, Float> effectiveInputs(final Balancer.BalanceResult balance) {
        final Balancer.NodeBalance nb = balance.nodeBalances()
            .get(id);
        return nb == null ? Map.of() : nb.effectiveInputs;
    }

    private PropertyProvider pickBestExtractor(final IRecipeHandler handler, final int recipeIndex) {
        for (final PropertyProvider p : availableExtractors) {
            if (p.canCraft(handler, recipeIndex)) return p;
        }
        return availableExtractors.getFirst();
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
        this.availableExtractors = RecipePropertyAPI.getExtractors(ref.handler.getClass());
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
