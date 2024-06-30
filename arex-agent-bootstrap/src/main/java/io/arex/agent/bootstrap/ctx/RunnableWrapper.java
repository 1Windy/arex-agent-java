package io.arex.agent.bootstrap.ctx;

import io.arex.agent.bootstrap.TraceContextManager;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;


public class RunnableWrapper implements Runnable {
    private static final ConcurrentHashMap<String, Optional<Field>> COMPARABLE_RUNNABLE_FIELD_MAP = new ConcurrentHashMap<>();
    private static final AtomicBoolean EXECEPTION_LOGGED = new AtomicBoolean(false);
    private final Runnable runnable;
    private final TraceTransmitter traceTransmitter;

    /**
     * 在调用 java.util.concurrent.ThreadPoolExecutor#execute(Runnable runnable) 时，
     * 通过使用 RunnableWrapper 将参数 runnable 进行 wrap，构造 RunnableWrapper 时将当前线程上下文捕获，
     * 在 run 方法时替换子线程上下文，执行完后再还原子线程上下文。
     */
    private RunnableWrapper(Runnable runnable) {
        this.runnable = runnable;
        // 捕获当前线程上下文，TraceTransmitter用来传递链路trace
        this.traceTransmitter = TraceTransmitter.create();
    }

    @Override
    public void run() {
        //替换子线程上下文
        try (TraceTransmitter tm = traceTransmitter.transmit()) {
            runnable.run();
        }
        //还原子线程上下文
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RunnableWrapper that = (RunnableWrapper) o;
        return runnable.equals(that.runnable);
    }

    @Override
    public int hashCode() {
        return runnable.hashCode();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " - " + runnable.toString();
    }

    public static Runnable get(Runnable runnable) {
        if (null == runnable  || TraceContextManager.get() == null) {
            return runnable;
        }

        if (runnable instanceof RunnableWrapper || runnable instanceof ForkJoinTask) {
            return runnable;
        }

        if (runnable instanceof Comparable) {
            wrapRunnableField(runnable);
            return runnable;
        }
        return new RunnableWrapper(runnable);
    }

    /**
     * issue: https://github.com/arextest/arex-agent-java/issues/516
     * executor with PriorityQueue
     * ex: class PriorityRunnable extends Runnable implements Comparable<PriorityRunnable> {
     *     private final Runnable run;
     *     private final int priority;
     * }
     * can't wrap the PriorityRunnable because queue need runnable implement Comparable,
     * so we need to wrap the original runnable field.
     */
    private static void wrapRunnableField(Runnable runnable) {
        try {
            Class<? extends Runnable> runnableClass = runnable.getClass();
            Optional<Field> originalRunnableFieldOpt = COMPARABLE_RUNNABLE_FIELD_MAP.computeIfAbsent(runnableClass.getName(), k -> {
                for (Field declaredField : runnableClass.getDeclaredFields()) {
                    Class<?> declaredFieldType = declaredField.getType();
                    if (declaredFieldType.isAssignableFrom(Runnable.class) && !declaredFieldType.isAssignableFrom(Comparable.class)) {
                        declaredField.setAccessible(true);
                        return Optional.of(declaredField);
                    }
                }
                return Optional.empty();
            });
            if (originalRunnableFieldOpt.isPresent()) {
                Field originalRunnableField = originalRunnableFieldOpt.get();
                Runnable originalRunnable = (Runnable) originalRunnableField.get(runnable);
                if (originalRunnable instanceof RunnableWrapper) {
                    return;
                }
                RunnableWrapper runnableWrapper = new RunnableWrapper(originalRunnable);
                originalRunnableField.set(runnable, runnableWrapper);
            }
        } catch (Exception e) {
            if (EXECEPTION_LOGGED.compareAndSet(false, true)) {
                System.err.printf("wrap original runnable %s failed.%n", runnable.getClass().getName());
            }
        }
    }
}
