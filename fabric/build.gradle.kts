plugins {
    id("net.fabricmc.fabric-loom")
}

dependencies {
	minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
	compileOnly(files(rootProject.property("fabric_named_minecraft") as String))
	implementation("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
}

sourceSets {
	main {
		resources.srcDir(rootProject.file("common/src/main/resources"))
	}
}
