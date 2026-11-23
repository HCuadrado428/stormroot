# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/). Versiones: `MAJOR.MINOR.PATCH`.

## [0.1.0] - sin publicar

Primera versión jugable. Minecraft 1.20.1 (Fabric, Forge; también NeoForge 1.20.1 con el jar de Forge),
1.21–1.21.1 (Fabric, NeoForge) y 1.21.11 (Fabric, NeoForge). Quilt con el jar de Fabric.

### Clima
- **Frentes climáticos** de lluvia, tormenta y niebla que nacen a contraviento de los jugadores, se mueven con un
  viento dominante, crecen, se debilitan y desaparecen. Se guardan con el mundo.
- **Clima por zonas**: cada jugador ve la lluvia y los truenos de su zona, con transición suave.
- Los rayos naturales solo caen dentro de los frentes de tormenta (no junto a jugadores que están fuera).
- **Niebla** dentro de los bancos de niebla.
- **Cielo propio**: las nubes vanilla se sustituyen por las de Stormroot (estilo vanilla sombreado): pocas nubes de
  buen tiempo, de pequeñas a enormes, que se forman y se deshacen; nubarrones de lluvia y tormenta que se ven llegar
  desde lejos. Sin necesitar Bloomberto. Opción `custom_clouds` para shader packs.
- **Solo llueve bajo los nubarrones** (también fuegos, calderos y nieve); aviso de tormenta, sombras de las nubes, capa
  alta y niebla que rueda. Los frentes nacen según el bioma; la niebla se deshace en el desierto.
- **Tormenta seca** en desiertos, badlands y sabanas: rayos sin lluvia, el doble de cristales y **fuego del clima**,
  que solo quema hojas de árbol y nunca casas.
- **Ventisca** en biomas fríos: nieve que se apila y **cuevas de hielo** (nieve y hielo, carámbanos que caen con luz,
  cristales de hielo, congelación, strays) que se deshielan poco a poco o de golpe con una tormenta fuerte.
- **Ola de calor**: cielo despejado, más hambre al sol, fuego del clima en las hojas a mediodía, suelo que se
  seca mucho antes y deshielo masivo de las cuevas heladas.
- **Monzón**: lluvia torrencial que inunda orillas y cuevas (con cascadas del techo) y hace crecer los cultivos; todo
  vuelve a su sitio al irse.
- **Aurora** de noche en biomas fríos (polvo de aurora) y **tormenta magnética** (instrumentos locos, roca magnetizada).
- **Arcoíris** tras la lluvia, con un tesoro en su extremo (fragmento de arcoíris y esmeraldas).
- **Frentes que chocan**: calor + lluvia = tormenta, la ventisca se traga la lluvia, la niebla se disipa, la lluvia asienta
  la arena y dos frentes iguales se funden.
- Niebla marrón en la tormenta de arena, **fósiles** que destapa el viento y **charcos de cueva** que se hielan con la helada.
- Guardián de micelio más robusto.
- **Guardián de micelio**: defensor de las redes grandes; al vencerlo la red se calma y suelta el **corazón de micelio**,
  que domestica redes. **Huevos de generación** de esporín, tejedor y guardián.
- `/weather` y dormir mueven los frentes: `clear` los deshace y pausa los nuevos, `rain`/`thunder` crean uno encima de
  cada jugador, dormir deshace los que hay encima, y `doWeatherCycle false` (`advance_weather` en 1.21.11) los congela.
- Mobs mejorados: el esporín tiene ahora un sombrero con cúpula, motas que brillan y se balancea al andar; la
  **tejedora** (antes tejedor) es una araña del tamaño de la de cueva con setas por el cuerpo y ocho ojos verdes que
  brillan; el guardián es una **seta gigante que anda**, con cara bajo el sombrero, ojos y láminas que brillan, brazos
  de raíz y un golpe alzando los brazos.
- Texturas definitivas de los tres mobs: esporín con sombrero morado moteado y láminas, tejedor peludo con rombo
  en el lomo, cuatro ojos que brillan y patas a franjas, y guardián de corteza con vetas verdes desde el pecho,
  ojos hundidos, manos de raíz y un sombrero de seta rojiza con láminas.
- **Tejedores**: neutrales; reparan la red y te enredan si rompes nodos cerca. Sueltan **hilo de micelio**.
- **Esporines**: el primer habitante de la red de micelio (pasivo; huye de la luz, duerme con la sequía, hace crecer la red).
- Texturas mejoradas: gafas de arena, esporas, polvo de aurora, granizo, roca magnetizada y micelio.
- **Tormenta de arena** en biomas secos: muro marrón en el cielo, capas de arena que se van, ceguera sin **gafas de
  arena** (nuevo objeto) y calima anaranjada alrededor.
- **Viento**: desvía un poco los tiros largos (nunca los de cerca), más con tormenta y nada bajo techo; empuja elytras y
  barcos. Ajustes `wind_projectiles` y `wind_player_shots` para servidores de PvP.
- **Helada** en noches despejadas de biomas fríos y templados: hiela el agua poco honda y para los cultivos sin luz.
- Las **brújulas giran** dentro de una tormenta magnética.
- La **aurora calma la noche**: bajo ella, al aire libre, salen muchos menos mobs hostiles.
- **Red de micelio** bajo pantanos, manglares y campos de champiñones: nodos e hilos que crecen con la humedad, se
  asustan con la luz y mueren si rompes su nodo; hasta 30 chunks por red. Da esporas, setas y el nodo (toque de seda).
- **Bruma de esporas**: la niebla sobre esos biomas marea si la respiras mucho y hace crecer setas.
- **Granizo** en el núcleo de algunas tormentas: daña cultivos maduros, cristal a la intemperie y a quien no lleve
  casco; deja granizo (4 = un bloque de hielo).
- Partículas en el borde de los frentes cercanos.
- `/weather` se respeta: si alguien lo usa, Stormroot espera a que ese clima termine.

### Bajo tierra
- **Filtración**: cada chunk guarda la humedad del suelo y del subsuelo (que va unos 10 minutos por detrás).
- Con el subsuelo empapado, las cuevas cercanas a los jugadores se llenan de **barro, charcos que crecen, pozas y
  musgo**; con sequía, la tierra se agrieta. Todo vuelve exactamente a como estaba al cambiar el clima.
  El agua nunca se derrama: solo aparece donde queda encerrada.
- Goteo del techo en cuevas húmedas y polvo en sequía.
- **Raíces de rayo**: columnas de cristal luminoso que crecen desde donde cae un rayo hasta una cueva.

### Objetos
- **Cristal de rayo**: aparece donde caen rayos sobre arena o piedra, o al picar raíces de rayo.
- **Veleta**: señal de redstone según el viento; clic derecho para ver el rumbo.
- **Barómetro**: pronostica el próximo frente (3 minutos); señal de redstone según lo fuerte y cercano que sea.

### Ajustes
- `/stormroot settings`: interruptores guardados en el mundo (`seepage`, `cave_changes`, `lightning_roots`) y
  `/stormroot weather on|off`.
- Con [Bloomberto](https://github.com/HCuadrado428/Bloomberto) instalado: `config/stormroot.toml` con pantalla de config
  (Forge, NeoForge y, con Mod Menu, Fabric), efectos de pantalla en tormentas y niebla, temblor de cámara con los
  rayos e indicador del clima.

### Para desarrolladores
- Comandos de depuración `/stormroot front|here|seep`.
- `art/entity/`: generador de las texturas de los mobs, vista previa 3D y modelos de Blockbench (`.bbmodel`).
- Self-test dentro del juego (`-Dstormroot.selfTest=server|client|all`) que la CI ejecuta en los 6 targets.
- Compatibilidad probada en la CI con **Sodium** y **Sodium + Iris** (Fabric, 3 versiones). Con un pack de shaders, el cielo propio tambien se ve (`IrisApi#assignPipeline`).
- Prueba multijugador en la CI: servidor dedicado + 2 clientes, cada uno con su clima; los frentes lejanos no viajan.
- Materiales de publicación en `docs/publicacion/` (descripción es/en, galería) y jar de Fabric probado en Quilt
  en la CI (job `selftest-quilt`: servidor Quilt real con Fabric API).
- Sonido ambiente del clima: viento, tormenta, ventisca, tormenta de arena, granizo y aurora (bucles que suben y
  bajan con el frente; bajo techo se amortiguan).
- Prueba de mundos antiguos en la CI: un mundo guardado por una versión anterior se abre con la actual sin perder nada.
- `-Dstormroot.perf=true`: mide lo que cuesta el cielo, las sombras y la simulación (`Perf`).
