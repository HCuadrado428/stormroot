import gg.meza.stonecraft.mod

plugins {
    id("gg.meza.stonecraft")
}

modSettings {
    clientOptions {
        fov = 90
        guiScale = 3
        narrator = false
        darkBackground = true
        musicVolume = 0.0
    }

    variableReplacements =
        mapOf(
            "minecraftVersionVirtual" to stonecutter.current.version,
            // Rango de Minecraft con límite superior (versions/dependencies/<versión>.properties)
            "mcRangeFabric" to project.mod.prop("mc_range_fabric"),
            "mcRangeForge" to project.mod.prop("mc_range_forge"),
            "forgeLoaderVersion" to
                if (project.mod.isForge) {
                    project.mod
                        .prop("forge_version")
                        .substringAfter("-")
                        .substringBefore(".")
                } else {
                    ""
                },
        )
}

stonecutter {
    // En 1.21.11 Mojang renombró ResourceLocation -> Identifier.
    // El código se escribe con Identifier y Stonecutter lo traduce en versiones anteriores.
    replacements.string(stonecutter.current.parsed < "1.21.11") {
        replace("Identifier", "ResourceLocation")
    }
}

// Recursos cuyo formato o carpeta cambia entre versiones (recetas, loot tables, tags):
// src/versioned/<versión de Minecraft>/resources se añade solo al target de esa versión.
extensions.getByType<SourceSetContainer>().named("main") {
    resources.srcDir(rootProject.file("src/versioned/${stonecutter.current.version}/resources"))
}

// Mod Menu (opcional, solo Fabric): botón de config en la lista de mods. Solo para compilar; no se incluye.
repositories {
    maven("https://maven.terraformersmc.com/releases/") {
        content { includeGroup("com.terraformersmc") }
    }
}
if (mod.isFabric) {
    dependencies {
        "modCompileOnly"("com.terraformersmc:modmenu:${project.mod.prop("modmenu_version")}")
    }
} else {
    extensions.getByType<SourceSetContainer>().named("main") {
        java.exclude("**/client/ModMenuIntegration.java")
    }
}

// Bloomberto (opcional). Si existe libs/bloomberto/bloomberto-*+<target>.jar (scripts/fetch-bloomberto.sh),
// se compila la integración (paquete compat.bloomberto) y Bloomberto se carga en runClient/runServer.
// Si no existe (p. ej. 1.21.11, que Bloomberto no soporta), esa integración se excluye y el mod compila igual.
val bloombertoJar: File? =
    rootProject
        .file("libs/bloomberto")
        .listFiles()
        ?.firstOrNull { it.name.startsWith("bloomberto-") && it.name.endsWith("+${stonecutter.current.project}.jar") }

if (bloombertoJar != null) {
    logger.lifecycle("[stormroot] ${stonecutter.current.project}: integración con Bloomberto (${bloombertoJar.name})")
    dependencies {
        "modCompileOnly"(files(bloombertoJar))
        "modLocalRuntime"(files(bloombertoJar))
    }
} else {
    extensions.getByType<SourceSetContainer>().named("main") {
        java.exclude("**/compat/bloomberto/**")
    }
}

// Mixins de los 3 loaders: stormroot-sky (cliente: ocultar las nubes vanilla) y stormroot-common (lluvia solo bajo
// los nubarrones). Fabric: fabric.mod.json; NeoForge: [[mixins]] de neoforge.mods.toml; Forge 1.20.1: aquí.
if (mod.isForge) {
    extensions.getByType<net.fabricmc.loom.api.LoomGradleExtensionAPI>().forge
        .mixinConfig("stormroot-sky.mixins.json", "stormroot-common.mixins.json")
}

publishMods {
    // NeoForge 1.20.1 es un fork de Forge 47.1: el jar de Forge 1.20.1 también funciona ahí.
    // Stonecraft ya añade "forge"; aquí sumamos "neoforge" para que aparezca en ese filtro.
    if (mod.isForge && stonecutter.current.version == "1.20.1") {
        modLoaders.add("neoforge")
    }

    modrinth {
        if (mod.isFabric) requires("fabric-api")
        environment.set(CLIENT_OR_SERVER_PREFERS_BOTH)
    }

    curseforge {
        client = true
        server = true
        if (mod.isFabric) requires("fabric-api")
    }
}
