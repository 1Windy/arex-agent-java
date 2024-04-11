package io.arex.agent.bootstrap.ctx;

import io.arex.agent.bootstrap.TraceContextManager;

import java.util.concurrent.ForkJoinTask;


public class RunnableWrapper implements Runnable {
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
        return new RunnableWrapper(runnable);
    }
}
