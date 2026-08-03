package com.sbancuz.plannh.annotation;

import java.lang.reflect.Field;
import java.util.Set;

import com.sbancuz.plannh.PlanNH;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import sun.misc.Unsafe;

public class VersionedInjector {

    public static void injectAll(ASMDataTable asmData) {
        Set<ASMDataTable.ASMData> mods = asmData.getAll(Versioned.Mod.class.getName());
        for (ASMDataTable.ASMData data : mods) {
            try {
                Class<?> modClass = Class.forName(data.getClassName());
                Versioned.Mod modAnn = modClass.getAnnotation(Versioned.Mod.class);
                if (modAnn != null) {
                    processModClass(modClass, modAnn.modId(), modAnn.sinceVersion());
                }
            } catch (ReflectiveOperationException e) {
                PlanNH.LOG.error("Failed to process @Versioned.Mod on {}", data.getClassName(), e);
            }
        }
    }

    private static void processModClass(Class<?> modClass, String modId, String sinceVersion) {
        for (Class<?> inner : modClass.getDeclaredClasses()) {
            Versioned.Class classAnn = inner.getAnnotation(Versioned.Class.class);
            if (classAnn == null) continue;

            String resolvedModId = classAnn.modId()
                .isEmpty() ? modId : classAnn.modId();
            String resolvedSince = classAnn.sinceVersion()
                .isEmpty() ? sinceVersion : classAnn.sinceVersion();
            Class<?> target = resolveTargetClass(classAnn, inner, resolvedModId);
            if (target == null) continue;

            for (Field field : inner.getDeclaredFields()) {
                Versioned.Constant constAnn = field.getAnnotation(Versioned.Constant.class);
                String fieldName = (constAnn != null && !constAnn.value()
                    .isEmpty()) ? constAnn.value() : field.getName();
                String fieldSince = (constAnn != null && !constAnn.sinceVersion()
                    .isEmpty()) ? constAnn.sinceVersion() : resolvedSince;
                injectField(field, target, resolvedModId, fieldSince, fieldName);
            }
        }
    }

    private static Class<?> resolveTargetClass(Versioned.Class ann, Class<?> inner, String modId) {
        if (ann.clazz() != void.class) return ann.clazz();

        String className = !ann.value()
            .isEmpty() ? ann.value() : inner.getSimpleName();

        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {}

        return null;
    }

    private static Unsafe UNSAFE;

    private static Unsafe unsafe() {
        if (UNSAFE == null) {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                UNSAFE = (Unsafe) f.get(null);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        return UNSAFE;
    }

    private static void injectField(Field field, Class<?> sourceClass, String modId, String sinceVersion,
        String fieldName) {
        try {
            Field sourceField = sourceClass.getDeclaredField(fieldName);
            Object value = sourceField.get(null);
            Class<?> type = field.getType();
            long offset = unsafe().staticFieldOffset(field);
            if (type == int.class) {
                unsafe().putInt(field.getDeclaringClass(), offset, (int) value);
            } else if (type == long.class) {
                unsafe().putLong(field.getDeclaringClass(), offset, (long) value);
            } else if (type == double.class) {
                unsafe().putDouble(field.getDeclaringClass(), offset, (double) value);
            } else if (type == float.class) {
                unsafe().putFloat(field.getDeclaringClass(), offset, (float) value);
            } else if (type == boolean.class) {
                unsafe().putBoolean(field.getDeclaringClass(), offset, (boolean) value);
            } else {
                unsafe().putObject(field.getDeclaringClass(), offset, value);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            warnIfUnexpected(sourceClass, modId, sinceVersion, fieldName);
        }
    }

    private static void warnIfUnexpected(Class<?> sourceClass, String modId, String sinceVersion, String fieldName) {
        ModContainer container = Loader.instance()
            .getIndexedModList()
            .get(modId);
        if (container == null) return;

        DefaultArtifactVersion current = new DefaultArtifactVersion(container.getVersion());
        DefaultArtifactVersion since = new DefaultArtifactVersion(sinceVersion);

        if (current.compareTo(since) >= 0) {
            PlanNH.LOG.warn(
                "Versioned field {}.{} missing on {} {} (expected present since {}) \u2014 falling back to hardcoded default",
                sourceClass.getName(),
                fieldName,
                modId,
                container.getVersion(),
                sinceVersion);
        }
    }
}
