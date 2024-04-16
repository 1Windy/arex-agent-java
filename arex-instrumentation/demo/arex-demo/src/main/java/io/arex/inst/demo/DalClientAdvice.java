package io.arex.inst.demo;

import com.your.company.dal.DalClient;
import io.arex.agent.bootstrap.model.MockResult;
import io.arex.agent.bootstrap.model.Mocker;
import io.arex.inst.runtime.context.ContextManager;
import io.arex.inst.runtime.serializer.Serializer;
import io.arex.inst.runtime.util.MockUtils;
import io.arex.inst.runtime.util.TypeUtil;

/**
 * @author zhenghf
 * @date 2024-04-11
 * @desc
 */

public class DalClientAdvice {
    /**
     * 录制
     * @param action
     * @param param
     * @param result
     */
    public static void record(DalClient.Action action, String param, Object result) {
        if (ContextManager.needRecord()) {
            Mocker mocker = buildMocker(action, param);
            mocker.getTargetResponse().setBody(Serializer.serialize(result));
            // TypeUtil获取java全类型type
            mocker.getTargetResponse().setType(TypeUtil.getName(result));
            MockUtils.recordMocker(mocker);
        }
    }

    /**
     * 回放
     * @param action
     * @param param
     * @return
     */
    public static MockResult replay(DalClient.Action action, String param) {
        if (ContextManager.needReplay()) {
            Mocker mocker = buildMocker(action, param);
            Object result = MockUtils.replayBody(mocker);
            return MockResult.success(result);
        }
        return null;
    }

    private static Mocker buildMocker(DalClient.Action action, String param) {
        Mocker mocker = MockUtils.createDatabase(action.name().toLowerCase());
        mocker.getTargetRequest().setBody(param);
        return mocker;
    }
}
