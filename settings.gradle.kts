pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
    }
}

plugins {
    id("gg.meza.stonecraft") version "1.15.1"
    id("dev.kikugie.stonecutter") version "0.10"
}

stonecutter {
    shared {
        fun mc(version: String, vararg loaders: String) {
            // Crea los targets "1.21.1-fabric", "1.21.1-neoforge", etc.
            for (it in loaders) version("$version-$it", version)
        }

        // Versiones ancla. Añade o quita líneas según lo que quieras soportar
        // (cada versión necesita su fichero en versions/dependencies/).
        mc("1.20.1", "fabric", "forge")
        mc("1.21.1", "fabric", "neoforge")
        mc("1.21.11", "fabric", "neoforge")

        // Versión "activa" con la que está escrito el código fuente en el repositorio.
        vcsVersion = "1.21.11-fabric"
    }
    create(rootProject)
}

rootProject.name = "Stormroot"
