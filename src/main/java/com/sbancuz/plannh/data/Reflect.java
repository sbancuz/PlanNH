package com.sbancuz.plannh.data;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reflective access to members another mod did not make public.
 *
 * <p>
 * PlanNH models machines from other mods, and the numbers it reads often sit in private fields or
 * methods. The three access shapes are written once here. Callers differ only in how they handle a
 * failed lookup, and that handling stays at the call site: some can continue without the value, and
 * some must not.
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
     * The nearest declaration of a method walking up from {@code from}, or null when no class on the
     * way declares it. Callers read the declaring class as a signal: a machine that declares
     * {@code supportsMachineModeSwitch} has modes. So the walk stops before {@code until}, which lets a
     * caller exclude the base class that declares it for every machine.
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
                // declaration is further up, or absent
            }
        }
        return null;
    }

    /** Makes a member accessible. Public for callers that find a member by walking a class themselves. */
    @Nonnull
    public static <T extends AccessibleObject> T accessible(@Nonnull final T member) {
        AccessibleObject.setAccessible(new AccessibleObject[] { member }, true);
        return member;
    }
}
