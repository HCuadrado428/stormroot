#!/usr/bin/env bash
# Corre DENTRO de xvfb-run (lo llama scripts/selftest.sh): lanza el cliente de un jugador y, cada vez que el self-test
# escribe "[selftest] SCREENSHOT <nombre>", captura la pantalla virtual en build/selftest-shots/<target>-<nombre>.png.
# Uso: scripts/selftest-client-xvfb.sh <target> <log>
t="$1"; log="$2"
# Con RENDER_MODS=sodium,iris el cliente arranca con esos mods y las capturas llevan "+sodium+iris" en el nombre.
tag="${RENDER_MODS:++${RENDER_MODS//,/+}}"
shots="build/selftest-shots"
mkdir -p "$shots"
# Sodium comprueba que LWJGL sea la de Minecraft; el entorno de desarrollo de 1.20.1 trae otra (3.3.2-snapshot) y
# Sodium no arrancaría. Los jugadores sí tienen la buena, así que aquí se salta esa comprobación.
[ -n "${RENDER_MODS:-}" ] && export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dsodium.checks.issue2561=false"
./gradlew ":$t:runClient" ${RENDER_MODS:+-Pstormroot.renderMods=$RENDER_MODS} --args="--quickPlaySingleplayer testworld" > "$log" 2>&1 &
pid=$!
taken=" "
used="$(mktemp)"
ls run/screenshots/*.png 2>/dev/null > "$used"
while kill -0 "$pid" 2>/dev/null; do
  for name in $(grep -oE "\[selftest\] SCREENSHOT [a-z_]+" "$log" 2>/dev/null | awk '{print $3}'); do
    case "$taken" in *" $name "*) continue ;; esac
    taken="$taken$name "
    # El self-test hace la captura con la función del propio juego (capturar la pantalla virtual desde fuera sale
    # negro) justo antes de escribir la marca; se espera al archivo nuevo de run/screenshots/ y se copia con nombre.
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      sleep 1
      newest=$(ls -t run/screenshots/*.png 2>/dev/null | head -n 1)
      [ -n "$newest" ] && ! grep -qx "$newest" "$used" && break
    done
    if [ -n "$newest" ] && ! grep -qx "$newest" "$used"; then
      echo "$newest" >> "$used"
      convert "$newest" -alpha off "$shots/$t$tag-$name.png"
      echo "captura: $shots/$t$tag-$name.png brillo=$(convert "$shots/$t$tag-$name.png" -format '%[fx:mean]' info:)"
    else
      echo "no se pudo capturar $name (el juego no guardó la captura)"
    fi
  done
  sleep 1
done
wait "$pid"
