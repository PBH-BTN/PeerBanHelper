package com.ghostchu.peerbanhelper.util.scriptengine

import com.ghostchu.peerbanhelper.Main
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule
import com.ghostchu.peerbanhelper.module.CheckResult
import com.ghostchu.peerbanhelper.module.PeerAction
import com.ghostchu.peerbanhelper.text.Lang
import com.ghostchu.peerbanhelper.text.TextManager
import com.ghostchu.peerbanhelper.text.TranslationComponent
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxPool
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol.Init
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxScript
import com.ghostchu.peerbanhelper.wrapper.StructuredData
import jakarta.annotation.PreDestroy
import java.io.File
import java.io.IOException
import java.util.Locale
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ScriptEngineManager(
    engines: List<ScriptEngine>
) {
    private val engineMap = engines.associateBy { it.getFileExtension() }

    val supportedExtensions: List<String>
        get() = engineMap.keys.sorted()

    fun getEngineByFileName(fileName: String): ScriptEngine? =
        engineMap["." + fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)]

    fun compileLocal(file: File, source: String, sandbox: Boolean): CompiledScript? =
        compile(file, file.name, source, sandbox)

    fun compileBtn(name: String, source: String): CompiledScript? = compile(null, name, source, true)

    private fun compile(file: File?, name: String, source: String, sandbox: Boolean): CompiledScript? {
        val engine = if (file == null && '.' !in name) engineMap[".av"] else getEngineByFileName(name)
        requireNotNull(engine) { "Unsupported script extension: $name" }
        Main.getPlatform()?.malwareScanner?.use { scanner ->
            if (scanner.isMalicious(source)) {
                log.error("Malware scanner rejected script: {}", name)
                return null
            }
        }
        if (!sandbox) return engine.compileScript(file, name, source)
        val init = Init(engine.getFileExtension(), name, source, if (file == null) "btn" else "local")
        return SandboxScript(file, init, SandboxPool.prepare(init))
    }

    @Throws(IOException::class)
    fun validateScriptSyntax(fileName: String, source: String, sandbox: Boolean): Boolean {
        val engine = getEngineByFileName(fileName) ?: return false
        if (sandbox) {
            SandboxPool.prepare(Init(engine.getFileExtension(), fileName, source, "local"))
            return true
        }
        return engine.validateScriptSyntax(fileName, source)
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
            when (i) {
                0 -> {
                    return null
                }

                1 -> {
                    return CheckResult(
                        javaClass,
                        PeerAction.BAN,
                        banDuration,
                        TranslationComponent(Lang.USER_SCRIPT_RULE),
                        TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, script.name(), returns.toString()),
                        StructuredData.create().add("script", script.name())
                    )
                }

                2 -> {
                    return CheckResult(
                        javaClass,
                        PeerAction.SKIP,
                        banDuration,
                        TranslationComponent(Lang.USER_SCRIPT_RULE),
                        TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, script.name(), returns.toString()),
                        StructuredData.create().add("script", script.name())
                    )
                }

                else -> {
                    log.error(TextManager.tlUI(Lang.RULE_ENGINE_INVALID_RETURNS, script))
                    return null
                }
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

    @PreDestroy
    fun close() = SandboxPool.closeAll()

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
