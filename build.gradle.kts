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

publishMods {
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
