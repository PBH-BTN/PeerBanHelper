package com.ghostchu.peerbanhelper.util.scriptengine

import java.io.File

/**
 * 脚本引擎通用接口
 */
interface ScriptEngine {
    /**
     * 编译脚本
     *
     * @param file          脚本文件
     * @param fallbackName  备用名称
     * @param scriptContent 脚本内容
     * @return 编译后的脚本，如果编译失败返回 null
     */
    fun compileScript(file: File?, fallbackName: String?, scriptContent: String?): CompiledScript?

    /**
     * 获取脚本引擎名称
     */
    fun getEngineName(): String?

    /**
     * 获取支持的脚本文件扩展名
     */
    fun getFileExtension(): String?
}
