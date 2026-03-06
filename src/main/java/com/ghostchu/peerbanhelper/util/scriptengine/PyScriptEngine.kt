package com.ghostchu.peerbanhelper.util.scriptengine

import com.ghostchu.peerbanhelper.Main
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule
import com.ghostchu.peerbanhelper.module.CheckResult
import com.ghostchu.peerbanhelper.module.PeerAction
import com.ghostchu.peerbanhelper.text.Lang
import com.ghostchu.peerbanhelper.text.TextManager
import com.ghostchu.peerbanhelper.text.TranslationComponent
import com.ghostchu.peerbanhelper.wrapper.StructuredData
import org.python.core.PyCode
import org.python.util.PythonInterpreter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import java.util.*

@Component
class PyScriptEngine : ScriptEngine {
    private val interpreter: PythonInterpreter

    init {
        val props = Properties()
        props["python.import.site"] = "false"
        props["python.cachedir.skip"] = "true"
        PythonInterpreter.initialize(System.getProperties(), props, arrayOfNulls(0))
        this.interpreter = PythonInterpreter()
    }

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

    fun checkMalware(scriptContent: String, scriptPath: String?): Boolean {
        val platform = Main.getPlatform()
        if (platform != null) {
            try {
                platform.getMalwareScanner().use { scanner ->
                    if (scanner != null) {
                        if (scanner.isMalicious(scriptContent)) {
                            log.error(
                                TextManager.tlUI(
                                    Lang.MALWARE_SCANNER_DETECTED,
                                    "UserScript",
                                    scriptPath
                                )
                            )
                            return true
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                throw RuntimeException(e)
            }
        }
        return false
    }

    override fun compileScript(file: File?, fallbackName: String?, scriptContent: String?): CompiledScript? {
        if (scriptContent == null) return null

        if (checkMalware(scriptContent, file?.absolutePath ?: "<memory script>")) {
            return null
        }
        try {
            BufferedReader(StringReader(scriptContent)).use { reader ->
                var name = fallbackName
                var author = "Unknown"
                var version = "null"
                var cacheable = true
                var threadSafe = true
                while (true) {
                    val line: String = reader.readLine() ?: break
                    if (line.startsWith("#")) {
                        val comment = line.substring(1).trim()
                        if (comment.startsWith("@NAME")) {
                            name = comment.substring(5).trim()
                        } else if (comment.startsWith("@AUTHOR")) {
                            author = comment.substring(7).trim()
                        } else if (comment.startsWith("@CACHEABLE")) {
                            cacheable = comment.substring(10).trim().toBoolean()
                        } else if (comment.startsWith("@VERSION")) {
                            version = comment.substring(8).trim()
                        } else if (comment.startsWith("@THREADSAFE")) {
                            threadSafe = comment.substring(11).trim().toBoolean()
                        }
                    }
                }
                // 编译 Python 脚本
                val compiledCode: PyCode = interpreter.compile(scriptContent, file?.name ?: fallbackName)
                return PyCompiledScript(
                    file,
                    name,
                    author,
                    cacheable,
                    threadSafe,
                    version,
                    scriptContent,
                    compiledCode,
                    interpreter
                )
            }
        } catch (e: Exception) {
            log.warn("Python Script Engine unable to compile the script: {}", fallbackName, e)
            return null
        }
    }

    override fun getEngineName(): String = "Jython"

    override fun getFileExtension(): String = ".py"

    companion object {
        private val log = LoggerFactory.getLogger(PyScriptEngine::class.java)

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
