package com.ghostchu.peerbanhelper.util.scriptengine

import com.ghostchu.peerbanhelper.Main
import com.ghostchu.peerbanhelper.text.Lang
import com.ghostchu.peerbanhelper.text.TextManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

@Component
class PyScriptEngine : ScriptEngine {

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
            val interpreter = PyThreadSafeInterpreter()
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
                interpreter.lock.use { _ ->
                    interpreter.exec(scriptContent)
                    interpreter.invoke("shouldBanPeer", null_env)
                    return PyCompiledScript(
                        file,
                        name,
                        author,
                        cacheable,
                        threadSafe,
                        version,
                        scriptContent,
                        null,
                        interpreter
                    )
                }
            }
        } catch (e: Exception) {
            log.warn("Python Script Engine unable to compile the script: {}", fallbackName, e)
            return null
        }
    }

    override fun getEngineName(): String = "Jep"

    override fun getFileExtension(): String = ".py"

    companion object {
        private val log = LoggerFactory.getLogger(PyScriptEngine::class.java)
        private val null_env: MutableMap<String?, Any?> = hashMapOf(
            "torrent" to null,
            "peer" to null,
            "downloader" to null,
            "cacheable" to null,
            "server" to null,
            "moduleInstance" to null,
            "btnNetwork" to null,
            "banDuration" to null,
            "ramStorage" to null)
    }
}
