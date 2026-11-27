plugins {
    java
}

version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// Use a PBH distribution when this example is copied out of the repository.
val distribution = providers.gradleProperty("pbhHome").orNull?.let { file(it) }
val hostJar = distribution?.resolve("PeerBanHelper.jar") ?: file("../../build/libs/PeerBanHelper.jar")
val libraries = distribution?.resolve("libraries") ?: file("../../build/libraries")

dependencies {
    compileOnly(files(hostJar))
    compileOnly(fileTree(libraries) { include("*.jar") })
    annotationProcessor(fileTree(libraries) {
        include("pf4j-*.jar")
        exclude("pf4j-spring-*.jar")
    })
}

tasks.compileJava {
    if (project != rootProject && distribution == null) dependsOn(":jar")
    options.encoding = "UTF-8"
    options.release.set(25)
}
