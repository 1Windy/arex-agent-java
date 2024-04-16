package io.arex.inst.demo;

import com.google.auto.service.AutoService;
import io.arex.inst.extension.ModuleInstrumentation;
import io.arex.inst.extension.TypeInstrumentation;

import java.util.Collections;
import java.util.List;

/**
 * @author zhenghf
 * @date 2024-04-11
 * @desc
 */

@AutoService(ModuleInstrumentation.class)
public class DalClientModuleInstrumentation extends ModuleInstrumentation {

    public DalClientModuleInstrumentation() {
        // 插件模块名，如果你的 DalClient 组件不同的版本之间代码差异比较大，且要分版本支持的话，可以指定不同的 version 匹配：
        // ModuleDescription.builder().name("dalclient").supportFrom(ComparableVersion.of("1.0")).supportTo(ComparableVersion.of("2.0")).build();
        super("arex-demo");
    }


    @Override
    public List<TypeInstrumentation> instrumentationTypes() {
        // 我们真正去修饰 DalClient 字节码的类
        return Collections.singletonList(new DalClientInstrumentation());
    }
}
