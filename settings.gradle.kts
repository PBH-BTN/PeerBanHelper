rootProject.name = "peerbanhelper"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "ej-technologies"
            url = uri("https://maven.ej-technologies.com/repository")
        }
        maven {
            name = "spring-snapshots"
            url = uri("https://repo.spring.io/snapshot")
        }
    }
    plugins {
        kotlin("jvm") version "2.3.0"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
