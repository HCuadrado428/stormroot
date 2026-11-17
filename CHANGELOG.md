# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/). Versiones: `MAJOR.MINOR.PATCH`.

## [0.1.0] - sin publicar

Primera versión jugable. Minecraft 1.20.1 (Fabric, Forge; también NeoForge 1.20.1 con el jar de Forge),
1.21–1.21.1 (Fabric, NeoForge) y 1.21.11 (Fabric, NeoForge). Quilt con el jar de Fabric.

### Clima
- **Frentes climáticos** de lluvia, tormenta y niebla que nacen a contraviento de los jugadores, se mueven con un
  viento dominante, crecen, se debilitan y desaparecen. Se guardan con el mundo.
- **Clima por zonas**: cada jugador ve la lluvia y los truenos de su zona, con transición suave.
- La lluvia "de juego" (rayos, fuego...) solo ocurre dentro de los frentes.
- **Niebla** dentro de los bancos de niebla.
- Partículas en el borde de los frentes cercanos; con Bloomberto, los frentes se ven también de lejos.
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
- Self-test dentro del juego (`-Dstormroot.selfTest=server|client|all`) que la CI ejecuta en los 6 targets.
