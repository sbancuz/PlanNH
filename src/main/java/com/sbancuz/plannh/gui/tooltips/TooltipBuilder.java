package com.sbancuz.plannh.gui.tooltips;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.GuiHelper.RateUnit;

/**
 * Writes a tooltip, one level of nesting at a time.
 *
 * <p>
 * Two things this does that a hand-written {@code t.addLine(...)} chain does not. Indentation is
 * state rather than a string baked into a helper, so a group can hold a group; and a group buffers
 * its rows before deciding to print its own header, so a group with nothing in it disappears
 * entirely - rule included - without the caller having to know up front whether there was anything
 * to show.
 *
 * <p>
 * A builder is made fresh for every hover, because {@link RichTooltip} clears and re-runs its
 * builder each time. Nothing here may be cached across hovers: content reaches the model through the
 * suppliers held by the lambdas it is handed, and the widget that owns the tooltip calls {@link
 * Widget#markTooltipDirty()} when any of it has moved - which is
 * what stops a tooltip sitting on screen from going stale, since ModularUI only redraws it when it is
 * told to.
 *
 * <p>
 * Every sentence written here is a lang key, so the pack's wording, word order and punctuation live
 * in the lang file rather than in the calls that print them. What the builder owns is layout - what
 * sits under what, and what colour a value is because of what it is.
 *
 * <p>
 * Not thread safe, and does not need to be - a tooltip is built on the client thread.
 */
public final class TooltipBuilder {

    /** One level. Three spaces, the width ModularUI's own tooltips indent by. */
    private static final String INDENT = "   ";

    /**
     * How deep nesting goes before it stops indenting. A tooltip is one column wide, so depth buys
     * nothing and costs the right-hand edge.
     */
    private static final int MAX_INDENT = 4;

    private static final String COLON = ": ";

    /** Where the finished lines go. Null on a nested builder, which hands them to its parent. */
    private final RichTooltip target;

    private final List<Row> rows;

    private int indent;

    private TooltipBuilder(final RichTooltip target) {
        this.target = target;
        this.rows = new ArrayList<>();
    }

    /**
     * A builder over the tooltip it is about to fill.
     *
     * <p>
     * Takes the {@link RichTooltip} rather than the widget that owns it, because the widget usually
     * cannot be named from where its tooltip is written: it is a field on some other builder, a
     * {@code Flow} of several, or a row rebuilt every solve.
     */
    public static TooltipBuilder create(final RichTooltip tooltip) {
        return new TooltipBuilder(tooltip);
    }

    /** A builder with no tooltip behind it, for a group that has to know what it holds first. */
    private TooltipBuilder nest() {
        return new TooltipBuilder(null);
    }

    // ── structure ───────────────────────────────────────────────────────────

    /** The divider between two groups, as wide as the tooltip turns out to be. */
    public TooltipBuilder separator() {
        rows.add(Row.divider());
        return this;
    }

    /** One step in, up to {@link #MAX_INDENT}. */
    public TooltipBuilder indent() {
        if (indent < MAX_INDENT) indent++;
        return this;
    }

    /** One step back out, never past the left edge. */
    public TooltipBuilder outdent() {
        if (indent > 0) indent--;
        return this;
    }

    /**
     * A group of rows under a header, with a rule above it.
     *
     * <p>
     * The body is written one level in from the header, because that is what a header <i>is</i> -
     * the rows it introduces sit under it - and a caller who has to remember that will eventually
     * forget. Both the rule and the header are skipped when the body turns out to be empty, which is
     * the other reason this exists: the rows are built first and the header follows from what they
     * turned out to be.
     */
    public TooltipBuilder group(final String headerKey, final Consumer<TooltipBuilder> body) {
        final TooltipBuilder nested = nest();
        nested.indent = Math.min(MAX_INDENT, indent + 1);
        body.accept(nested);
        if (nested.rows.isEmpty()) return this;
        separator().header(headerKey);
        rows.addAll(nested.rows);
        return this;
    }

    /**
     * A header in the label colour with a colon, the way the pack has always written one.
     *
     * <p>
     * {@link #group} prints these itself. Calling it directly is for the one thing a group cannot
     * express: a header whose rows belong at the same level as the header rather than under it.
     */
    public TooltipBuilder header(final String langKey) {
        return row(TooltipTheme.key(TooltipTheme.Role.LABEL, StatCollector.translateToLocal(langKey) + COLON));
    }

    // ── rows ────────────────────────────────────────────────────────────────

    /**
     * A row of translated text, resolved now. For the sentences and headings a tooltip is mostly made
     * of.
     *
     * <p>
     * Named apart from {@link #row} because the difference matters: this translates, that does not.
     */
    public TooltipBuilder langRow(final String langKey) {
        return row(IKey.lang(langKey));
    }

    /**
     * A translated row with the values the sentence is about filled in.
     *
     * <p>
     * The sentence, its word order and where its punctuation sits all belong to the lang file, so the
     * arguments here are values rather than a string this side assembled. A call site that wanted to
     * write "1.2k mB/s" itself would be writing prose, and prose is what the lang file is for.
     */
    public TooltipBuilder langRow(final String langKey, final Object... args) {
        return row(IKey.lang(langKey, args));
    }

    /** Translated rows, each on its own. */
    public TooltipBuilder langRows(final String... langKeys) {
        for (final String langKey : langKeys) langRow(langKey);
        return this;
    }

    /** A line of text on its own, from a key already built. A label that is not a translation. */
    public TooltipBuilder row(final IKey key) {
        return line(IKey.comp(pad(), key));
    }

    /** A label and its value: the tooltip's workhorse. */
    public TooltipBuilder entry(final String label, final TooltipTheme.Role role, final String value) {
        return entry(label, role, value, null);
    }

    /**
     * A label and its value, with a tail in the body's colour - a duration's ticks beside its
     * seconds, say. A null tail prints nothing, and the line is the same line without it.
     */
    public TooltipBuilder entry(final String label, final TooltipTheme.Role role, final String value,
        final @Nullable String tail) {
        return entry(IKey.str(label), role, value, tail);
    }

    /** An {@link #entry} whose label is a key, which is the form every lang-backed row reads best in. */
    public TooltipBuilder entry(final IKey label, final TooltipTheme.Role role, final String value) {
        return entry(label, role, value, null);
    }

    /** An {@link #entry} whose value is a key too, for a value a lang sentence carries. */
    public TooltipBuilder entry(final IKey label, final TooltipTheme.Role role, final IKey value) {
        return entry(label, role, value, null);
    }

    /** An {@link #entry} over keys throughout, tail included by way of the string overload. */
    public TooltipBuilder entry(final IKey label, final TooltipTheme.Role role, final IKey value,
        final @Nullable String tail) {
        return entry(label, role, value.get(), tail);
    }

    public TooltipBuilder entry(final IKey label, final TooltipTheme.Role role, final String value,
        final @Nullable String tail) {
        final IKey tailKey = tail == null ? IKey.EMPTY : TooltipTheme.key(TooltipTheme.Role.BODY, " (" + tail + ")");
        return line(
            IKey.comp(
                pad(),
                TooltipTheme.key(
                    TooltipTheme.Role.LABEL,
                    IKey.comp(label, IKey.str(COLON))
                        .get()),
                TooltipTheme.key(role, value),
                tailKey));
    }

    /**
     * An {@link #entry} one further in than this level - for a value that belongs to one particular
     * row of the group rather than to the group itself. A {@link #group} already indents its body, so
     * this is what takes a value to the level below that.
     */
    public TooltipBuilder detail(final String label, final TooltipTheme.Role role, final String value) {
        return indent().entry(label, role, value)
            .outdent();
    }

    /**
     * A bare label with no value: a flag that is on, and only ever on.
     *
     * <p>
     * A null or blank label writes nothing rather than an empty row, so a producer with nothing to
     * say can hand the builder one without the row having to be taken back out again. That is what
     * lets a setting say "off is the absence of a row", and lets the group above it notice it has no
     * rows to print.
     */
    public TooltipBuilder flag(final @Nullable String label) {
        if (label == null || label.isBlank()) return this;
        return row(TooltipTheme.key(TooltipTheme.Role.BODY, label));
    }

    /**
     * A row out of a list where one is in use. The mark keeps its width whether or not it is drawn, so
     * the taken row's label sits where the untaken ones' labels sit.
     */
    public TooltipBuilder marker(final String mark, final String label, final boolean on) {
        final TooltipTheme.Role role = on ? TooltipTheme.Role.ACCENT : TooltipTheme.Role.MUTED;
        return line(IKey.comp(pad(), IKey.str(mark + " "), TooltipTheme.key(role, label)));
    }

    // ── numbers ──────────────────────────────────────────────────────────────

    /**
     * A duration, stated in one unit with its ticks beside it.
     *
     * <p>
     * The ticks are a tail rather than a value: a tick is a rate, not a span of time, so it has no
     * unit to sit in. They are dropped when the unit is already ticks, and when every unit is asked
     * for - the all-units reading ends in them.
     *
     * @param allUnits every duration unit at once rather than the given one
     */
    public TooltipBuilder duration(final String label, final int ticks, final RateUnit unit, final boolean allUnits) {
        return entry(
            label,
            TooltipTheme.Role.IDENTITY,
            durationValue(ticks, unit, allUnits),
            allUnits || !unit.duration ? null : ticks + " " + mark(RateUnit.TICKS));
    }

    /** An amount with its per-unit suffix, the way every rate in the pack is written. */
    public static String rate(final String amount, final RateUnit unit) {
        return amount + StatCollector.translateToLocal(unit.suffixKey());
    }

    /**
     * Formats something like 3 -> x3
     */
    public static IKey multiple(final double factor) {
        return IKey.lang("plannh.gui.number.multiple", GuiHelper.formatCount(factor));
    }

    private static String durationValue(final int ticks, final RateUnit unit, final boolean allUnits) {
        final double seconds = ticks / (double) GuiHelper.TICKS_PER_SECOND;
        if (!allUnits) return number(seconds / unit.secondsPerUnit) + " " + mark(unit);

        final StringBuilder all = new StringBuilder();
        for (final RateUnit each : RateUnit.VALUES) {
            if (!each.duration) continue;
            all.append(number(seconds / each.secondsPerUnit))
                .append(' ')
                .append(mark(each))
                .append(", ");
        }
        return all.append(ticks)
            .append(' ')
            .append(mark(RateUnit.TICKS))
            .toString();
    }

    private static String mark(final RateUnit unit) {
        return StatCollector.translateToLocal(unit.langKey);
    }

    private static String number(final double value) {
        return GuiHelper.trimTrailingZeros(String.format(Locale.ROOT, "%.2f", value));
    }

    // ── output ──────────────────────────────────────────────────────────────

    /**
     * Hands the rows to the tooltip. Called once per build, on the builder {@link #create} made, after
     * the content is written - which is every time, {@link RichTooltip} starting from scratch on a
     * hover.
     */
    public void flush() {
        if (target == null) throw new IllegalStateException("only the root builder writes to a tooltip");
        for (final Row row : rows) row.writeTo(target);
        rows.clear();
    }

    /**
     * Whether anything has been written here yet. A group's emptiness is read through this.
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * What this builder has collected so far, for a test to read.
     *
     * <p>
     * A rule comes back as null: it is drawn, not written, so there is no text to assert on. What this
     * buys is that the shape of a whole tooltip is checkable without a screen - a builder holds keys
     * and strings and nothing that needs a {@code GuiContext}.
     */
    public List<IKey> collected() {
        return rows.stream()
            .map(Row::key)
            .toList();
    }

    private TooltipBuilder line(final IKey key) {
        rows.add(Row.text(key));
        return this;
    }

    private IKey pad() {
        return indent == 0 ? IKey.EMPTY : IKey.str(INDENT.repeat(indent));
    }

    /** A line: either text, or the divider. They cannot be one thing, because a rule is drawn. */
    private record Row(IKey key, TooltipRule rule) {

        static Row text(final IKey key) {
            return new Row(key, null);
        }

        static Row divider() {
            return new Row(null, new TooltipRule());
        }

        void writeTo(final RichTooltip tooltip) {
            if (rule != null) tooltip.addLine(rule);
            else tooltip.addLine(key);
        }
    }
}
