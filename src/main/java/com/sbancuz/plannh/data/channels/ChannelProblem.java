package com.sbancuz.plannh.data.channels;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

public record ChannelProblem(@Nonnull List<Recipe> recipes, @Nonnull Conflicts conflicts,
    @Nonnull Comparator<Ingredient.Item> keyOrder) {

    public enum Feed {
        PASSIVE,
        BATCH
    }

    public sealed interface Conflicts permits Batch,Passive {
    }

    public record Batch(@Nonnull List<List<BatchConflict>> byVictim) implements Conflicts {}

    public record Passive(@Nonnull List<PassiveConflict> all) implements Conflicts {}

    public static ChannelProblem batch(final List<Recipe> recipes, final List<List<BatchConflict>> byVictim,
        final Comparator<Ingredient.Item> keyOrder) {
        return new ChannelProblem(recipes, new Batch(byVictim), keyOrder);
    }

    public static ChannelProblem passive(final List<Recipe> recipes, final List<PassiveConflict> all,
        final Comparator<Ingredient.Item> keyOrder) {
        return new ChannelProblem(recipes, new Passive(all), keyOrder);
    }

    public ChannelProblem {
        switch (conflicts) {
            case Batch(final List<List<BatchConflict>> byVictim) -> {
                if (byVictim.size() != recipes.size())
                    throw new IllegalArgumentException("one conflict list per recipe");
                for (int i = 0; i < byVictim.size(); i++) {
                    for (final BatchConflict c : byVictim.get(i)) {
                        if (recipes.get(i)
                                .catalysts()
                                .containsAll(c.needs())) {
                            throw new IllegalArgumentException("recipe " + i + " conflicts in a channel of its own");
                        }
                    }
                }
            }
            case Passive(final List<PassiveConflict> all) -> {
                for (final PassiveConflict c : all) {
                    for (int i = 0; i < recipes.size(); i++) {
                        if (i != c.owner() && c.metBy(
                                recipes.get(i)
                                        .held())) {
                            throw new IllegalArgumentException(
                                    "recipe " + i + " conflicts in a channel of its own");
                        }
                    }
                }
            }
        }
    }

    public record Recipe(@Nonnull Set<Ingredient.Item> catalysts, @Nonnull Set<Ingredient> inputs) {

        public int fluidInputs() {
            return (int) inputs.stream()
                .filter(i -> i instanceof Ingredient.Fluid)
                .count();
        }

        public boolean needsBus() {
            return !catalysts.isEmpty() || inputs.stream()
                .anyMatch(i -> i instanceof Ingredient.Item);
        }

        public Set<Ingredient> held() {
            final Set<Ingredient> out = new HashSet<>(inputs);
            out.addAll(catalysts);
            return out;
        }
    }

    public record BatchConflict(@Nonnull Set<Ingredient.Item> needs, boolean fluidsOnly, int planRecipe) {}

    public record PassiveConflict(@Nonnull List<Set<Ingredient>> ingredients, int owner) {

        public boolean metBy(final Set<? extends Ingredient> held) {
            return ingredients.stream()
                .allMatch(
                    forms -> forms.stream()
                        .anyMatch(held::contains));
        }
    }

    public Comparator<Set<Ingredient.Item>> catalystOrder() {
        return (a, b) -> {
            if (a.isEmpty() || b.isEmpty()) return Boolean.compare(!a.isEmpty(), !b.isEmpty());
            final Iterator<Ingredient.Item> ia = a.stream()
                .sorted(keyOrder)
                .iterator();
            final Iterator<Ingredient.Item> ib = b.stream()
                .sorted(keyOrder)
                .iterator();
            while (ia.hasNext() && ib.hasNext()) {
                final int c = keyOrder.compare(ia.next(), ib.next());
                if (c != 0) return c;
            }
            return Boolean.compare(ia.hasNext(), ib.hasNext());
        };
    }
}
