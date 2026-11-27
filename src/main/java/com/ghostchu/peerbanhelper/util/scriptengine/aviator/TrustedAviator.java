package com.ghostchu.peerbanhelper.util.scriptengine.aviator;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TextManager;
import com.ghostchu.peerbanhelper.util.*;
import com.ghostchu.peerbanhelper.util.encrypt.RSAUtils;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.runtime.JavaMethodReflectionFunctionMissing;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TrustedAviator {
    private TrustedAviator() {
    }

    static void configure(AviatorEvaluatorInstance evaluator) {
        // 启用反射方法查找
        evaluator.setFunctionMissing(JavaMethodReflectionFunctionMissing.getInstance());
        // 注册反射调用
        registerFunctions(evaluator, IPAddressUtil.class);
        registerFunctions(evaluator, HTTPUtil.class);
        registerFunctions(evaluator, JsonUtil.class);
        registerFunctions(evaluator, Lang.class);
        registerFunctions(evaluator, StrUtil.class);
        registerFunctions(evaluator, PeerBanHelper.class);
        registerFunctions(evaluator, InfoHashUtil.class);
        registerFunctions(evaluator, CommonUtil.class);
        registerFunctions(evaluator, ByteUtil.class);
        registerFunctions(evaluator, MiscUtil.class);
        registerFunctions(evaluator, MsgUtil.class);
        registerFunctions(evaluator, SharedObject.class);
        registerFunctions(evaluator, UrlEncoderDecoder.class);
        registerFunctions(evaluator, URLUtil.class);
        registerFunctions(evaluator, WebUtil.class);
        registerFunctions(evaluator, RSAUtils.class);
        registerFunctions(evaluator, Pageable.class);
        registerFunctions(evaluator, TextManager.class);
        registerFunctions(evaluator, ExchangeMap.class);
        registerFunctions(evaluator, Main.class);
    }

    private static void registerFunctions(AviatorEvaluatorInstance evaluator, Class<?> clazz) {
        try {
            evaluator.addInstanceFunctions(StrUtil.uncapitalize(clazz.getSimpleName()), clazz);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            log.error("Internal error: failed on register instance functions: {}", clazz.getName(), e);
            Sentry.captureException(e);
        }
        try {
            evaluator.addStaticFunctions(StrUtil.capitalize(clazz.getSimpleName()), clazz);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            log.error("Internal error: failed on register static functions: {}", clazz.getName(), e);
            Sentry.captureException(e);
        }
    }
}
