plugins {
    id("net.neoforged.moddev")
}

sourceSets {
    main {
        java.srcDir(rootProject.file("common/src/main/java"))
        resources.srcDir(rootProject.file("common/src/main/resources"))
    }
}

neoForge {
    version = rootProject.property("neoforge_version") as String

    mods {
        create(rootProject.property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}
