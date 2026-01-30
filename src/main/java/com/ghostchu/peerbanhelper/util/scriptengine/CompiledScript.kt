package com.ghostchu.peerbanhelper.util.scriptengine

import java.io.File

/**
 * 编译后的脚本通用接口
 */
interface CompiledScript {
    /**
     * 获取脚本文件
     */
    fun file(): File?

    /**
     * 获取脚本名称
     */
    fun name(): String?

    /**
     * 获取脚本作者
     */
    fun author(): String?

    /**
     * 是否可缓存
     */
    fun cacheable(): Boolean

    /**
     * 是否线程安全
     */
    fun threadSafe(): Boolean

    /**
     * 获取脚本版本
     */
    fun version(): String?

    /**
     * 获取脚本源代码
     */
    fun script(): String?

    /**
     * 执行脚本
     * 
     * @param env 环境变量
     * @return 执行结果
     */
    fun execute(env: MutableMap<String, Any>): Any?

    /**
     * 创建新的执行环境
     * 
     * @return 环境变量映射
     */
    fun newEnv(): MutableMap<String, Any>

    /**
     * 获取脚本的哈希码（用于缓存）
     */
    fun scriptHashCode(): Int
}