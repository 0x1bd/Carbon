plugins {
    id("java")
    id("maven-publish")
    id("fabric-loom") version "1.16-SNAPSHOT" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
}

val targetJavaVersion = (property("java_version") as String).toInt()

allprojects {
    version = "${rootProject.property("mod_version")}-${rootProject.property("minecraft_version")}"
    group = rootProject.property("maven_group") as String

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForge"
        }
        maven("https://maven.parchmentmc.org") {
            name = "ParchmentMC"
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    base {
        archivesName.set("${rootProject.property("archives_base_name")}-${project.name}")
    }

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJavaVersion)
    }

    tasks.withType<ProcessResources>().configureEach {
        val replacements = mapOf(
            "mod_id" to rootProject.property("mod_id"),
            "mod_name" to rootProject.property("mod_name"),
            "version" to project.version,
            "minecraft_version" to rootProject.property("minecraft_version"),
            "minecraft_version_range" to rootProject.property("minecraft_version_range"),
            "fabric_loader_version" to rootProject.property("fabric_loader_version"),
            "neoforge_version" to rootProject.property("neoforge_version"),
            "neoforge_loader_version_range" to rootProject.property("neoforge_loader_version_range"),
        )
        inputs.properties(replacements)
        filesMatching(listOf("fabric.mod.json", "META-INF/neoforge.mods.toml")) {
            expand(replacements)
        }
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = "${rootProject.property("archives_base_name")}-${project.name}"
                from(components["java"])
            }
        }
    }
}
