plugins {
    java
    application
    id("com.gorylenko.gradle-git-properties") version "2.5.7"
    id("com.install4j.gradle") version "12.0.2" apply false
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm")
    kotlin("plugin.lombok") version "2.3.10"
    id("io.freefair.lombok") version "9.2.0"
}

group = "com.ghostchu.peerbanhelper"
version = "9.3.6"

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

val flatlafVersion = "3.7"
val ormliteVersion = "6.1"

configurations.all {
    exclude(group = "commons-logging", module = "commons-logging")
}

dependencyManagement {
    imports {
        mavenBom("com.baomidou:mybatis-plus-bom:3.5.16")
    }
}
dependencies {
    // Spring Framework
    implementation("org.springframework:spring-context:7.0.4"){
        exclude(group="commons-logging", module="commons-logging")
    }
    // Database
    implementation("org.springframework:spring-aop:7.0.4")
    implementation("org.springframework:spring-tx:7.0.4")
    implementation("org.springframework:spring-jdbc:7.0.4")
    // Source: https://mvnrepository.com/artifact/org.aspectj/aspectjweaver
    implementation("org.aspectj:aspectjweaver:1.9.25.1")
    implementation("org.xerial:sqlite-jdbc:3.51.2.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("com.mysql:mysql-connector-j:9.6.0")
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("com.github.chris2018998:beecp:5.2.1")

    // MyBatis-Plus Stuff
    implementation("com.baomidou:mybatis-plus-jsqlparser")
    implementation("com.baomidou:mybatis-plus-annotation")
    implementation("com.baomidou:mybatis-plus-core")
    implementation("com.baomidou:mybatis-plus-extension")
    implementation("com.baomidou:mybatis-plus-spring")

    implementation("org.mybatis:mybatis-spring:4.0.0")

    // Annotations
    implementation("org.flywaydb:flyway-core:11.20.3")
    implementation("org.flywaydb:flyway-mysql:11.20.3")
    implementation("org.flywaydb:flyway-database-postgresql:11.20.3")
    compileOnly("org.jetbrains:annotations:26.0.2-1")

    // Core dependencies
    implementation("com.vdurmont:semver4j:3.1.0")
    implementation("io.javalin:javalin:6.7.0")
    // GeoIP
    implementation("com.maxmind.geoip2:geoip2:5.0.2")
    // Expression engine
    implementation("com.googlecode.aviator:aviator:5.4.3")

    // System theme detector
    implementation("com.github.Dansoftowner:jSystemThemeDetector:3.9.1") {
        exclude(group = "com.github.oshi", module = "oshi-core")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    // Email
    implementation("org.eclipse.angus:angus-mail:2.0.5")
    // System monitoring
    implementation("com.github.oshi:oshi-core:6.9.3") {
        exclude(group = "net.java.dev.jna", module = "jna-platform")
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    // Markdown
    implementation("org.commonmark:commonmark:0.27.1")
    // Compression
    implementation("org.tukaani:xz:1.11")
    // DNS
    implementation("dnsjava:dnsjava:3.6.4")
    // UI - FlatLaf
    implementation("com.formdev:flatlaf-extras:3.7")
    implementation("com.formdev:flatlaf:$flatlafVersion")
    // Reload library
    implementation("com.ghostchu:simplereloadlib:1.1.2")
    // Utilities
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.google.guava:guava:33.5.0-jre")
    implementation("com.github.seancfoley:ipaddress:5.6.1")
    implementation("org.bspfsystems:yamlconfiguration:3.0.4")

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
    implementation("ch.qos.logback:logback-classic:1.5.31")
    implementation("org.slf4j:jcl-over-slf4j:2.0.17")

    // Async utilities
    implementation("com.spotify:completable-futures:0.3.6")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")

    // JNA
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("net.java.dev.jna:jna-platform:5.18.1")

    // UPnP
    implementation("org.bitlet:weupnp:0.1.4")

    // Netty
    implementation("io.netty:netty-all:4.2.10.Final") {
        exclude(group = "io.netty", module = "netty-codec-memcache")
        exclude(group = "io.netty", module = "netty-codec-redis")
        exclude(group = "io.netty", module = "netty-codec-smtp")
        exclude(group = "io.netty", module = "netty-codec-mqtt")
        exclude(group = "io.netty", module = "netty-codec-stomp")
        exclude(group = "io.netty", module = "netty-codec-protobuf")
        exclude(group = "io.netty", module = "netty-codec-native-quic")
        exclude(group = "io.netty", module = "netty-codec-classes-quic")
        exclude(group = "io.netty", module = "netty-codec-http3")
        exclude(group = "io.netty", module = "netty-codec-haproxy")
        exclude(group = "io.netty", module = "netty-codec-marshalling")
        exclude(group = "io.netty", module = "netty-transport-sctp")
        exclude(group = "io.netty", module = "netty-transport-udt")
        exclude(group = "io.netty", module = "netty-transport-rxtx")


    }

    // SWT (provided scope - for compilation only)
    compileOnly("org.eclipse.platform:org.eclipse.swt.win32.win32.x86_64:3.132.0")

    // install4j stuff
    compileOnly("com.install4j:install4j-runtime:12.0.2")

    implementation(platform("io.sentry:sentry-bom:8.32.0")) //import bom
    implementation("io.sentry:sentry")
    implementation("io.sentry:sentry-logback")
    implementation("io.sentry:sentry-jdbc")

    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("p6spy:p6spy:3.9.1")
    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")

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

    // Don't expand all files, only specific ones if needed
    filesMatching(listOf("**/*.properties")) {
        expand(expandProps)
    }

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
