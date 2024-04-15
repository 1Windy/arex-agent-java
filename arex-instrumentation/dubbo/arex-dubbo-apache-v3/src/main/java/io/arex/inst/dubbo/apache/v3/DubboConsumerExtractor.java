package io.arex.inst.dubbo.apache.v3;

import io.arex.agent.bootstrap.model.MockResult;
import io.arex.agent.bootstrap.model.Mocker;
import io.arex.inst.dubbo.common.DubboExtractor;
import io.arex.inst.runtime.util.IgnoreUtils;
import io.arex.inst.runtime.util.MockUtils;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter;
import org.apache.dubbo.rpc.support.RpcUtils;

public class DubboConsumerExtractor extends DubboExtractor {
    private final DubboAdapter adapter;
    public DubboConsumerExtractor(DubboAdapter adapter) {
        this.adapter = adapter;
    }
    public Result record(Result result) {
        return adapter.execute(result, makeMocker());
    }
    private Mocker makeMocker() {
        Mocker mocker = MockUtils.createDubboConsumer(adapter.getServiceOperation());
        return buildMocker(mocker, adapter, null, null);
    }
    public MockResult replay() {
        Object result = MockUtils.replayBody(makeMocker());
        boolean ignoreMockResult = IgnoreUtils.ignoreMockResult(adapter.getPath(), adapter.getOperationName());
        AsyncRpcResult asyncRpcResult = null;
        if (result != null && !ignoreMockResult) {
            Invocation invocation = adapter.getInvocation();
            if (result instanceof Throwable) {
                asyncRpcResult = AsyncRpcResult.newDefaultAsyncResult((Throwable) result, invocation);
            } else {
                asyncRpcResult = AsyncRpcResult.newDefaultAsyncResult(result, invocation);
            }
            // need to set invoke mode to FUTURE if return type is CompletableFuture
            if (invocation instanceof RpcInvocation) {
                RpcInvocation rpcInv = (RpcInvocation) invocation;
                rpcInv.setInvokeMode(RpcUtils.getInvokeMode(adapter.getUrl(), invocation));
            }
            RpcContext.getContext().setFuture(new FutureAdapter<>(asyncRpcResult.getResponseFuture()));
        }
        return MockResult.success(ignoreMockResult, asyncRpcResult);
    }
}
