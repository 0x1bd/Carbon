plugins {
    id("fabric-loom")
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    add("mappings", "net.fabricmc:yarn:${rootProject.property("yarn_mappings")}:v2")
    add("modImplementation", "net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
}

sourceSets {
    main {
        resources.srcDir(rootProject.file("common/src/main/resources"))
    }
}
