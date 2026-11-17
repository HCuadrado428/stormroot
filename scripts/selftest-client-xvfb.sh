#!/usr/bin/env bash
# Corre DENTRO de xvfb-run (lo llama scripts/selftest.sh): lanza el cliente de un jugador y, cada vez que el self-test
# escribe "[selftest] SCREENSHOT <nombre>", captura la pantalla virtual en build/selftest-shots/<target>-<nombre>.png.
# Uso: scripts/selftest-client-xvfb.sh <target> <log>
t="$1"; log="$2"
shots="build/selftest-shots"
mkdir -p "$shots"
./gradlew ":$t:runClient" --args="--quickPlaySingleplayer testworld" > "$log" 2>&1 &
pid=$!
taken=" "
while kill -0 "$pid" 2>/dev/null; do
  for name in $(grep -oE "\[selftest\] SCREENSHOT [a-z_]+" "$log" 2>/dev/null | awk '{print $3}'); do
    case "$taken" in *" $name "*) continue ;; esac
    taken="$taken$name "
    # Captura con F2, la tecla de capturas del propio Minecraft (lee lo que dibuja OpenGL; capturar la pantalla
    # virtual desde fuera sale negro). El juego la guarda en run/screenshots/; se copia con nombre propio.
    before=$(ls -t run/screenshots/*.png 2>/dev/null | head -n 1)
    xdotool mousemove 640 360 2>/dev/null
    xdotool key F2
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      sleep 1
      newest=$(ls -t run/screenshots/*.png 2>/dev/null | head -n 1)
      [ -n "$newest" ] && [ "$newest" != "$before" ] && break
    done
    if [ -n "$newest" ] && [ "$newest" != "$before" ]; then
      convert "$newest" -alpha off "$shots/$t-$name.png"
      echo "captura: $shots/$t-$name.png brillo=$(convert "$shots/$t-$name.png" -format '%[fx:mean]' info:)"
    else
      echo "no se pudo capturar $name (el juego no guardó ninguna captura con F2)"
    fi
  done
  sleep 1
done
wait "$pid"
