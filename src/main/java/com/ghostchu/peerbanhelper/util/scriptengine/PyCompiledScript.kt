package com.ghostchu.peerbanhelper.util.scriptengine

import org.python.core.PyCode
import org.python.core.PyObject
import org.python.util.PythonInterpreter
import java.io.File
import java.util.concurrent.locks.ReentrantLock

class PyCompiledScript(
    private val _file: File?,
    private val _name: String?,
    private val _author: String?,
    private val _cacheable: Boolean,
    private val _threadSafe: Boolean,
    private val _version: String?,
    private val _script: String?,
    private val compiledCode: PyCode?,
    private val interpreter: PythonInterpreter?
) : CompiledScript {

    // 用于保护解释器执行的锁，因为 PythonInterpreter 不是线程安全的
    private val executionLock = ReentrantLock()

    override fun file(): File? = _file
    override fun name(): String? = _name
    override fun author(): String? = _author
    override fun cacheable(): Boolean = _cacheable
    override fun threadSafe(): Boolean = _threadSafe
    override fun version(): String? = _version
    override fun script(): String? = _script

    override fun execute(env: MutableMap<String, Any>): Any? {
        // 无论 threadSafe 标志如何，都需要对解释器执行加锁
        // 因为 PythonInterpreter 本身不是线程安全的
        executionLock.lock()
        try {
            return executeInternal(env)
        } finally {
            executionLock.unlock()
        }
    }

    private fun executeInternal(env: MutableMap<String, Any>): Any? {
        for (entry in env.entries) {
            interpreter?.set(entry.key, entry.value)
        }
        interpreter?.exec(compiledCode)
        val result: PyObject? = interpreter?.get("result")
        if (result != null) {
            return result.__tojava__(Any::class.java)
        }
        return null
    }

    override fun newEnv(): MutableMap<String, Any> {
        return HashMap()
    }

    override fun scriptHashCode(): Int {
        return _script?.hashCode() ?: 0
    }
}
