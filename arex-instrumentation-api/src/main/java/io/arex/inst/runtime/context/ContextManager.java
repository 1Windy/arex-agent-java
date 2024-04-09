package io.arex.inst.runtime.context;

import io.arex.agent.bootstrap.TraceContextManager;
import io.arex.agent.bootstrap.util.CollectionUtil;
import io.arex.agent.bootstrap.util.StringUtil;
import io.arex.inst.runtime.listener.ContextListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContextManager {
    private static final Map<String, ArexContext> RECORD_MAP = new LatencyContextHashMap();
    private static final List<ContextListener> LISTENERS = new ArrayList<>();

    /**
     * agent call this method
     */
    public static ArexContext currentContext() {
        return currentContext(false, null);
    }

    /**
     * AREX Agent 源码解读之全链路跟踪和 Mock 数据读写: https://mp.weixin.qq.com/s/eCEWvZMu_3mz-E0diR-Ppg
     *
     * 1、(FilterInstrumentationV3)入口请求的录制是在 javax.servlet.Filter 的 doFilter（还有几个其他的类和函数等等）收到请求后，如果通过录制频率检测，就会开始录制请求。
     *
     * 2、当函数被调用时（ContextManager.currentContext(true, id)）：
     *
     * 在 EventProcessor 中，initContext 函数会调用 ContextManager.currentContext(true, id)，onCreate 会调用 initContext 函数。
     *
     * 在 CaseEventDispatcher 中，onEvent(Create) 会调用上述的 initContext 函数。
     *
     * 在 ServletAdviceHelper 中，会调用上述的 onEvent 函数。
     *
     * 在 FilterInstrumentationV3 中，会调用上述的 onEvent 函数。这些类和函数的注入可以在代码中看到。
     */

    /**
     * agent will call this method
     * record scene: recordId is map key
     * replay scene: replayId is map key
     */
    public static ArexContext currentContext(boolean createIfAbsent, String recordId) {
        String traceId = TraceContextManager.get(createIfAbsent);
        if (StringUtil.isEmpty(traceId)) {
            return null;
        }
        if (createIfAbsent) {
            final ArexContext arexContext = createContext(recordId, traceId);
            publish(arexContext, true);
            RECORD_MAP.put(traceId, arexContext);
            return arexContext;
        }
        return RECORD_MAP.get(traceId);
    }

    /**
     *  ArexContext.of(recordId, replayId)
     */
    private static ArexContext createContext(String recordId, String traceId) {
        // replay scene: traceId is replayId
        if (StringUtil.isNotEmpty(recordId)) {
            return ArexContext.of(recordId, traceId);
        }
        // record scene: traceId is recordId
        return ArexContext.of(traceId);
    }

    public static ArexContext getContext(String traceId) {
        return RECORD_MAP.get(traceId);
    }

    public static boolean needRecord() {
        ArexContext context = currentContext();
        return context != null && !context.isReplay();
    }

    public static boolean needReplay() {
        ArexContext context = currentContext();
        return context != null && context.isReplay();
    }

    public static boolean needRecordOrReplay() {
        return currentContext() != null;
    }

    public static void remove() {
        String caseId = TraceContextManager.remove();
        if (StringUtil.isEmpty(caseId)) {
            return;
        }
        ArexContext context = RECORD_MAP.remove(caseId);
        publish(context, false);
    }

    public static void registerListener(ContextListener listener) {
        LISTENERS.add(listener);
    }

    private static void publish(ArexContext context, boolean isCreate) {
        if (CollectionUtil.isNotEmpty(LISTENERS)) {
            LISTENERS.forEach(listener -> {
                if (isCreate) {
                    listener.onCreate(context);
                } else {
                    listener.onComplete(context);
                }
            });
        }
    }
}
