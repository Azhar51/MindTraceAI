package com.mindtrace.ai.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Global executor pool for the whole application.
 *
 * <p>Replaces ad-hoc {@code new Thread()} calls across the codebase with a
 * centralized, lifecycle-safe threading model. Provides three executor pools:</p>
 *
 * <ul>
 *   <li><b>diskIO</b> — single-thread for Room DB queries and file I/O</li>
 *   <li><b>networkIO</b> — fixed 3-thread pool for API calls</li>
 *   <li><b>mainThread</b> — posts to the Android main looper</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>
 *     AppExecutors.diskIO().execute(() -> {
 *         // background work
 *         AppExecutors.mainThread().execute(() -> {
 *             // UI update
 *         });
 *     });
 * </pre>
 *
 * <p>Thread-safe singleton pattern ensures zero duplicate executor creation.</p>
 */
public final class AppExecutors {

    private static volatile AppExecutors INSTANCE;

    private final ExecutorService diskIO;
    private final ExecutorService networkIO;
    private final Executor mainThread;

    private AppExecutors() {
        diskIO = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mindtrace-disk-io");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        networkIO = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "mindtrace-network-io");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        mainThread = new MainThreadExecutor();
    }

    @NonNull
    public static AppExecutors getInstance() {
        if (INSTANCE == null) {
            synchronized (AppExecutors.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AppExecutors();
                }
            }
        }
        return INSTANCE;
    }

    // ── Convenience static accessors ──

    /** Single-thread executor for disk/database I/O. */
    @NonNull
    public static ExecutorService diskIO() {
        return getInstance().diskIO;
    }

    /** Fixed 3-thread pool for network operations. */
    @NonNull
    public static ExecutorService networkIO() {
        return getInstance().networkIO;
    }

    /** Posts work to the Android main/UI thread. */
    @NonNull
    public static Executor mainThread() {
        return getInstance().mainThread;
    }

    /**
     * Convenience: run work on diskIO then post result to main thread.
     *
     * @param backgroundWork produces a result on the IO thread
     * @param uiCallback     consumes the result on the main thread
     * @param <T>            result type
     */
    public static <T> void runOnDiskThenUi(
            @NonNull java.util.concurrent.Callable<T> backgroundWork,
            @NonNull java.util.function.Consumer<T> uiCallback) {
        diskIO().execute(() -> {
            try {
                T result = backgroundWork.call();
                mainThread().execute(() -> uiCallback.accept(result));
            } catch (Exception e) {
                // Fail silently — callers should handle errors in backgroundWork
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // MAIN THREAD EXECUTOR
    // ═══════════════════════════════════════════════════════════════════

    private static class MainThreadExecutor implements Executor {
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainHandler.post(command);
        }
    }
}
