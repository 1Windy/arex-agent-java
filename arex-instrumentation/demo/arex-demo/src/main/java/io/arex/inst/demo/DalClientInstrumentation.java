package io.arex.inst.demo;

import com.your.company.dal.DalClient;
import io.arex.agent.bootstrap.model.MockResult;
import io.arex.inst.extension.MethodInstrumentation;
import io.arex.inst.extension.TypeInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collections;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * @author zhenghf
 * @date 2024-04-11
 * @desc
 */

public class DalClientInstrumentation extends TypeInstrumentation {
    /**
     * 这个类的功能就是在修改的 invoke 方法调用前后添加代码，实现录制和回放的功能。
     */

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        // 我们要修改的 DalClient 类路径
        return named("com.your.company.dal.DalClient");
    }

    @Override
    public List<MethodInstrumentation> methodAdvices() {
        // 我们要修饰的方法
        ElementMatcher<MethodDescription> matcher = named("invoke")
                // 这个方法的第一个参数类型，可能有同名方法，便于区分
                .and(takesArgument(0, named("com.your.company.dal.DalClient$Action")))
                .and(takesArgument(1, named("java.lang.String")));

        // InvokeAdvice 类是我们在 invoke 方法里需要添加的代码
        return Collections.singletonList(new MethodInstrumentation(matcher, InvokeAdvice.class.getName()));
    }

    /**
     * 类似于 AOP 的功能，分别在调用前后插入我们的代码，如果回放成功则返回 Mock 的结果，不走原来的逻辑，如果不回放，即需要录制，则在 return 前先录制结果。
     *
     * skipOn = Advice.OnNonDefaultValue 参数表示如果 mockResult != null && mockResult.notIgnoreMockResult()
     * 为 true 时（非默认值，boolean 类型默认值是 false）则跳过方法原来的逻辑，即不执行原方法逻辑而直接返回我们 Mock 的值。
     * 如果是 false 则执行方法原有的逻辑。
     */
    public static class InvokeAdvice {
        // OnMethodEnter 表示被修改的方法(invoke)逻辑调用前执行的操作
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
        public static boolean onEnter(@Advice.Argument(0) DalClient.Action action, // 获取被修饰方法的第一个参数引用
                                      @Advice.Argument(1) String param, // 获取被修饰方法的第二个参数引用
                                      @Advice.Local("mockResult") MockResult mockResult) { // 我们在该方法内自定义的变量 mockResult
            // 回放
            mockResult = DalClientAdvice.replay(action, param);
            return mockResult != null && mockResult.notIgnoreMockResult();
        }

        // OnMethodExit 表示被修改的方法 (invoke) 结束前执行的操作
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.Argument(0) DalClient.Action action,
                                  @Advice.Argument(1) String param,
                                  @Advice.Local("mockResult") MockResult mockResult,
                                  @Advice.Return(readOnly = false) Object result) {
            // 方法的返回结果 result
            if (mockResult != null && mockResult.notIgnoreMockResult()) {
                // 使用回放的结果
                result = mockResult.getResult();
                return;
            }
            // 录制
            DalClientAdvice.record(action, param, result);
        }
    }
}
