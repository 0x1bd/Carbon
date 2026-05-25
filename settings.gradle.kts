pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForge"
        }
        gradlePluginPortal()
    }
}

rootProject.name = "Carbon"
include("fabric")
include("neoforge")
