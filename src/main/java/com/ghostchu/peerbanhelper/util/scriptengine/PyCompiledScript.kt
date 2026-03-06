package com.ghostchu.peerbanhelper.util.scriptengine

import java.io.File
import kotlin.use

class PyCompiledScript(
    private val _file: File?,
    private val _name: String?,
    private val _author: String?,
    private val _cacheable: Boolean,
    private val _threadSafe: Boolean,
    private val _version: String?,
    private val _script: String?,
    private val compiledCode: Any?,
    private val interpreter: PyThreadSafeInterpreter?
) : CompiledScript {

    override fun file(): File? = _file
    override fun name(): String? = _name
    override fun author(): String? = _author
    override fun cacheable(): Boolean = _cacheable
    override fun threadSafe(): Boolean = _threadSafe
    override fun version(): String? = _version
    override fun script(): String? = _script

    override fun execute(env: MutableMap<String, Any>): Any? {
        interpreter?.lock.use { _ ->
            interpreter?.set("compiled_code", compiledCode)
            interpreter?.set("env", env)
            interpreter?.exec("context=dict(env)\n" + "exec(compiled_code,context)")
            return interpreter?.getValue("context.get('result')")
        }
    }

    override fun newEnv(): MutableMap<String, Any> {
        return HashMap()
    }

    override fun scriptHashCode(): Int {
        return _script?.hashCode() ?: 0
    }

    override fun close() {
        interpreter?.shutdown()
    }
}
