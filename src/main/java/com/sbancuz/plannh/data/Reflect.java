package com.sbancuz.plannh.data;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reaching a member another mod did not make public.
 *
 * <p>
 * PlanNH models machines it does not own, and the numbers it needs are often held in a field or a
 * method the mod never meant to publish. Every such reach is one of the three shapes here, so they
 * are written once - what differs between callers is only what they do when the reach fails, and
 * that stays at the call site: some can carry on without the value, and some must not.
 */
public final class Reflect {

    private Reflect() {}

    /** The class, or null when this pack does not have the mod that declares it. */
    @Nullable
    public static Class<?> type(@Nonnull final String className) {
        try {
            return Class.forName(className);
        } catch (final ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    /** An accessible field, for a caller that cannot go on without it. */
    @Nonnull
    public static Field field(@Nonnull final Class<?> owner, @Nonnull final String name)
        throws ReflectiveOperationException {
        return accessible(owner.getDeclaredField(name));
    }

    /** An accessible field, or null for a caller that can carry on without it. */
    @Nullable
    public static Field optionalField(@Nonnull final Class<?> owner, @Nonnull final String name) {
        try {
            return field(owner, name);
        } catch (final ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    /**
     * The nearest declaration of a method walking up from {@code from}, or null when nothing on the
     * way declares it. Which class declares a method is itself the answer to several questions here -
     * a machine that declares {@code supportsMachineModeSwitch} is saying it really has modes - so the
     * walk stops before {@code until}, letting a caller exclude the base class that declares it for
     * everyone.
     *
     * @param until the class to stop before, or null to walk to the top
     */
    @Nullable
    public static Method declaredMethod(@Nonnull final Class<?> from, @Nullable final Class<?> until,
        @Nonnull final String name, final Class<?>... argumentTypes) {
        for (Class<?> c = from; c != null && c != until; c = c.getSuperclass()) {
            try {
                return accessible(c.getDeclaredMethod(name, argumentTypes));
            } catch (final NoSuchMethodException keepWalking) {
                // The declaration is further up, or there is none at all.
            }
        }
        return null;
    }

    /** Opens a member a caller found for itself, by walking a class rather than by naming it. */
    @Nonnull
    public static <T extends AccessibleObject> T accessible(@Nonnull final T member) {
        AccessibleObject.setAccessible(new AccessibleObject[] { member }, true);
        return member;
    }
}
