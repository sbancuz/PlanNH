package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.Arrays;

import javax.annotation.Nullable;

/**
 * One thing the solver said, as a key and its arguments rather than as a pre-rendered string: the
 * {@link SolverMessage} constant names WHAT was said, the arguments carry the runtime data (machine
 * names, ingredients, rates) it was said about, and {@link #severity()} colours it. This is what
 * travels in the {@code notes} lists instead of a tagged string, so the model never localizes and
 * a reader keys on the message, not on spelled-out English.
 */
public record Note(SolverMessage message, Object... args) {

    public Note(final SolverMessage message, @Nullable final Object... args) {
        this.message = message;
        this.args = args == null ? new Object[0] : args;
    }

    public Severity severity() {
        return message.severity();
    }

    public String key() {
        return message.key();
    }

    /**
     * The localized sentence; needs a live client, so the GUI is the only caller. Nested notes render too.
     */
    public String render() {
        final Object[] rendered = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            rendered[i] = args[i] instanceof final Note note ? note.render() : args[i];
        }
        return message.render(rendered);
    }

    /**
     * The message's key; Minecraft-free, so tests, logs and the profiler key on a stable identity.
     */
    public String describe() {
        return message.describe();
    }

    /**
     * Whether this note, or anything nested in its arguments, speaks {@code target}.
     */
    public boolean containsMessage(final SolverMessage target) {
        if (message == target) return true;
        for (final Object a : args) {
            if (a instanceof final Note note && note.containsMessage(target)) return true;
        }
        return false;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Note(SolverMessage message1, Object[] args1))) return false;
        return message == message1 && Arrays.equals(args, args1);
    }

    @Override
    public int hashCode() {
        return 31 * message.hashCode() + Arrays.hashCode(args);
    }

    @Override
    public String toString() {
        return describe();
    }
}
