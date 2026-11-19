# ⛈️🍄 Stormroot — *Raíz de Tormenta* *(nombre provisional)*

> Mod multiloader para Minecraft **1.20.1 · 1.21–1.21.1 · 1.21.11** (Fabric, Forge, NeoForge)
> **Lo que pasa en el cielo acaba llegando bajo tierra.**
> Estado: 🛠️ Base del proyecto creada · empezando el MVP

---

## 📖 ¿De qué va?

**Stormroot** cambia el clima de Minecraft por **frentes climáticos que avanzan por el mundo**: se ven venir, se pueden predecir y cambian el terreno a su paso.

Cada tormenta, sequía o banco de niebla **se filtra al subsuelo**. Días después, las cuevas que hay debajo se inundan, se secan, cristalizan o se llenan de vida. El jugador aprende a leer el cielo para saber qué encontrará abajo.

Por el mundo hay **ruinas de una civilización antigua que aprendió a dominar el clima**. Con sus restos y con muestras recogidas de cada fenómeno, el jugador acaba **creando microclimas propios** y, al final, **dirigiendo frentes enteros**.

**Pilares del diseño**
1. 🌩️ **Clima vivo**: el tiempo es un sistema que se mueve, no un interruptor global.
2. 🍄 **Subsuelo reactivo**: las cuevas cambian según lo que pasa arriba.
3. 🧭 **Leer y anticipar**: el jugador gana más observando y preparándose que luchando.
4. 🧪 **Experimentar**: las muestras de clima se combinan para conseguir efectos.
5. 🧩 **Apto para modpacks**: todo configurable, data-driven y sin romper el clima vanilla.

---

## 📦 Compatibilidad

| Jar (target) | Funciona en | Fabric | Forge | NeoForge | Java |
|---|---|:---:|:---:|:---:|:---:|
| 1.20.1  | 1.20.1         | ✅ | ✅ | ➖ | 17 |
| 1.21.1  | 1.21 – 1.21.1  | ✅ | ➖ | ✅ | 21 |
| 1.21.11 | 1.21.11        | ✅ | ➖ | ✅ | 21 |

Las versiones intermedias (1.20.2–1.20.6, 1.21.2–1.21.10) **no tienen jar** de momento. Cada jar declara en sus metadatos solo las versiones que soporta, así que en otra versión el loader avisa en lugar de crashear. Para añadir un target: `settings.gradle.kts` + `versions/dependencies/<versión>.properties` (con `mc_range_fabric` y `mc_range_forge`).

Quilt: el jar de Fabric funciona en Quilt Loader.

NeoForge 1.20.1: usa el jar de **Forge 1.20.1** (NeoForge 1.20.1 es un fork de Forge 47.1). Al publicar, ese jar se marca también como NeoForge. Pendiente de probar en el juego.

---

## 🛠️ Cómo compilar

Requisitos: **Java 25** para ejecutar Gradle (y 17 + 21 para los targets; Gradle puede descargarlos), Git.

```bash
./gradlew buildAndCollect          # Windows: gradlew.bat buildAndCollect
```

Los `.jar` aparecen en `build/libs/`. El primer build tarda varios minutos (descarga Minecraft para los 6 targets).

- **IntelliJ IDEA**: *File → Open* la carpeta y esperar a Gradle.
- **Cambiar versión/loader activo** (Stonecutter): panel de Gradle → `stonecutter` → *Set active project to 1.21.1-neoforge*, etc.
- **Probar en el juego**: tarea `runClient` del target activo.
- La CI de GitHub (`.github/workflows/build.yml`) compila todo en cada push y publica en Modrinth/CurseForge al crear una Release.

## 🌸 Bloomberto (opcional)

[Bloomberto](https://github.com/HCuadrado428/Bloomberto) es una librería nuestra. Stormroot **funciona sin ella**; si está instalada (en los 6 targets), añade:

- **Config** en `config/stormroot.toml`, con botón *Config* en la lista de mods de Forge/NeoForge y, en Fabric, en Mod Menu.
- **Efectos de pantalla**: desaturación y viñeta dentro de tormentas y niebla.
- **Temblor de cámara** cuando cae un rayo cerca.
- **Frentes visibles de lejos**: nubes y cortinas de lluvia en el borde de los frentes hasta a 400 bloques.
- **Indicador del clima** en pantalla (lluvia/tormenta/niebla en %), desactivado por defecto: `weather_hud = true`.

Para compilar con la integración: `scripts/fetch-bloomberto.sh` (compila Bloomberto con JDK 21 en el commit de `bloomberto.ref` y deja los jars en `libs/bloomberto/`). Sin esos jars, el mod compila igual pero sin integración. Con ellos, `runClient` carga también Bloomberto. Para usar una versión más nueva de Bloomberto, cambia el commit de `bloomberto.ref`.

## 🧪 Pruebas dentro del juego (self-test)

La CI arranca **Minecraft de verdad** en los 6 targets en cada push (`scripts/selftest.sh`, job `selftest`):

- **Servidor dedicado** (33 comprobaciones): construye una cueva de pruebas y comprueba registros, guardado de frentes y de filtración, el paquete de red, los comandos, los ajustes del mundo, que `/weather` se respeta, que se quitan los rayos fuera de las tormentas, el pronóstico, la veleta y el barómetro (señal de redstone), que las pozas solo llenan hoyos cerrados, que la cueva empapada da charcos, barro y musgo, que **el agua nunca se derrama**, que la raíz de rayo baja hasta la cueva y que **al secarse cada bloque vuelve a ser exactamente el original**; además, que solo llueve bajo los nubarrones, que los frentes nacen según el bioma, que la niebla humedece sin hacer charcos, que los rayos de tormenta seca no prenden fuego vanilla que el fuego del clima solo quema hojas de árbol, que la nieve de la ventisca se apila, que la ola de calor seca el suelo y despeja el cielo, que el granizo daña cultivos y cristal, que el monzón inunda orillas y cuevas y lo deja todo como estaba y que **la cueva helada vuelve a ser la de antes al deshelarse**.
- **Un jugador** en una pantalla virtual (13 más): llegan los frentes al cliente, la tormenta se ve y se va, la niebla (y que el enganche de cada loader la aplica de verdad al dibujar), la humedad de las cuevas, el cielo de Stormroot (nubes vanilla ocultas, una tormenta lejana dibujada, nubes de buen tiempo sin frentes y sus sombras en el suelo) y la integración con Bloomberto (archivo y pantalla de config, efectos de pantalla). Saca capturas (artefacto `selftest-shots` de la CI).

En local: `scripts/selftest.sh server 1.21.1-fabric` o `scripts/selftest.sh client 1.21.1-fabric` (cliente: necesita `xvfb-run` en Linux). En cualquier lanzador: `-Dstormroot.selfTest=server|client|all` (el juego se cierra al terminar; `-Dstormroot.selfTest.exit=false` lo deja abierto). Sin esa propiedad el self-test no hace nada.

**No lo cubre** (hay que mirarlo a ojo): que la niebla, las partículas, las texturas y los modelos *se vean* bien, los mensajes al hacer clic derecho en los instrumentos, las recetas, y partidas con varios jugadores.

## 📁 Estructura del código

Proyecto **Stonecraft + Stonecutter**: un único código fuente con comentarios `//? if ...` para cada versión y loader.

```
src/main/java/io/github/hcuadrado428/stormroot/
  Stormroot.java          → entrada del mod: registro y eventos de cada loader
  ModIds.java             → construye ids "stormroot:algo" (ResourceLocation / Identifier)
  registry/ModItems.java  → items
  registry/ModBlocks.java → bloques (veleta, barómetro)
  block/                  → InstrumentBlock (base con redstone) y los instrumentos
  config/StormrootConfig.java   → ajustes (valores por defecto o los de Bloomberto)
  compat/Compat.java            → carga las integraciones opcionales por reflexión
  compat/bloomberto/            → integración con Bloomberto (solo se compila si están sus jars)
  selftest/SelfTest.java  → pruebas dentro del juego (servidor); client/ClientSelfTest.java (cliente)
  network/                → paquete servidor → cliente con los frentes cercanos
  client/                 → clima por zonas, niebla y partículas (solo cliente)
  seep/                   → filtración: SeepageCell/Rates/Field (Java puro), SeepageTicker y SeepageSavedData
  weather/
    FrontType.java        → tipos de frente (Java puro)
    WeatherFront.java     → un frente: posición, radio, movimiento, intensidad (Java puro)
    FrontSimulation.java  → simulación de los frentes de una dimensión (Java puro)
    FrontTicker.java      → conecta la simulación con el tick del servidor (vanilla)
src/main/resources/assets/stormroot/  → texturas, modelos, idiomas
art/textures/             → "fuentes" de las texturas (specs de texgen.py)
versions/dependencies/    → versiones de Minecraft, loaders y rangos de cada target
src/versioned/<versión>/  → recetas, loot tables y tags (su formato cambia entre 1.20.1, 1.21.1 y 1.21.11)
```

**Regla:** la lógica va en clases sin imports de ningún loader (como `weather/`). En `Stormroot.java` solo va el "pegamento" de cada loader.

## ✅ Qué hay ya hecho

- Proyecto multiloader con los 6 targets configurados (la CI compila todo en cada push).
- Item **Cristal de rayo** (`stormroot:lightning_crystal`) con textura, modelo y traducciones en/es, en la pestaña *Ingredientes*.
- **Simulación de frentes** (lluvia, tormenta eléctrica, niebla): nacen a contraviento de los jugadores, se mueven con un viento dominante que cambia despacio, crecen y se debilitan al nacer y morir, y caducan.
- **Los frentes se guardan en el mundo** (`data/stormroot_fronts.dat`), junto con el viento y `/stormroot weather`.
- **Clima en el servidor**: si algún jugador está en una tormenta truena, si está en un frente de lluvia llueve y, si no, está despejado (afecta a cultivos, fuego, rayos...).
- **Rayos solo en las tormentas**: los rayos naturales que caerían fuera de cualquier frente de tormenta se cancelan (los de `/summon` con cielo despejado y los del tridente no se tocan).
- **`/weather` se respeta**: si alguien cambia el clima con `/weather`, Stormroot espera a que ese clima termine y luego retoma el control.
- **Clima por zonas en el cliente**: el servidor manda a cada jugador los frentes cercanos y cada uno **ve** la lluvia y los truenos de su zona, con transición suave al entrar y salir. Si el servidor o el cliente no tienen el mod, no se rompe nada.
- **Niebla** dentro de los frentes de niebla, en todos los loaders y versiones (evento en Forge/NeoForge, mixin en Fabric).
- **Cielo propio** (con o sin Bloomberto): Stormroot quita las nubes vanilla y dibuja las suyas, bloques planos sombreados al estilo vanilla:
  - con buen tiempo, **pocas nubes sueltas**, de pequeñas a **enormes** según la zona, que se forman y se deshacen poco a poco y derivan con el viento;
  - en los frentes, **nubarrones** grises (lluvia) o gris azulado, más bajos y gruesos (tormenta), que aparecen y desaparecen con el frente; se ven llegar desde casi 400 bloques.
  - Respeta el ajuste de nubes del jugador (Rápidas, Detalladas, No). Para shader packs con nubes propias: `custom_clouds = false` (en `config/stormroot.toml` con Bloomberto, o en `config/stormroot-client.properties` sin él) vuelve a las vanilla.
  - **Solo llueve bajo los nubarrones**, también en el juego (fuera no se apagan fuegos ni se llenan calderos).
  - **Aviso de tormenta**: delante de un frente salen más nubes y se van poniendo grises; los nubarrones son más gruesos y oscuros cuanto más fuerte es el frente.
  - **Sombra de las nubes** en el suelo (opción `cloud_shadows`) y una **capa alta** de velos finos.
- **Tormenta seca**: en desiertos, badlands y sabanas las tormentas echan rayos sin lluvia, que hacen daño y dan el doble de cristales de rayo. No prenden fuego normal: a veces encienden el **fuego del clima**, que solo arde en hojas de árbol (no en las que pone un jugador), salta como mucho 4 veces, se apaga solo en unos 30 s o con la lluvia y no puede quemar casas.
- **Frentes según el bioma**: en el desierto solo nacen tormentas (secas); en pantanos, junglas y océanos, más lluvia y niebla.
- **Ventisca** (biomas fríos y cumbres): nubarrón blanco, poca visibilidad y nieve que se apila hasta 4 capas. Debajo, **todas las cuevas se hielan** poco a poco:
  - suelo de nieve y hielo compacto mezclados, agua natural congelada, **carámbanos** (hasta 3 bloques; caen y hacen daño si les pones luz cerca) y **cristales de hielo** que brillan;
  - **congelación** si te quedas mucho rato (como la nieve polvo; la ropa de cuero protege) y más **strays** a oscuras;
  - se deshielan muy poco a poco; una tormenta fuerte sobre una cueva muy helada provoca un **deshielo masivo**: se derrite en un minuto y se inunda.
  - Todo se deshace al deshelarse (cada bloque vuelve a su material).
- **Ola de calor** (templados, húmedos y secos): enorme, larga y casi quieta. Despeja el cielo, de día y al sol da más hambre, a mediodía puede prender las hojas de los árboles (fuego del clima) y **seca el subsuelo** mucho más rápido (grietas antes). Sobre una cueva muy helada provoca un **deshielo masivo**.
- **Monzón** (junglas, pantanos, costas): lluvia torrencial enorme y larga que quita visibilidad. Los cultivos crecen más rápido, **las orillas de ríos y lagos se inundan** (hasta 2 bloques, y vuelven a ser hierba, arena o tierra al irse), el subsuelo se moja tres veces más rápido y en las cuevas caen **cascadas temporales** del techo.
- **Aurora** (rara, biomas fríos y cumbres): de noche, cortinas de luz verde y morada en el cielo; a veces cae **polvo de aurora**.
- **Tormenta magnética** (rara, en cualquier bioma): chispas en el aire, los instrumentos (veleta, barómetro) dan lecturas locas y el mineral de hierro de alrededor se vuelve **roca magnetizada** (suelta chispas y sigue dando hierro).
- **Arcoíris** (evento raro): cuando se acaba una lluvia, tormenta o monzón cerca de ti, de día, a veces sale un arcoíris. Uno de sus extremos toca el suelo cerca: si llegas antes de que se borre (2,5 min) encuentras un **fragmento de arcoíris** y esmeraldas.
- **Tormenta de arena**: solo nace en desiertos, badlands y sabanas. Se ve venir como un muro marrón desde casi el suelo. Dentro, visión corta, arena que vuela con el viento y capas de arena (hasta 3) sobre arena y terracota a cielo abierto, que el viento se lleva cuando acaba; nunca tapa cultivos ni casas. En el centro, mirar contra el viento sin **gafas de arena** (cuero, cuerda y paneles de cristal) ciega un momento de vez en cuando; con ellas se ve el doble de lejos. Su **calima** llega a los biomas de alrededor (hasta 2,5 veces su radio): polvo fino y nubes anaranjadas, solo visual.
- **Frentes que chocan**: cuando dos frentes maduros se encuentran, pasa algo. La ola de calor convierte la lluvia en tormenta eléctrica y deshace las ventiscas; la ventisca se traga la lluvia y dura más; la niebla se disipa al llegar casi cualquier otro frente; la lluvia asienta las tormentas de arena; y dos frentes iguales se funden en uno más grande.
- **Viento**: no es un fenómeno sino una mecánica de todos. Sopla poco con buen tiempo y fuerte dentro de las tormentas y ventiscas y en la franja que llevan delante; no se nota bajo techo ni en cuevas, se nota la mitad bajo los árboles y más en océanos, montañas y en lo alto. Desvía los tiros largos sin rachas al azar: nada en los primeros 15–20 bloques (el PvP de cerca no cambia), unos 0,3 bloques a 40 con buen tiempo y 1 con tormenta. El tridente lo nota a la mitad, las bolas de nieve y huevos el doble y las perlas de ender casi nada. Las elytras y los barcos van algo más rápido a favor. Ajustes del mundo `wind_projectiles` y `wind_player_shots` (apágalo en servidores de PvP para que no toque los disparos de los jugadores).
- **Helada**: en las noches despejadas de los biomas fríos (7 de cada 10 noches) y templados (3 de cada 10) hiela de 20:00 a 10:00. El agua quieta de un bloque de hondo se congela (y vuelve a ser agua al acabar), los cultivos no crecen salvo con una antorcha cerca y hay escarcha sobre la hierba.
- **Red de micelio**: bajo pantanos, manglares y campos de champiñones, en cuevas húmedas, nace una red viva: **nodos** que brillan un poco y sueltan esporas, e **hilos** finos en el suelo, las paredes y el techo. Crece con la humedad del subsuelo (el doble con niebla), se para con la sequía y es permanente; cada red ocupa como mucho **30 chunks** y nunca sale de esos biomas. **Se asusta con la luz**: los hilos con luz de antorcha se retiran y no crecen hacia ella. Si **rompes un nodo**, muere el trozo de red que solo dependía de él. Los nodos dan **esporas** (y a veces una seta); con toque de seda, el nodo.
- **Habitantes de la red**: los **esporines** (pasivos, del tamaño de un conejo) corretean por los hilos, huyen de ti y de la luz, se quedan dormidos con la sequía y hacen crecer la red; con niebla salen el doble. Sueltan esporas. Los **tejedores** (neutrales, como arañas blandas de hongo) vuelven a tejer la red a oscuras; si les pegas o rompes un nodo cerca, te enredan (lentitud) y sueltan **hilo de micelio** (3 hilos → 2 de cuerda). El **guardián de micelio** (30 corazones) duerme sobre un nodo de las redes salvajes grandes; si le atacas o rompes nodos cerca despierta, llama a los tejedores y ataca con golpes fuertes, nubes de esporas (náusea y lentitud) y raíces que te clavan al suelo. Las antorchas lo debilitan y ardiendo no usa esporas ni raíces. Al vencerlo **la red se calma** (deja de crecer) y suelta el **corazón de micelio**: úsalo sobre un nodo y la red pasa a ser tuya (solo crece por roca y tierra naturales y sus tejedores te defienden de los monstruos). Hay huevos de generación de los tres.
- **Bruma de esporas**: la niebla sobre esos mismos biomas lleva esporas. Respirarla unos 30 s da **náusea leve**; protege llevar una calabaza tallada o subir a terreno alto (16 bloques sobre el mar). Las setas de alrededor se extienden más deprisa y la red crece el doble.
- **Granizo**: una de cada tres tormentas tiene un núcleo verdoso que suelta granizo donde haría llover. Sin casco quita medio corazón cada pocos segundos, los cultivos maduros retroceden dos etapas, puede romper el cristal normal a la intemperie (`/stormroot settings hail_breaks_glass off` para evitarlo) y deja **granizo** en el suelo (4 = un bloque de hielo).
- **Niebla que rueda** pegada al suelo con el viento; se deshace al entrar en biomas sin lluvia (desierto, badlands, sabana); los bancos de niebla humedecen un poco el subsuelo (barro y musgo, nunca charcos).
- **Partículas en el borde** de los frentes cercanos (gotas, humo de tormenta, nubes bajas).
- **Cristales de rayo**: un rayo sobre arena o piedra deja un cristal el 25 % de las veces. Se usan en la receta del barómetro.
- **Comandos de depuración** `/stormroot ...`.
- **Veleta**: la flecha apunta hacia donde la colocas; da señal de redstone máxima cuando el viento sopla hacia ahí. Clic derecho: rumbo del viento.
- **Filtración**: cada chunk guarda la humedad del suelo y del subsuelo. La lluvia empapa el suelo en minutos y el subsuelo la sigue unos 10 minutos después. Según el subsuelo, las cuevas cercanas a los jugadores cambian:
  - **Barro**: la tierra del suelo se vuelve barro.
  - **Charcos**: bloques del suelo pasan a ser agua, siempre encerrados (nunca se derrama), y crecen con la humedad.
  - **Pozas**: si está muy empapado, los hoyos cerrados del suelo se llenan de agua.
  - **Musgo** en la roca tras mucho tiempo húmedo; **goteo** del techo en el cliente.
  - **Secado lento**: el agua estancada retiene la humedad y el agua se retira bloque a bloque (charcos, pozas, barro).
  - **Sequía**: la tierra se agrieta, el musgo se seca y flota polvo. Todo vuelve a su estado al cambiar el clima.
  - Solo se tocan bloques naturales de cueva y cada cambio se deshace solo si el bloque sigue como lo dejó el mod.
- **Raíces de rayo**: un rayo sobre piedra, tierra o arena puede hacer crecer hacia abajo una columna de cristal luminoso hasta llegar a una cueva. Al picarla puede soltar Cristales de rayo.
- **Barómetro**: pronostica el próximo frente en los próximos 3 minutos; la señal de redstone sube cuanto más fuerte y cercano. Clic derecho: qué llega y en cuánto.

## 🔜 Siguientes pasos

1. **Probarlo a ojo en el juego**: nubes, nieblas, partículas, mobs y texturas (ver "Pruebas dentro del juego").
2. **Publicar la 0.1**: crear los proyectos en Modrinth y CurseForge, poner en GitHub los secretos `MODRINTH_TOKEN` y
   `CURSEFORGE_TOKEN` y las variables `MODRINTH_ID`, `CURSEFORGE_ID` y `CURSEFORGE_SLUG`, y publicar una *release*
   (la CI sube los jars sola).
3. Del diseño, aún sin hacer: corrientes de aire y bolsas de gas, ruinas de los Tempestarios, más instrumentos,
   muestras y frascos, el jugador y el clima, más fauna y flora, y progresión.

### Limitaciones conocidas de la 0.1
- Los frentes solo existen en el **Overworld** (decisión de diseño: el Nether y el End no tienen clima).
- Fuera de los rayos, la lluvia "de juego" sigue siendo global en el servidor (fuego que se apaga, calderos que se llenan...) mientras algún jugador esté en un frente.
- Sin Bloomberto no hay pantalla de config: los ajustes del mundo se cambian con `/stormroot settings` y los del cliente en `config/stormroot-client.properties`.
- En Fabric, la pantalla de config necesita Bloomberto **y** Mod Menu.

## 🧰 Comandos de depuración

Requieren permisos de operador (nivel 2).

| Comando | Qué hace |
|---|---|
| `/stormroot front list` | Lista los frentes activos |
| `/stormroot front spawn rain\|thunderstorm\|fog` | Crea un frente centrado en ti |
| `/stormroot front clear` | Borra todos los frentes |
| `/stormroot here` | Qué frente hay donde estás, su intensidad y hacia dónde sopla el viento |
| `/stormroot seep` | Humedad del suelo y del subsuelo de tu chunk |
| `/stormroot seep set <-1..1>` | Fija la humedad de tu chunk (1 = charcos enseguida, -1 = sequía) |
| `/stormroot settings` | Ajustes de este mundo (se guardan con el mundo) |
| `/stormroot settings seepage\|cave_changes\|lightning_roots\|hail_breaks_glass on\|off` | Apaga la filtración, los cambios de bloques en cuevas, las raíces de rayo o que el granizo rompa cristal, sin necesitar Bloomberto |
| `/stormroot weather on\|off` | Stormroot controla el clima vanilla (`on`, por defecto) o lo deja en paz (`off`) |

---

## 🌩️ 1. Frentes climáticos

En lugar de que llueva en todo el mundo a la vez, aparecen **frentes**: zonas de clima con forma, tamaño, dirección y velocidad propias.

| Frente | Qué hace | Qué deja |
|---|---|---|
| 🌧️ **Lluvia persistente** | Llena charcos y riega cultivos | Filtración → cuevas inundadas |
| ⚡ **Tormenta eléctrica** | Rayos frecuentes y viento fuerte | Cristal de rayo, roca magnetizada |
| 🌫️ **Banco de niebla** | Visión muy reducida, sonidos amortiguados | Musgo de rocío, hongos de eco |
| 🧊 **Granizo** | Daña cultivos y cristal sin proteger | Hielo de granizo (refrigerante) |
| 🔥 **Ola de calor** | Evapora el agua, el hambre baja más rápido | Sequía → grietas y cuevas al descubierto |
| 🌪️ **Viento seco / polvo** | Empuja entidades y proyectiles, arrastra arena | Dunas nuevas, fósiles destapados |
| ❄️ **Ventisca** | Acumula capas de nieve y ralentiza | Cuevas de hielo bajo la nieve |
| 🌌 **Aurora** *(rara, nocturna)* | Cielo de colores, mobs pasivos tranquilos | Polvo de aurora (ingrediente endgame) |

**Mecánicas**
- Los frentes **nacen** en ciertos biomas (el calor en desiertos, la niebla en pantanos...) y **se desplazan** con el viento dominante.
- Se ven **desde lejos**: cortinas de lluvia, muros de niebla, nubes oscuras en el horizonte.
- **Chocan entre sí**: un frente frío contra uno caliente da una tormenta eléctrica.
- **Charcos dinámicos** que se forman y se evaporan, y **capas de nieve** que se acumulan de verdad.
- **Arcoíris** tras algunas tormentas: donde "toca el suelo" puede aparecer algo.
- El **viento** afecta a flechas, tridentes, elytras, partículas y a la velocidad de los barcos.

---

## 🕳️ 2. Filtración: el subsuelo reacciona

Cada chunk guarda un **"registro climático"** con lo que le ha caído encima en los últimos días. Ese registro cambia las cuevas de debajo de forma gradual.

- 💧 **Inundación**: tras lluvias largas, las cuevas poco profundas se llenan de agua y aparecen **cuevas inundadas** con fauna propia.
- 🏜️ **Desecación**: con sequías largas se baja el nivel del agua, se abren **grietas** y quedan al descubierto vetas y estructuras enterradas.
- ⚡ **Raíces de rayo**: donde cae un rayo se forma un **cristal que crece hacia abajo**, y seguirlo lleva a una geoda o una cámara.
- 🍄 **Red de micelio**: con humedad crece y se extiende, con sequía se seca y se defiende, y con niebla emite esporas.
- 🌬️ **Corrientes de aire**: las cuevas "respiran"; las partículas de aire marcan por dónde está la salida más cercana.
- 💨 **Bolsas de gas** *(en sequía)*: se pueden encender... o aprovechar.
- 🪨 **Estalactitas que gotean**: si gotean más, ha llovido arriba hace poco, y eso sirve de pista para el jugador minero.

---

## 🍄 3. La red de micelio

Un organismo subterráneo que se extiende lentamente y **reacciona** a lo que hace el jugador.

- Se **asusta** con la luz fuerte y con las explosiones.
- **Responde al sonido**: picar mucho o hacer ruido atrae a sus defensores.
- Forma **nodos** que conectan zonas: si se destruye uno, una parte de la red muere.
- **Corazón de la red** *(jefe opcional)*: su comportamiento depende del clima de la superficie en ese momento.
  - 🌧️ Con lluvia: regenera e invoca esporas.
  - ☀️ Con sequía: se endurece y lanza fragmentos.
  - 🌫️ Con niebla: se vuelve invisible a ratos.
  - Se puede **destruir**, **domar** (la red pasa a ayudar al jugador) o **dejarlo crecer** (cambia el bioma subterráneo).

---

## 🏛️ 4. Ruinas de los Tempestarios

La civilización antigua que domaba el clima. Sus estructuras **solo se activan con ciertos climas**.

| Estructura | Dónde | Secreto |
|---|---|---|
| 🔭 **Observatorio derruido** | Montañas | Enseña a predecir frentes |
| ⚡ **Torre pararrayos** | Llanuras | Hay que "cargarla" con un rayo para que se abra |
| 🔔 **Templo de la campana de niebla** | Pantanos | La campana disipa o atrae la niebla |
| 🕳️ **Cisterna subterránea** | Bajo aldeas antiguas | Se inunda o se vacía según el clima |
| 🏺 **Pozo de lluvia** | Desiertos | Con lluvia revela un acceso subterráneo |
| 🌌 **Altar de aurora** | Taiga o tundra | Solo funciona bajo una aurora: endgame |

- **Tablillas de clima**: fragmentos de conocimiento que desbloquean recetas y páginas del cuaderno.
- **Mecanismos**: palancas de presión, veletas gigantes y puertas que se abren con el viento.

---

## 🧪 5. Muestras y frascos de clima

El jugador **recoge muestras** de cada fenómeno y experimenta con ellas.

**Muestras**: agua de tormenta, polen de niebla, chispa estática, polvo seco, escarcha de granizo, esporas de micelio y polvo de aurora.

**Usos**
- 🫙 **Frascos de clima**: se lanzan y crean un **microclima local** durante un rato (una nube que riega, niebla para escapar, una ráfaga de viento...).
- ⚗️ **Destilador climático**: combina muestras. Las combinaciones **no están fijas** del todo: hay efectos emergentes que el jugador apunta.
- 📓 **Cuaderno del meteorólogo**: registra frentes vistos, muestras y recetas descubiertas. Al principio las descripciones son vagas y se vuelven precisas con la experiencia.
- 💥 **Fallos útiles**: una mezcla mal hecha da sal, vidrio turbio o fertilizante, que también sirven para algo.

---

## 🧭 6. Herramientas e instrumentos

| Item / bloque | Función | Redstone |
|---|---|:---:|
| 🧭 **Veleta** | Dirección e intensidad del viento | ✅ según dirección |
| 🌡️ **Barómetro** | Predice el próximo frente (horas antes) | ✅ según presión |
| 🌧️ **Pluviómetro** | Mide la lluvia acumulada y la filtración del chunk | ✅ |
| 🔭 **Catalejo de nubes** | Muestra la silueta de los frentes en el horizonte | ➖ |
| 🗺️ **Mapa sinóptico** | Mapa con los frentes cercanos en tiempo real | ➖ |
| 🔔 **Campana de niebla** | Disipa la niebla en un radio | ✅ |
| ⚡ **Pararrayos tempestario** | Captura rayos y guarda carga estática | ✅ |
| 🌀 **Molino de viento** | Usa el viento para moler o mover cosas | ✅ |

---

## 🧥 7. El jugador y el clima

- **Estado "Empapado"**: más lento y con más frío, pero resistente al fuego.
- **"A cubierto"**: el mod detecta si estás bajo techo y lo usa en varias mecánicas.
- **Equipo**
  - 🧥 Capa impermeable: anula "Empapado".
  - 🥽 Gafas de niebla: mejoran la visión en niebla y polvo.
  - 👢 Botas de barro: no resbalas ni te hundes en los charcos.
  - 🪁 Cometa de tormenta: atrae rayos a distancia (cuidado).
  - 🪂 Paravela: planea aprovechando el viento antes de tener elytras.
- **Campamento**: tienda de lona, hoguera protegida y lona para cubrir cultivos.

---

## 🌾 8. Agricultura y construcción

- Algunos cultivos **crecen mejor** con ciertos frentes, y el granizo **rompe** los que no están protegidos.
- **Cisternas y canalones** que recogen la lluvia para regar en épocas de sequía.
- **Invernadero funcional**: la estructura detecta el cristal y protege lo que hay dentro.
- **Contraventanas**: se cierran solas cuando llega una tormenta (con redstone o automáticas).
- **Bloques que reaccionan al clima**: tejas que se oxidan con la lluvia, madera que se blanquea con el sol y piedra con musgo tras la niebla.

---

## 🦎 9. Fauna y flora

**Criaturas**
| Mob | Dónde | Comportamiento |
|---|---|---|
| 🐌 **Caracol de lluvia** | Superficie, con lluvia | Pasivo; deja un rastro que fertiliza |
| 🦋 **Polilla de tormenta** | Antes de las tormentas | Su presencia **avisa** de que viene un frente |
| 🦀 **Cangrejo de charco** | Charcos dinámicos | Desaparece cuando el charco se seca |
| 🦅 **Halcón de corriente** | Montañas con viento | Domable; señala la dirección de los frentes |
| 👻 **Espectro de niebla** | Bancos de niebla | Hostil solo si estás lejos de la luz |
| 🪼 **Medusa de cueva** | Cuevas inundadas | Ilumina el agua; pica si la tocas |
| 🐀 **Topo de raíz** | Subsuelo | Cava túneles; sigue las raíces de rayo |
| 🍄 **Guardián de micelio** | Red de micelio | Defiende los nodos; acude al ruido |
| 🔔 **Centinela tempestario** | Ruinas | Constructo que se activa con cierto clima |

**Plantas**
- 🌼 **Flor barométrica**: se cierra antes de que llueva.
- 🌿 **Musgo de rocío**: crece tras la niebla y da agua al romperlo.
- 🍄 **Hongo de eco**: repite los sonidos cercanos (una trampa natural).
- ⚡ **Raíz de rayo**: brilla y crece hacia abajo.
- 🌵 **Cactus de reserva**: guarda agua en sequía.

---

## 🌠 10. Eventos raros

- 🌀 **Tormenta perfecta**: varios frentes chocan; caos y botín muy valioso.
- 🌑 **Eclipse de niebla**: niebla global durante una noche; los espectros salen en masa.
- 🍄 **Erupción de esporas**: la red de micelio "estalla" hacia la superficie.
- 🏜️ **Gran sequía**: varios días sin lluvia; se abren las cuevas más profundas.
- 🌌 **Noche de aurora**: única oportunidad de usar el altar de aurora.

---

## 📈 11. Progresión

| Rango | Cómo se llega | Desbloquea |
|---|---|---|
| 1. **Observador** | Fabricar veleta y barómetro | Cuaderno y predicción básica |
| 2. **Lector del cielo** | Registrar 5 tipos de frente | Muestras, frascos y destilador |
| 3. **Explorador de raíces** | Llegar a una zona de filtración profunda | Equipo de cueva y raíces de rayo |
| 4. **Tempestario** | Activar 3 ruinas distintas | Pararrayos tempestario y campana de niebla |
| 5. **Domador de frentes** | Usar el altar de aurora | **Faro climático**: dirigir, frenar o crear frentes en una región |

Incluye **logros (advancements)** para cada paso y otros ocultos para eventos raros.

---

## ⚙️ 12. Configuración y modpacks

- **Data-driven**: frentes, muestras y recetas del destilador definidos en **JSON** (datapacks).
- Activar o desactivar cada sistema por separado: frentes, filtración, micelio, estado empapado...
- Límites por **dimensión** y **bioma**.
- **Modo compatible**: si otro mod de clima o estaciones está activo, Stormroot usa una capa ligera y no sustituye nada.
- Compatibilidad prevista: **JEI / EMI / REI**, **Jade / WTHIT**, mods de estaciones (lectura de datos) y **Create** (molinos de viento, opcional).

---

## 🛠️ Notas técnicas

- Los frentes se guardan como **SavedData** por dimensión: lista de frentes con posición, radio, vector y tipo.
- La simulación funciona por **ticks lentos** (cada X segundos), no por tick, para no afectar al rendimiento en servidores.
- El "registro climático" se guarda **por chunk** (attachment o capability) y la filtración se aplica de forma **perezosa** al cargar el chunk.
- El renderizado del clima es solo del cliente, con sincronización mínima por paquetes.
- **No se reemplaza** el clima vanilla: se superpone. `/weather` sigue funcionando.
- Comandos de depuración: `/stormroot front spawn|list|clear` y `/stormroot seep <chunk>`.

---

## 🚀 MVP (primera versión jugable)

- [x] Simulación de **frentes** con 3 tipos: lluvia, tormenta eléctrica y niebla (núcleo, sin efectos visibles aún)
- [x] Frentes que cambian el clima donde están (por zonas en el cliente)
- [ ] Frentes que se ven llegar en el horizonte
- [ ] **Veleta** y **barómetro**
- [ ] **Filtración básica**: inundación de cuevas tras lluvia
- [x] Item **Cristal de rayo**
- [x] Cristal de rayo que se forma al caer un rayo
- [ ] Raíces de rayo que crecen hacia abajo
- [ ] 2 mobs: polilla de tormenta y medusa de cueva
- [x] Comandos de depuración
- [ ] Config básica
- [x] Compila en los 6 targets · [ ] probado en el juego en los 6 (1.20.1 Fabric/Forge, 1.21.1 y 1.21.11 Fabric/NeoForge)

## 🗺️ Roadmap

- **v0.1, "Primeras nubes"**: MVP
- **v0.2, "Bajo la lluvia"**: charcos, estado empapado, capa, invernadero y contraventanas
- **v0.3, "Raíces"**: red de micelio, guardianes, sequía y grietas
- **v0.4, "Tempestarios"**: ruinas, tablillas y cuaderno completo
- **v0.5, "Alquimia del cielo"**: muestras, frascos y destilador
- **v0.6, "Todos los vientos"**: granizo, calor, polvo, ventisca, viento sobre proyectiles y paravela
- **v1.0, "Domador de frentes"**: corazón de la red, aurora, faro climático y eventos raros

---

## 👥 Equipo

| Nombre | Rol |
|--------|-----|
| ...    | Programación (sistemas: frentes, filtración) |
| ...    | Programación (contenido: bloques, mobs) |
| ...    | Texturas / modelos / partículas |
| ...    | Diseño / balance / worldgen |

---

## 📄 Licencia

MIT (la que trae la plantilla; cambiadla en `fabric.mod.json` y los `.toml` si elegís otra).
