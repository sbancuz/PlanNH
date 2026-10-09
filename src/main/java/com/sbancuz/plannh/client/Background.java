package com.sbancuz.plannh.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;

public final class Background {

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "plannh-solver");
        thread.setDaemon(true);
        return thread;
    });

    public static void offClient(final Runnable work, final Runnable onClient) {
        offClient(() -> {
            work.run();
            return null;
        }, ignored -> onClient.run());
    }

    /** Computes on the one background thread; applies the result on the next client tick. */
    public static <T> void offClient(final Supplier<T> work, final Consumer<T> onClient) {
        POOL.execute(() -> {
            final AtomicReference<T> result = new AtomicReference<>();
            try {
                result.set(work.get());
            } finally {
                publish(() -> onClient.accept(result.get()));
            }
        });
    }

    private static void publish(final Runnable onClient) {
        try {
            Minecraft.getMinecraft()
                .func_152344_a(onClient);
        } catch (final LinkageError | RuntimeException noClient) {
            onClient.run();
        }
    }
}
