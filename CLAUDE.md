# Stormroot · mod de Minecraft (multiloader / multiversión)

Frentes climáticos que avanzan por el mundo y cambian lo que hay bajo tierra. El diseño completo está en `README.md`.

## Objetivo
- Targets (todas versiones ofuscadas; NO hay versiones 26.x en este proyecto):
  | Target | Jar válido en | Loaders | Java |
  |----|----|---------|------|
  | 1.20.1 | 1.20.1 | Forge, Fabric | 17 |
  | 1.21.1 | 1.21 – 1.21.1 | NeoForge, Fabric | 21 |
  | 1.21.11 | 1.21.11 | NeoForge, Fabric | 21 |
- **Quilt** = el jar de Fabric probado en Quilt Loader (sin QSL).
- **NeoForge 1.20.1** = el jar de Forge 1.20.1 (fork de Forge 47.1). No uses APIs de Forge posteriores a 47.1 en el código de Forge. Se publica también con la etiqueta `neoforge` (`publishMods` en `build.gradle.kts`).
- Rangos de Minecraft de cada jar: `mc_range_fabric` / `mc_range_forge` en `versions/dependencies/<versión>.properties`, **siempre con límite superior**.

## Estructura
- Proyecto **Stonecraft + Stonecutter** con UN solo `src/main` para todos los targets.
  - Versión: `//? if >=1.21 {` … `//?} else {` … `//?}`
  - Loader: `/*? if fabric {*/` … `/*?}*/` (los bloques inactivos quedan comentados con `/* … */`, así que dentro de ellos NO puede haber comentarios de bloque).
  - El código del repo está escrito para la versión activa `1.21.11-fabric` (`vcsVersion` en `settings.gradle.kts`).
  - `ResourceLocation` → se escribe `Identifier` y `build.gradle.kts` lo traduce en versiones < 1.21.11.
- Paquete base: `io.github.hcuadrado428.stormroot`
  - `Stormroot.java`: SOLO pegamento de cada loader (registro y eventos).
  - `weather/FrontType`, `WeatherFront`, `FrontSimulation`: **Java puro**, sin imports de Minecraft ni de loaders. Mantenerlo así.
  - `weather/FrontTicker`: puente vanilla entre Minecraft y la simulación. Llama cada paso a `seep/SeepageTicker`.
  - `seep/SeepageCell`, `SeepageRates`, `SeepageField`: **Java puro** (filtración). `SeepageTicker` y `SeepageSavedData` usan Minecraft.
  - La filtración solo cambia bloques que puede deshacer: cada cambio se apunta en la celda y se revierte solo si el bloque sigue igual.
  - `registry/`: creación de items/bloques (común); el registro lo hace cada loader en `Stormroot.java`.
- Datos con formato distinto por versión (recetas, loot tables, tags) → `src/versioned/<1.20.1|1.21.1|1.21.11>/resources`
  (1.20.1: carpetas en plural y `{"item": ...}`; 1.21.1: singular y resultado `{"id": ...}`; 1.21.11: ingredientes como texto `"minecraft:x"` / `"#tag"`).
  La CI comprueba que cada jar los lleva. El validador del plugin avisa del plural en 1.20.1: es correcto ahí.
- Bloques: `registry/ModBlocks` + `block/`. `FACING` es `EnumProperty<Direction>` desde 1.21.2 (`DirectionProperty` antes);
  interacción con `useWithoutItem` desde 1.20.5 (`use` en 1.20.1).
- Texturas: spec en `art/textures/<nombre>.json` → PNG en `src/main/resources/assets/stormroot/textures/...` con `texgen.py`.
- Mappings: **Mojang (official)** en todos los loaders. No usar Yarn.

## Comandos
- Compilar todo: `./gradlew buildAndCollect` (Windows: `gradlew.bat buildAndCollect`) → jars en `build/libs/`
- Cambiar target activo: `./gradlew "Set active project to 1.21.1-neoforge"` (o panel de Gradle → stonecutter)
- Probar: `runClient`, `runServer`, `runGameTestServer` del target activo
- Texturas: skill `minecraft-modding:pixel-art-16x16`

## Reglas de oro
1. **Nunca inventes una API.** Antes de usar una clase/método, compruébalo en las fuentes decompiladas de ESA versión (`./gradlew genSources` en Fabric; las fuentes de NeoForge/Forge se adjuntan en el IDE) o en la doc oficial.
2. Consulta la skill `minecraft-modding:version-matrix` antes de escribir código que dependa de la versión, y la skill temática que toque (`worldgen`, `datagen`, `gui-screens`, `networking`, `geckolib`, `mod-config`, `gametests`, `publishing`).
3. Un cambio no está terminado hasta que `./gradlew buildAndCollect` pasa en **los 6 targets** y el self-test de la CI
   está en verde. Gradle se ejecuta con Java 25.
4. Lógica nueva → en clases sin imports de loader. Si necesitas algo del loader, crea una interfaz común e impleméntala por loader con bloques Stonecutter.
5. Texturas: 16×16 PNG RGBA, revisa siempre la preview ampliada antes de darla por buena.
6. Delega en los subagentes del plugin: compilación rota → `minecraft-modding:build-fixer`; portar → `minecraft-modding:mod-porter`; mixins → `minecraft-modding:mixin-reviewer`; dudas de API → `minecraft-modding:api-researcher`; texturas → `minecraft-modding:texture-artist`; modelos 3D → `minecraft-modding:model-artist`.

## Estado
- Hecho: item `lightning_crystal`, simulación de frentes, `FrontSavedData` (guardado), clima vanilla del servidor
  controlado por frentes, comandos `/stormroot`, cristales con los rayos (`LightningCrystals`), red
  (`network/FrontsSyncPayload`, `StormrootNetwork`) y clima por zonas en el cliente (`client/ClientWeather`).
- SavedData: 1.20.1 `computeIfAbsent(load, new, nombre)`; 1.21.1 `SavedData.Factory`; 1.21.11 `SavedDataType` + Codec.
- Red: Fabric 1.20.1 `PacketByteBufs`; Fabric 1.21+ `PayloadTypeRegistry.playS2C()`; Forge 1.20.1 `SimpleChannel`
  con `acceptMissingOr`; NeoForge `RegisterPayloadHandlersEvent` + `.optional()`.
- Permisos de comandos: en 1.21.11 `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`; antes `source.hasPermission(2)`.
- Código de cliente: SOLO en `client/`. `ClientFronts` es Java puro (lo llaman los handlers de red en ambos lados).
- Niebla: Forge/NeoForge con `ViewportEvent.RenderFog` (cancelado hasta 1.21.5; desde 1.21.6 solo se modifica `FogData`);
  Fabric con `client/mixin/FogRendererMixin` (`stormroot.mixins.json`, solo lo declara `fabric.mod.json`): TAIL de
  `FogRenderer.setupFog` + `RenderSystem` hasta 1.21.1; `@Local FogData` antes de `RenderSystem.getDevice()` en 1.21.6+.
  Todos llaman a `ClientWeather.markFogApplied()` (el self-test lo comprueba).
- Rayos: `LightningCrystals.onEntityAdded` devuelve `true` para cancelar rayos naturales fuera de los frentes de tormenta
  (Forge/NeoForge cancelan el evento; Fabric hace `discard()`).
- `/weather`: Stormroot siempre pone duraciones ≤ `STEP_TICKS*2`; si el mundo tiene una mayor, es un `/weather` y se espera
  (`FrontTicker.weatherOverridden`). Ajustes del mundo en `config/WorldSettings` (guardados en `FrontSavedData`).
- Mod Menu (solo Fabric, `modCompileOnly`, versión en `versions/dependencies/*.properties`): `client/ModMenuIntegration`,
  excluida al compilar Forge/NeoForge.
- HUD/texto: colores siempre ARGB (`0xFFFFFFFF`); desde 1.21.6 un color sin alfa no se ve.
- **Bloomberto** (librería nuestra, opcional): integración en `compat/bloomberto/`, cargada por reflexión desde
  `compat/Compat` solo si el mod está instalado. Solo se compila si existe `libs/bloomberto/bloomberto-*+<target>.jar`
  (`scripts/fetch-bloomberto.sh`, commit en `bloomberto.ref`). Desde Bloomberto `0e64d37` los 6 targets tienen integración.
  Si un commit de Bloomberto no tiene algún target, el script avisa y ese target compila sin ella.
  NUNCA importes clases de Bloomberto fuera de `compat/bloomberto/`. Los ajustes se leen siempre de `config/StormrootConfig`.
  API de Bloomberto: `docs/es/uso.md` de su repo; nivel de API probado: 2 (`BloombertoCompat.API_LEVEL`).
- Filtración v2 hecha: charcos encerrados que crecen, pozas, barro, musgo, grietas (`seep/SeepageTicker`, umbrales
  `MUD_AT`…`DROUGHT_AT`), materiales permitidos y su restauración en `seep/CaveMaterials`; goteo y polvo en el cliente;
  raíces de rayo (`weather/LightningRoots`). El agua solo se coloca si queda encerrada por bloques completos (`isWall`).
  Orden: tierra → barro → charco; un bloque tiene como mucho UN cambio apuntado y siempre guarda el material de antes
  de Stormroot (`place`). Al secarse: charcos, luego pozas, luego barro.
  `getMinY()` desde 1.21.2 (`getMinBuildHeight()` antes).
- **Cielo propio (fase 1 hecha)**: `sky/CloudCover` + `sky/ValueNoise` (Java puro y deterministas: el servidor podrá
  usarlos en la fase 2) deciden la nube de cada celda de 12 bloques; `client/sky/SkyRenderer` la dibuja desde
  `client/render/WorldRenderHook` (eventos de render de cada loader en `StormrootClient`, igual que `WorldRenderBridge`
  de Bloomberto). Las vanilla se ocultan con `client/mixin/sky/OptionsCloudsMixin` (`Options#getCloudsType` → OFF),
  en `stormroot-sky.mixins.json`, que cargan los 3 loaders (Fabric `fabric.mod.json`, NeoForge `[[mixins]]`, Forge
  `loom.forge.mixinConfig` en `build.gradle.kts`). Opción `custom_clouds` (Bloomberto) o `config/stormroot-client.properties`
  (`config/ClientSettingsFile`).
- **Cielo, fase 2 hecha**: semilla del cielo por mundo (`sky/SkySeed`, en el paquete). Lluvia solo bajo los nubarrones:
  `sky/SkyWeather` + `mixin/BiomePrecipitationMixin` (`stormroot-common.mixins.json`, 3 loaders) sobre
  `Biome#getPrecipitationAt` (`(BlockPos)` hasta 1.21.1, `(BlockPos, int seaLevel)` en 1.21.11), solo en los hilos
  principales. Tormenta seca: `weather/DryStorms` (rayos con daño; `mixin/LightningBoltFireMixin` cancela su `spawnFire`) y fuego del clima `block/StormFireBlock` (solo hojas no persistentes, `SPREAD` ≤ 4, `AGE` 15; render cutout: Fabric `BlockRenderLayerMap`, Forge/NeoForge `render_type` del modelo). La niebla del cliente se deshace en biomas sin lluvia. Frentes por bioma:
  `weather/ClimateZone` (pesos) + `weather/BiomeClimate`. Niebla: humedece hasta `SeepageCell.FOG_CAP`. Cliente:
  sombras, aviso de tormenta (`CloudCover.approach`), capa alta, niebla que rueda (`ClientWeather.spawnRollingFog`).
- **Ventisca y cuevas de hielo**: `FrontType.BLIZZARD` (pesos en `ClimateZone`), `weather/BlizzardSnow` (capas hasta 4),
  `SeepageCell.frost` (guardado; `updateFrost`, deshielo lento/masivo) y efectos en `SeepageTicker.freeze` (tipos
  `SNOW_FLOOR`, `ICE_FLOOR`, `FROZEN`, `ICICLE`, `ICE_CRYSTAL`, reversibles), bloques `block/IcicleBlock` (cae con luz
  de bloque ≥ 7) e `IceCrystalBlock`, peligros en `seep/ColdCaves` (congelación vanilla `setTicksFrozen`, strays).
- **Ola de calor**: `FrontType.HEAT_WAVE` (no precipita; `CloudCover.heat`/`fairBias` despejan el cielo),
  `SeepageCell.applyHeat` (seca más rápido), deshielo masivo también con calor, `weather/HeatWaves` (hambre al sol de
  día con `causeFoodExhaustion`, fuego del clima a mediodía).
- **Granizo**: `CloudCover.isHailStorm` (tormentas con id múltiplo de 3; núcleo con intensidad ≥ `HAIL_CORE` → `Kind.HAIL`),
  `weather/Hail` (daño sin casco con `hurt`/`hurtServer`, cultivos `CropBlock`, cristal según `WorldSettings.hailBreaksGlass`,
  item `hailstone` y receta 4 → hielo), partículas en `ClientWeather.spawnHail`.
- **Monzón**: `FrontType.MONSOON` (húmedos y océanos; moja ×3 en `rainAt`), `weather/Monsoons` (cultivos con `randomTick`
  extra), en `SeepageTicker` orillas `FLOOD` (suelo natural junto a agua quieta → agua, hasta 2 bloques) y cascadas
  `CASCADE` (agua bajo el techo de la cueva), reversibles.
- **Raros**: `FrontType.AURORA` (fríos y cumbres; cortinas en `SkyRenderer.drawAurora`, polvo `aurora_dust`) y
  `FrontType.MAGNETIC_STORM` (todos los biomas, peso 0,06; `InstrumentBlock` da potencia al azar; `weather/RareSkies`
  magnetiza hierro → `block/MagneticRockBlock`, loot = hierro en bruto).
- **Arcoíris**: `sky/Rainbow` (registro puro, va en el paquete), `weather/Rainbows` (al acabarse una lluvia cerca de un
  jugador, de día, 35 %; recompensa al llegar al extremo; no se guarda), dibujo en `SkyRenderer.drawRainbows`.
- **Red de micelio** (`mycel/`): `MyceliumRules` y `MyceliumNetworks` son Java puro (reglas; chunks de cada red, máx. 30,
  guardados en `SeepageSavedData` como `mycelium`). `Mycelium` (mundo): nace con `tick`/`tryBirth`, crece en los ticks
  aleatorios de `block/MyceliumVeinBlock` (extiende `GlowLichenBlock`: caras con `MultifaceBlock.getFaceProperty`) y
  `MyceliumNodeBlock`; solo bajo `HostBiomes` (pantano, manglar, champiñones; bioma a y=64) y bajo tierra. Es permanente
  (no pasa por la filtración ni se apunta en las celdas). Luz de bloque ≥ 7 → el hilo se retira; `killIfOrphan` mata los
  hilos sin nodo. `SporeMist`: niebla ≥ 0,3 en esos biomas → náusea (`MobEffects.NAUSEA` en 1.21.11, `CONFUSION` antes),
  setas con `randomTick`. Self-test en `selftest/MyceliumSelfTest` (túnel propio en y -57..-54).
- **Helada**: `weather/FrostRules` (Java pura; noche y zona → hiela, determinista con la semilla del cielo; la usa también
  el cliente para la escarcha) y `weather/Frost` (agua de 1 bloque → `block/FrostIceBlock`, que se derrite al acabar;
  cultivos parados con `mixin/CropFrostMixin` salvo luz de bloque ≥ 7). La aurora y la tormenta magnética no tapan el cielo.
- Brújulas en la tormenta magnética: `client/mixin/sky/MagneticCompassMixin` sobre `isValidCompassTargetPos`
  (`CompassItemPropertyFunction` hasta 1.21.1, `CompassAngleState` estático en 1.21.11) + `client/MagneticCompass`.
- Aurora: `weather/AuroraCalm` impide el 80 % de los hostiles al aire libre de noche (junto a `LightningCrystals.onEntityAdded`).
- **Viento**: `weather/WindRules` (Java pura: fuerza por frentes, resguardo, aceleración de proyectiles tras
  `GRACE_TICKS`), `weather/Wind` (mundo; `Source` del servidor = simulación, del cliente = `client/ClientWind` con el
  paquete) y `mixin/ProjectileWindMixin` (HEAD de `Projectile#tick`, ambos lados). Tipos por `EntityType` (las clases de
  proyectiles cambian de paquete en 1.21.11). Ajustes `wind_projectiles`/`wind_player_shots` en `WorldSettings`,
  `FrontSavedData`, Bloomberto y en el paquete (`windFlags`). Elytras y barcos (`AbstractBoat` en 1.21.11, `Boat` antes)
  en `ClientWind`.
- **Tormenta de arena**: `FrontType.SANDSTORM` (solo `ClimateZone.DRY`), `CloudCover.Kind.DUST` (muro bajo en
  `SkyRenderer`, `DUST_Y`; no llueve debajo: `SkyWeather.noRainCloudAt`), `CloudCover.calima` (nubes anaranjadas y polvo
  fuera de los biomas secos), `weather/Sandstorms` (capas `block/SandLayerBlock` extiende `SnowLayerBlock`, se van en
  sus ticks aleatorios; ceguera `MobEffects.BLINDNESS`), `item/SandGogglesItem` (`Equipable` hasta 1.21.1,
  `Item.Properties.equippable(HEAD)` desde 1.21.2), niebla y arena en `ClientWeather.spawnSand`.
- **Mobs** (`entity/`, primero: `Sporeling`, esporín de la red de micelio). Tipo en `entity/ModEntities`
  (`EntityType.Builder.build(String)` hasta 1.21.1, `build(ResourceKey)` desde 1.21.2); registro en `Stormroot.java`
  (Fabric `Registry.register` + `FabricDefaultAttributeRegistry`; Forge/NeoForge `RegisterEvent` + `EntityAttributeCreationEvent`
  en el bus del mod). Cliente en `client/entity/`: modelo `HierarchicalModel<E>` hasta 1.21.1 y `EntityModel<LivingEntityRenderState>`
  desde 1.21.2 (`setupAnim(state)`); renderer `MobRenderer<E, M>` / `MobRenderer<E, S, M>` con `createRenderState`.
  Registro: Fabric `EntityModelLayerRegistry` + `EntityRendererRegistry`; Forge/NeoForge `EntityRenderersEvent`
  (`StormrootClient.register(modBus)` en NeoForge). Aparición propia (no por bioma) en `Mycelium.maybeSpawnSporeling`
  con `EntityType.spawn(level, pos, EntitySpawnReason/MobSpawnType)`. Sin huevos de generación aún (`/summon stormroot:sporeling`).
  Plan: tejedores (neutrales, reparan la red) y guardián de micelio (mob fuerte, sin barra de jefe; al vencerlo la red
  se calma; da el corazón de micelio para domesticar redes).
- `research` también saca JSON de datos y recursos vanilla (loot tables, modelos) de los jars del juego.
- Fuentes decompiladas desde la CI: `gh api -X POST repos/HCuadrado428/stormroot/actions/workflows/research.yml/dispatches`
  con `inputs[classes]` y `inputs[pattern]` (workflow `research`).
- Compila en los 6 targets y **el self-test pasa dentro del juego en los 6** (servidor: 53 comprobaciones; cliente con
  un jugador en pantalla virtual: 17 más, incluidas las de Bloomberto y las del cielo).
- En el self-test, el clima se lee de `server.getWorldData().overworldData()`: `level.isRaining()/isThundering()` son la
  intensidad visual, que sube tick a tick. Lo visual (niebla, partículas, texturas) aún no se ha mirado a ojo.
- **Self-test** (`selftest/SelfTest`, `client/ClientSelfTest`, `scripts/selftest.sh`, job `selftest` de la CI): cuando
  añadas una mecánica, añade su comprobación. Construye su propia zona en el chunk (10,10) de un mundo plano.
  Ya encontró 2 fallos reales de la filtración (charcos que tapaban el barro; bloques que no volvían a su material).
- Siguientes pasos: ver "Siguientes pasos" en `README.md`.

## Mod
- modid: `stormroot`
- paquete base: `io.github.hcuadrado428.stormroot`
- nombre: `Stormroot`
