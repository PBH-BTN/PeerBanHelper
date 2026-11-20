package com.ghostchu.peerbanhelper.util.scriptengine.aviator;

import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.ClassPathConfigFunctionLoader;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.AviatorBoolean;
import com.googlecode.aviator.runtime.type.AviatorJavaType;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import com.googlecode.aviator.runtime.type.AviatorString;

import java.util.Locale;
import java.util.Map;

final class SandboxedAviator {
    private SandboxedAviator() {
    }

    static void configure(AviatorEvaluatorInstance evaluator) {
        evaluator.enableSandboxMode();
        evaluator.disableFeature(Feature.StaticFields);
        evaluator.disableFeature(Feature.Use);
        evaluator.removeFunctionLoader(ClassPathConfigFunctionLoader.getInstance());

        // Shipped rules use unqualified Java String methods on snapshot values.
        evaluator.aliasFunction("str", "toString");
        evaluator.addFunction(new AbstractFunction() {
            @Override
            public String getName() {
                return "isBlank";
            }

            @Override
            public AviatorObject call(Map<String, Object> env, AviatorObject argument) {
                var value = FunctionUtils.getStringValue(argument, env);
                return AviatorBoolean.valueOf(value == null || value.isBlank());
            }
        });
        evaluator.addFunction(new AbstractFunction() {
            @Override
            public String getName() {
                return "toLowerCase";
            }

            @Override
            public AviatorObject call(Map<String, Object> env, AviatorObject argument) {
                return new AviatorString(FunctionUtils.getStringValue(argument, env).toLowerCase(Locale.ROOT));
            }
        });

        // peer-ids.av.example uses literal String arrays. Keep class lookup disabled.
        evaluator.removeFunction("seq.array");
        evaluator.addFunction(new AbstractVariadicFunction() {
            @Override
            public String getName() {
                return "seq.array";
            }

            @Override
            public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... arguments) {
                if (arguments.length == 0 || !(arguments[0] instanceof AviatorJavaType type)
                        || !"java.lang.String".equals(type.getName())) {
                    throw new IllegalArgumentException("Sandbox seq.array supports only java.lang.String");
                }
                String[] values = new String[arguments.length - 1];
                for (int i = 0; i < values.length; i++) {
                    values[i] = FunctionUtils.getStringValue(arguments[i + 1], env);
                }
                return AviatorRuntimeJavaType.valueOf(values);
            }
        });
    }
}
