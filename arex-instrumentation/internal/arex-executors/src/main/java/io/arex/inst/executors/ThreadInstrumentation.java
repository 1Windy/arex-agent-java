package io.arex.inst.executors;

import io.arex.agent.bootstrap.ctx.RunnableWrapper;
import io.arex.inst.runtime.context.ArexContext;
import io.arex.inst.runtime.context.ContextManager;
import io.arex.inst.extension.MethodInstrumentation;
import io.arex.inst.extension.TypeInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.List;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ThreadInstrumentation extends TypeInstrumentation {
    /**
     * ThreadInstrumentation
     * 包含 $StartAdvice 类。其中包含一个名为 methodEnter 的静态方法，该方法使用了 Java Agent 中的 Advice 注解。
     *
     * 这个方法的作用是在 run 方法执行前拦截它，并进行一些操作。
     * 具体来说，这个方法会将 run 方法的参数 runnable 通过 FieldValue 注解获取到，然后检查是否存在 ArexContext，
     * 如果存在，就使用 RunnableWrapper.get() 方法包装这个 runnable，然后再将包装后的 runnable 赋值回去。
     * 包装了什么？ --》 TraceTransmitter用来传递链路trace。
     */

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        return is(Thread.class);
    }

    @Override
    public List<MethodInstrumentation> methodAdvices() {
        return singletonList(buildStartAdvice());
    }

    private MethodInstrumentation buildStartAdvice() {
        return new MethodInstrumentation(
                isMethod().and(named("start")).and(takesArguments(0)),
                this.getClass().getName() + "$StartAdvice");
    }

    @SuppressWarnings("unused")
    public static class StartAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void methodEnter(
                @Advice.FieldValue(value = "target", readOnly = false) Runnable runnable) {
            ArexContext context = ContextManager.currentContext();
            if (context != null) {
                // RunnableWrapper构造器中捕获当前上下文，用TraceTransmitter传递调用链路Trace
                runnable = RunnableWrapper.get(runnable);
            }
        }
    }
}
