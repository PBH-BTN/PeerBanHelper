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
}
