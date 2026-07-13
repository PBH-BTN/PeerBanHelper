plugins {
    java
    application
    id("com.gorylenko.gradle-git-properties") version "4.0.1"
    id("com.install4j.gradle") version "13.0.1" apply false
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm")
    kotlin("plugin.lombok") version "2.4.0"
    id("io.freefair.lombok") version "9.5.0"
}

group = "com.ghostchu.peerbanhelper"
version = "9.4.1"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}


repositories {
    mavenCentral()
    maven {
        name = "spring-snapshots"
        url = uri("https://repo.spring.io/snapshot")
        mavenContent {
            snapshotsOnly()
        }
    }
    maven {
        name = "reposilite-repository-releases"
        url = uri("https://maven.sergeybochkov.com/releases")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "maven-snapshots"
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        name = "reposilite-repository"
        url = uri("https://maven.reposilite.com/snapshots")
    }
}

val flatlafVersion = "3.7.2"
val nettyVersion = "4.2.16.Final"
val sqliteVersion = "3.53.2.0"
val springVersion = "7.0.8"

configurations.all {
    exclude(group = "commons-logging", module = "commons-logging")
}

dependencyManagement {
    imports {
        mavenBom("com.baomidou:mybatis-plus-bom:3.5.17")
    }
}
dependencies {
    // Spring Framework
    implementation("org.springframework:spring-context:${springVersion}"){
        exclude(group="commons-logging", module="commons-logging")
    }
    // Database
    implementation("com.github.chris2018998:beecp:5.2.2")
    implementation("org.springframework:spring-tx:${springVersion}")
    implementation("org.springframework:spring-jdbc:${springVersion}")
    implementation("org.xerial:sqlite-jdbc:${sqliteVersion}")
    implementation("org.xerial:sqlite-jdbc:${sqliteVersion}:natives-android")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.mysql:mysql-connector-j:9.7.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }

    // MyBatis-Plus Stuff
    implementation("com.baomidou:mybatis-plus-jsqlparser")
    implementation("com.baomidou:mybatis-plus-annotation")
    implementation("com.baomidou:mybatis-plus-core")
    implementation("com.baomidou:mybatis-plus-extension")
    implementation("com.baomidou:mybatis-plus-spring")

    implementation("org.mybatis:mybatis-spring:4.1.0")

    // Annotations
    implementation("org.flywaydb:flyway-core:11.20.3")
    implementation("org.flywaydb:flyway-mysql:11.20.3")
    implementation("org.flywaydb:flyway-database-postgresql:11.20.3")
    compileOnly("org.jetbrains:annotations:26.1.0")

    // Core dependencies
    implementation("com.vdurmont:semver4j:3.1.0")
    implementation("io.javalin:javalin:7.2.2")
    // GeoIP
    implementation("com.maxmind.geoip2:geoip2:5.1.0")
    // Expression engine
    implementation("com.googlecode.aviator:aviator:5.4.3")

    // Email
    implementation("org.eclipse.angus:angus-mail:2.0.5")
//    // System monitoring
//    implementation("com.github.oshi:oshi-core:6.12.0") {
//        exclude(group = "net.java.dev.jna", module = "jna-platform")
//        exclude(group = "net.java.dev.jna", module = "jna")
//    }
//    // System monitoring for supported platforms
    implementation("com.github.oshi:oshi-common:7.4.0")
    runtimeOnly("com.github.oshi:oshi-core-ffm:7.4.0")
    // Markdown
    implementation("org.commonmark:commonmark:0.29.0")
    // Compression
    implementation("org.tukaani:xz:1.12")
    // DNS
    implementation("dnsjava:dnsjava:3.6.5")
    // UI - FlatLaf
    implementation("com.formdev:flatlaf-extras:$flatlafVersion")
    implementation("com.formdev:flatlaf:$flatlafVersion")
    // Reload library
    implementation("com.ghostchu:simplereloadlib:1.1.2")
    // Utilities
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.guava:guava:33.6.0-jre")
    implementation("com.github.seancfoley:ipaddress:5.6.2")
    implementation("org.bspfsystems:yamlconfiguration:3.0.4")
    implementation("org.apache.commons:commons-collections4:4.5.0")
    // CSV
    implementation("de.siegmar:fastcsv:4.3.1")
    // Jackson
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.1"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Plugin framework
    implementation("org.pf4j:pf4j-spring:0.10.0") {
        exclude(group = "log4j", module = "log4j")
        exclude(group = "org.apache.logging.log4j", module = "log4j-api")
        exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
        exclude(group = "ch.qos.reload4j", module = "reload4j")
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
        exclude(group = "commons-lang", module = "commons-lang")
    }

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.38")
    implementation("org.slf4j:jcl-over-slf4j:2.0.18")

    // Async utilities
    implementation("com.spotify:completable-futures:0.3.6")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")

    // Netty
    implementation("io.netty:netty-transport:${nettyVersion}")
    compileOnly("io.netty:netty-transport-native-epoll:$nettyVersion")
    runtimeOnly("io.netty:netty-transport-native-epoll:$nettyVersion:linux-x86_64")
    runtimeOnly("io.netty:netty-transport-native-epoll:$nettyVersion:linux-aarch_64")
    runtimeOnly("io.netty:netty-transport-native-epoll:$nettyVersion:linux-riscv64")
    compileOnly("io.netty:netty-transport-native-io_uring:${nettyVersion}")
    runtimeOnly("io.netty:netty-transport-native-io_uring:$nettyVersion:linux-x86_64")
    runtimeOnly("io.netty:netty-transport-native-io_uring:$nettyVersion:linux-aarch_64")
    runtimeOnly("io.netty:netty-transport-native-io_uring:$nettyVersion:linux-riscv64")
    compileOnly("io.netty:netty-transport-native-kqueue:${nettyVersion}")
    runtimeOnly("io.netty:netty-transport-native-kqueue:${nettyVersion}:osx-x86_64")
    runtimeOnly("io.netty:netty-transport-native-kqueue:${nettyVersion}:osx-aarch_64")

    // SWT (provided scope - for compilation only)
    compileOnly("org.eclipse.platform:org.eclipse.swt.win32.win32.x86_64:3.134.0")

    // install4j stuff
    compileOnly("com.install4j:install4j-runtime:13.0.1")

    implementation(platform("io.sentry:sentry-bom:8.48.0")) //import bom
    implementation("io.sentry:sentry")
    implementation("io.sentry:sentry-logback")
    implementation("io.sentry:sentry-jdbc")

    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("p6spy:p6spy:3.9.1")
    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")

}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("com.ghostchu.peerbanhelper.MainJumpLoader")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Use inputs.properties to make expand work with configuration cache
    val expandProps = mapOf(
        "version" to project.version.toString(),
        "group" to project.group.toString(),
        "name" to project.name
    )
    inputs.properties(expandProps)

    val sentryDsn = System.getenv("JAVA_SENTRY_DSN") ?: ""
    filesMatching("sentry.properties") {
        expand(mapOf("JAVA_SENTRY_DSN" to sentryDsn))
    }

    from("src/main/resources") {
        exclude("assets/", "static/", "native/", "lang/")
    }

    from("src/main/resources/static") {
        exclude(".git/")
        into("static")
    }

    from("src/main/resources/assets") {
        exclude(".git/")
        into("assets")
    }

    from("src/main/resources/native") {
        into("native")
    }

    from("src/main/resources/lang") {
        exclude(".git/")
        into("lang")
    }

    from("src/main/resources/mapper") {
        into("mapper")
    }
}

tasks.jar {
    archiveBaseName.set("PeerBanHelper")
    archiveVersion.set("")

    manifest {
        attributes(
            "Main-Class" to "com.ghostchu.peerbanhelper.MainJumpLoader",
            "Class-Path" to configurations.runtimeClasspath.get().files.joinToString(" ") { "libraries/${it.name}" } + " libraries/swt.jar",
            "Enable-Native-Access" to "ALL-UNNAMED"
        )
    }
}

// Task to copy dependencies (equivalent to maven-dependency-plugin)
tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("libraries"))
}

// Make jar depend on copyDependencies
tasks.jar {
    dependsOn("copyDependencies")
}

tasks.compileJava {
    options.compilerArgs.addAll(
        listOf(
            "-parameters"
        )
    )
}

// Git properties configuration - disabled temporarily
gitProperties {
    dotGitDirectory.set(file("${project.rootDir}/.git"))
}

// Install4j configuration
// To build installers with install4j:
// - For CI: ./gradlew compileInstall4jCI -Pinstall4j.home=/path/to/install4j
// - For Dev: ./gradlew compileInstall4jDev -Pinstall4j.home=/path/to/install4j

// Install4j CI task (equivalent to install4j-ci profile)
tasks.register<Exec>("compileInstall4jCI") {
    group = "build"
    description = "Compile install4j installers for CI"
    dependsOn("jar")

    val install4jHome = project.findProperty("install4j.home") as String?
        ?: System.getenv("INSTALL4J_HOME")
        ?: "/opt/install4j"

    commandLine(
        "$install4jHome/bin/install4jc",
        "--release=${project.version}",
        "-g",
        "-d", layout.buildDirectory.dir("media").get().asFile.absolutePath,
        "--var-file=${project.projectDir}/install4j/project.install4j.vmoptions",
        "-D", "librariesPath=${layout.buildDirectory.dir("libraries").get().asFile.absolutePath}",
        "-D", "jarPath=${layout.buildDirectory.file("libs/PeerBanHelper.jar").get().asFile.absolutePath}",
        "install4j/project.install4j"
    )

    isIgnoreExitValue = true

    doFirst {
        if (!file(install4jHome).exists()) {
            throw GradleException("Install4j not found at: $install4jHome. Please set install4j.home property or INSTALL4J_HOME environment variable.")
        }
    }
}

// Install4j Dev task (equivalent to install4j-dev profile)
tasks.register<Exec>("compileInstall4jDev") {
    group = "build"
    description = "Compile install4j installers for development"
    dependsOn("jar")

    val install4jHome = project.findProperty("install4j.home") as String?
        ?: System.getenv("INSTALL4J_HOME")
        ?: "/opt/install4j"

    commandLine(
        "$install4jHome/bin/install4jc",
        "--release=${project.version}",
        "-g",
        "-d", layout.buildDirectory.dir("media").get().asFile.absolutePath,
        "-D", "librariesPath=${layout.buildDirectory.dir("libraries").get().asFile.absolutePath}",
        "-D", "jarPath=${layout.buildDirectory.file("libs/PeerBanHelper.jar").get().asFile.absolutePath}",
        "install4j/project.install4j"
    )

    environment("INSTALL4J_JVM_ARGS", "-Xmx5G")
    isIgnoreExitValue = true

    doFirst {
        if (!file(install4jHome).exists()) {
            throw GradleException("Install4j not found at: $install4jHome. Please set install4j.home property or INSTALL4J_HOME environment variable.")
        }
    }
}
kotlin {
    jvmToolchain(25)
}
