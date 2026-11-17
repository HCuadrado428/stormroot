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
    # La ventana del juego (si se encuentra) y, si no, toda la pantalla. Sin canal alfa: con alfa 0 sale negra.
    win=$(xwininfo -root -tree 2>/dev/null | grep -i "minecraft" | head -n 1 | awk '{print $1}')
    if [ -n "$win" ] && import -window "$win" -alpha off "$shots/$t-$name.png" 2>/dev/null; then
      echo "captura (ventana $win): $shots/$t-$name.png brillo=$(convert "$shots/$t-$name.png" -format '%[fx:mean]' info: 2>/dev/null)"
    elif import -window root -alpha off "$shots/$t-$name.png" 2>/dev/null; then
      echo "captura (pantalla): $shots/$t-$name.png brillo=$(convert "$shots/$t-$name.png" -format '%[fx:mean]' info: 2>/dev/null)"
    else
      echo "no se pudo capturar $name (¿falta ImageMagick?)"
    fi
  done
  sleep 1
done
wait "$pid"
