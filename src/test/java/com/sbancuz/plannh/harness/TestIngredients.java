package com.sbancuz.plannh.harness;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.properties.ResourceProperty;

import codechicken.nei.PositionedStack;

/**
 * A Minecraft-free ingredient type for headless tests. Corpus charts identify ingredients by
 * name, so tests never need ItemStack/FluidStack (which would require bootstrapping the game
 * registries).
 */
public final class TestIngredients {

    /** Mutable holder so {@link ResourceProperty#setAmount} works like it does for ItemStack. */
    public static final class TestIngredient {

        public final String name;
        public int amount;

        public TestIngredient(final String name, final int amount) {
            this.name = name;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return amount + "x " + name;
        }
    }

    public static final ResourceProperty<TestIngredient> TEST = ResourceProperty
        .builder("test_ingredient", new TestIngredient("", 0))
        .amountExtractor(i -> i.amount)
        .amountUpdater((i, amount) -> i.amount = amount)
        .connectionChecker((a, b) -> a.getValue().name.equals(b.getValue().name))
        // Without this every port reports the resource id, so a headless label reads "void 530/s
        // test_ingredient" and any test of what a player would read asserts nothing.
        .displayFormatter(i -> i.name)
        .hashCodeExtractor(i -> i.name.hashCode())
        .build();

    private TestIngredients() {}

    /**
     * Builds a port carrying quantity {@code perCraft} of the named ingredient. Fractional
     * quantities (gtnh-flow allows e.g. 0.25 dust per craft) are encoded exactly as
     * {@code amount = ceil(q), chance = q / ceil(q)} since the balancer computes effective rates
     * as {@code amount * chance}.
     */
    public static Port<TestIngredient> port(final String name, final double perCraft) {
        final int amount = (int) Math.ceil(perCraft);
        final float chance = (float) (perCraft / amount);
        return new Port<>(
            TEST,
            new TestIngredient(name, amount),
            chance,
            new PositionedStack(new ItemStack(Blocks.dirt), 0, 0));
    }

    public static String nameOf(final Port<?> port) {
        return ((TestIngredient) port.getValue()).name;
    }

    /** Effective per-craft quantity, undoing the amount/chance encoding. */
    public static double quantityOf(final Port<?> port) {
        return port.getAmount() * (double) port.getChance();
    }
}
