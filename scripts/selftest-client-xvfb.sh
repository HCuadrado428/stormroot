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
    if import -window root "$shots/$t-$name.png" 2>/dev/null; then
      echo "captura: $shots/$t-$name.png"
    else
      echo "no se pudo capturar $name (¿falta ImageMagick?)"
    fi
  done
  sleep 1
done
wait "$pid"
