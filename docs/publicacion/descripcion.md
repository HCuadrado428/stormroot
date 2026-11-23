# Stormroot · textos para Modrinth y CurseForge

## Resumen corto (campo "summary")

**ES:** Frentes climáticos que viajan por el mundo: tormentas, ventiscas, monzones, olas de calor, granizo, auroras y tormentas de arena que cambian lo que hay bajo tierra.

**EN:** Weather fronts that travel across your world: storms, blizzards, monsoons, heat waves, hail, auroras and sandstorms that change what lies underground.

## Descripción (ES)

### El clima deja de ser un interruptor

En vanilla, llueve en todo el mundo a la vez. Con **Stormroot**, el clima son **frentes** que nacen, crecen, viajan y se deshacen. Puedes ver la tormenta acercarse por el horizonte, salir corriendo de un nubarrón de granizo o cruzar andando la frontera entre la lluvia y el sol.

- **Nubes propias**: cada nube que ves existe de verdad; solo llueve debajo de los nubarrones. Sombras de nube en el suelo.
- **Diez climas**: lluvia, tormenta (con rayos que de verdad caen bajo el frente), **tormenta seca**, **ventisca**, **monzón**, **ola de calor**, **granizo**, **niebla**, **tormenta de arena**, y los raros: **aurora** y **tormenta magnética**.
- **Los frentes chocan**: calor + lluvia = tormenta; la lluvia deshace la tormenta de arena; dos frentes iguales se funden…
- **El clima cala bajo tierra**: la lluvia filtra y forma charcos, barro y musgo en las cuevas; la sequía los seca y agrieta; la ventisca congela y cuelga carámbanos; el monzón inunda orillas y cuelga cascadas. Todo reversible: el mod nunca rompe tus construcciones.
- **La red de micelio**: bajo pantanos y manglares crece una red viva con sus nodos, sus hilos y sus criaturas (el esporín, la tejedora y el guardián). Puedes destruirla… o domesticarla.
- **Detalles**: viento que desvía flechas, brújulas locas en la tormenta magnética, arcoíris con premio, escarcha nocturna, fósiles que destapa la arena, gafas de arena, veleta y barómetro.
- **Sonido ambiente** por clima y **`/weather` compatible**: los comandos y dormir se comportan como esperas.

### Compatibilidad

- Minecraft **1.20.1** (Forge y Fabric), **1.21–1.21.1** (NeoForge y Fabric) y **1.21.11** (NeoForge y Fabric). El jar de Forge 1.20.1 vale también para NeoForge 1.20.1. El de Fabric funciona en **Quilt**.
- En Fabric hace falta **Fabric API**. Probado con **Sodium** y con **Iris** (incluso con shaders).
- Multijugador: cada jugador ve su clima. Probado con servidor dedicado.
- [Bloomberto] (opcional): pantalla de ajustes y más integración.

## Description (EN)

### Weather stops being a switch

In vanilla it rains everywhere at once. With **Stormroot**, weather comes as **fronts** that are born, grow, travel and dissolve. Watch a storm roll in from the horizon, outrun a hail cloud, or walk across the line between rain and sunshine.

- **Real clouds**: every cloud you see actually exists; it only rains under the dark ones. Cloud shadows on the ground.
- **Ten weathers**: rain, thunderstorm (lightning actually strikes under the front), **dry storm**, **blizzard**, **monsoon**, **heat wave**, **hail**, **fog**, **sandstorm**, plus the rare **aurora** and **magnetic storm**.
- **Fronts collide**: heat + rain = storm; rain dissolves a sandstorm; equal fronts merge…
- **Weather seeps underground**: rain forms puddles, mud and moss in caves; drought dries and cracks them; blizzards freeze and hang icicles; monsoons flood shores. All reversible — the mod never breaks your builds.
- **The mycelium network**: a living network under swamps with nodes, threads and its own creatures (sporeling, weaver, guardian). Destroy it… or tame it.
- **Details**: wind bends arrows, compasses spin in magnetic storms, rainbows with a reward, night frost, fossils uncovered by sand, sand goggles, weathervane and barometer.
- **Ambient sound** per weather, and `/weather` and sleeping behave the way you expect.

### Compatibility

- Minecraft **1.20.1** (Forge & Fabric), **1.21–1.21.1** (NeoForge & Fabric), **1.21.11** (NeoForge & Fabric). The Forge 1.20.1 jar also works on NeoForge 1.20.1; the Fabric jar works on **Quilt**.
- Fabric needs **Fabric API**. Tested with **Sodium** and **Iris** (shaders included).
- Multiplayer-ready: each player sees their own sky. Tested on dedicated servers.
- [Bloomberto] (optional): settings screen and deeper integration.

## Notas para publicar

- Licencia: **MIT** (ya declarada en el jar y en `LICENSE`).
- Galería: capturas a tamaño completo en el artefacto `selftest-shots` de cualquier tanda verde de la CI
  (1280×720 PNG); las de `docs/publicacion/galeria/` son versiones reducidas de muestra.
- Etiquetas sugeridas: en Modrinth, `game-mechanics` y `decoration`; en CurseForge, "Adventure and RPG".
- Dependencia a declarar en Fabric: Fabric API (ya la añade `publishMods`).
