package com.ghostchu.peerbanhelper.util.scriptengine

import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule
import com.ghostchu.peerbanhelper.module.CheckResult
import com.ghostchu.peerbanhelper.module.PeerAction
import com.ghostchu.peerbanhelper.text.Lang
import com.ghostchu.peerbanhelper.text.TextManager
import com.ghostchu.peerbanhelper.text.TranslationComponent
import com.ghostchu.peerbanhelper.wrapper.StructuredData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.util.*

@Component
class ScriptEngineManager(
    avScriptEngine: AVScriptEngine
) {
    private val engineMap: MutableMap<String, ScriptEngine> = HashMap()

    init {
        // 注册引擎
        engineMap[".av"] = avScriptEngine
    }

    val supportedExtensions: List<String>
        get() = engineMap.keys.toList()

    fun getEngineByExtension(extension: String): ScriptEngine? {
        return engineMap[extension.lowercase(Locale.getDefault())]
    }

    fun getEngineByFileName(fileName: String): ScriptEngine? {
        for (entry in engineMap.entries) {
            if (fileName.endsWith(entry.key)) {
                return entry.value
            }
        }
        return engineMap[".av"]
    }

    fun compileScript(file: File?, fallbackName: String?, scriptContent: String?): CompiledScript? {
        val fileName: String = file?.name ?: fallbackName ?: return null
        val engine = getEngineByFileName(fileName)
        if (engine == null) {
            log.warn("No script engine found for file: {}", fileName)
            return null
        }
        return engine.compileScript(file, fallbackName, scriptContent)
    }

    /**
     * 处理脚本执行结果
     */
    fun handleResult(script: CompiledScript, banDuration: Long, returns: Any?): CheckResult? {
        if (returns is Boolean) {
            if (returns) {
                return CheckResult(
                    javaClass, PeerAction.BAN, banDuration,
                    TranslationComponent(Lang.USER_SCRIPT_RULE),
                    TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, script.name(), "true"),
                    StructuredData.create().add("script", script.name())
                )
            }
            return null
        }
        if (returns is Number) {
            val i = returns.toInt()
            if (i == 0) {
                return null
            } else if (i == 1) {
                return CheckResult(
                    javaClass,
                    PeerAction.BAN,
                    banDuration,
                    TranslationComponent(Lang.USER_SCRIPT_RULE),
                    TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, script.name(), returns.toString()),
                    StructuredData.create().add("script", script.name())
                )
            } else if (i == 2) {
                return CheckResult(
                    javaClass,
                    PeerAction.SKIP,
                    banDuration,
                    TranslationComponent(Lang.USER_SCRIPT_RULE),
                    TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, script.name(), returns.toString()),
                    StructuredData.create().add("script", script.name())
                )
            } else {
                log.error(TextManager.tlUI(Lang.RULE_ENGINE_INVALID_RETURNS, script))
                return null
            }
        }
        if (returns is PeerAction) {
            return CheckResult(
                javaClass,
                returns,
                banDuration,
                TranslationComponent(Lang.USER_SCRIPT_RULE),
                TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, script.name(), returns.name),
                StructuredData.create().add("script", script.name())
            )
        }
        if (returns is String) {
            if (returns.isBlank()) {
                return OK_CHECK_RESULT
            } else if (returns.startsWith("@")) {
                return CheckResult(
                    javaClass, PeerAction.SKIP, banDuration,
                    TranslationComponent(Lang.USER_SCRIPT_RULE),
                    TranslationComponent(returns.substring(1)), StructuredData.create().add("script", script.name())
                )
            } else {
                return CheckResult(
                    javaClass,
                    PeerAction.BAN,
                    banDuration,
                    TranslationComponent(Lang.USER_SCRIPT_RULE),
                    TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, script.name(), returns),
                    StructuredData.create().add("script", script.name())
                )
            }
        }
        if (returns is CheckResult) {
            return returns
        }
        log.error(TextManager.tlUI(Lang.RULE_ENGINE_INVALID_RETURNS, script.name()))
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(ScriptEngineManager::class.java)

        val OK_CHECK_RESULT: CheckResult = CheckResult(
            AbstractRuleFeatureModule::class.java,
            PeerAction.NO_ACTION,
            0,
            TranslationComponent("N/A"),
            TranslationComponent("Check passed"),
            StructuredData.create().add("status", "pass")
        )
    }
}
