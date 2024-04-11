package com.ghostchu.peerbanhelper

import org.bspfsystems.yamlconfiguration.file.YamlConfiguration

open class BuildMeta {
    var version: String = "unknown"
    private var isNativeImage: Boolean = false

    fun loadBuildMeta(configuration: YamlConfiguration) {
        this.version = configuration.getString("maven.version")!!
        this.isNativeImage = configuration.getString("env.native-image", "false").equals("true", ignoreCase = true)
    }

    override fun equals(o: Any?): Boolean {
        if (o === this) return true
        if (o !is BuildMeta) return false
        if (!o.canEqual(this as Any)) return false
        val `this$version`: Any = this.version
        val `other$version`: Any = o.version
        if (`this$version` != `other$version`) return false
        return true
    }

    protected fun canEqual(other: Any?): Boolean {
        return other is BuildMeta
    }

    override fun hashCode(): Int {
        val PRIME = 59
        val result = 1
        val version = this.version
        return result * PRIME + version.hashCode()
    }

    override fun toString(): String {
        return "BuildMeta(version=" + this.version + ")"
    }
}
