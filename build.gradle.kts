plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.sasha"
version = "1.25.0"
description = "Capture the Flag for Fractal Network: hub voting, kits, CTF, 4-Team CTF, King of the Hill and Destroy the Wool. Built by Sasha Wildfang."

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.257-stable")
    // Optional: rank prefixes from FractalRanks (LuckPerms).
    compileOnly("net.luckperms:api:5.4")

    // Bundled: the database pool and driver (both CTF servers share one MySQL database).
    implementation("com.zaxxer:HikariCP:6.3.0") {
        exclude(group = "org.slf4j") // Paper already has it
    }
    implementation("com.mysql:mysql-connector-j:9.4.0") {
        exclude(group = "com.google.protobuf") // only for MySQL's X DevAPI
    }

    testImplementation("io.papermc.paper:paper-api:26.3.build.257-stable")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked", "-Xlint:removal"))
    }

    processResources {
        val props = mapOf("version" to project.version, "description" to project.description)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveClassifier = "plain"
    }

    shadowJar {
        archiveClassifier = ""
        relocate("com.zaxxer.hikari", "com.sasha.ctf.lib.hikari")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
