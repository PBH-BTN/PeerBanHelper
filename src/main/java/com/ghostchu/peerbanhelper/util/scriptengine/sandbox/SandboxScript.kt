package com.ghostchu.peerbanhelper.util.scriptengine.sandbox

import com.ghostchu.peerbanhelper.util.scriptengine.CompiledScript
import java.io.File

class SandboxScript(
    private val file: File?,
    private val init: SandboxProtocol.Init,
    private val metadata: SandboxProtocol.Metadata
) : CompiledScript {
    override fun file() = file
    override fun name(): String = metadata.name()
    override fun author(): String = metadata.author()
    override fun version(): String = metadata.version()
    override fun cacheable() = metadata.cacheable()
    override fun threadSafe() = metadata.threadSafe()
    override fun script(): String = init.source()
    override fun scriptHashCode() = hashCode()
    override fun newEnv() = mutableMapOf<String, Any>()
    override fun execute(env: MutableMap<String, Any>): Any? = SandboxPool.execute(init, env)
}
