#!/usr/bin/env bash
# Prueba multijugador: un servidor dedicado y dos clientes (Alba y Berto) a la vez, cada uno en su pantalla virtual.
#   scripts/selftest-mp.sh 1.21.11-fabric
# Servidor: selftest/MultiplayerSelfTest (-Dstormroot.selfTest=mp). Clientes: client/MultiplayerClientSelfTest
# (-Dstormroot.selfTest=mp-client). El segundo cliente no puede ser otro Gradle (se bloquearían): se arranca con la
# misma orden que runClient (tarea stormrootClientCommand), en su propia carpeta.
set -u
cd "$(dirname "$0")/.."
t="$1"
mkdir -p build/selftest-logs build/selftest-shots
slog=build/selftest-logs/$t-mp-server.log
fail=0

# Mundo plano, sin autenticación (los clientes de desarrollo no tienen cuenta).
rm -rf run/selftest run-a run-b
mkdir -p run
echo "eula=true" > run/eula.txt
printf 'online-mode=false\nenforce-secure-profile=false\nlevel-name=selftest\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-monsters=false\nspawn-protection=0\nview-distance=6\n' > run/server.properties

timeout 1200 ./gradlew ":$t:stormrootClientCommand" || { echo "$t: no se pudo preparar el cliente"; exit 1; }
cmdfile="versions/$t/build/client-command.txt"
mapfile -t cmd < "$cmdfile"
# Variables de entorno del run original (NeoForge/Forge pasan así las clases del mod), menos las de la máquina.
client_env() {
  while IFS= read -r line; do
    case "$line" in
      PATH=*|HOME=*|PWD=*|SHELL=*|DISPLAY=*|XAUTHORITY=*|TMPDIR=*|TEMP=*|TMP=*|JAVA_TOOL_OPTIONS=*|_=*|OLDPWD=*|SHLVL=*) ;;
      [A-Za-z_]*=*) export "$line" ;;
    esac
  done < "versions/$t/build/client-env.txt"
}

JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dstormroot.selfTest=mp" \
  timeout 1500 ./gradlew ":$t:runServer" --args=nogui > "$slog" 2>&1 &
server=$!
for _ in $(seq 1 600); do
  grep -q "Done (" "$slog" 2>/dev/null && break
  kill -0 "$server" 2>/dev/null || break
  sleep 1
done
grep -q "Done (" "$slog" || { echo "$t: el servidor no arrancó"; tail -40 "$slog"; exit 1; }

client() {
  local name="$1" dir="$2" log="$3"
  mkdir -p "$dir"
  (
    cd "$dir"
    client_env
    JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dstormroot.selfTest=mp-client" \
      LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 MESA_GLSL_VERSION_OVERRIDE=450 \
      timeout 1200 xvfb-run -a -s "-screen 0 854x480x24" \
      "${cmd[@]}" --username "$name" --quickPlayMultiplayer localhost:25565 > "$log" 2>&1
  )
}
client Alba "$PWD/run-a" "$PWD/build/selftest-logs/$t-mp-alba.log" &
a=$!
sleep 20
client Berto "$PWD/run-b" "$PWD/build/selftest-logs/$t-mp-berto.log" &
b=$!
wait $a $b
wait $server

for log in "$slog" build/selftest-logs/$t-mp-alba.log build/selftest-logs/$t-mp-berto.log; do
  who=$(basename "$log" .log)
  grep -E "\[selftest\] (PASS|FAIL|SKIP|.* done)" "$log" | sed -E "s/^.*\[selftest\]/$who: [selftest]/"
  grep -q "\[selftest\] mp .* done" "$log" || { fail=1; echo "$who: no terminó"; grep -E "Exception|ERROR|Caused by" "$log" | head -20; tail -30 "$log"; }
  grep -q "\[selftest\] FAIL" "$log" && fail=1
done

# Capturas de cada cliente (el juego las guarda en screenshots/ de su carpeta) al log de la CI, como en selftest.sh.
for dir in run-a run-b; do
  for png in "$dir"/screenshots/*.png; do
    [ -f "$png" ] || continue
    who=$([ "$dir" = run-a ] && echo alba || echo berto)
    out="build/selftest-shots/$t-mp-$who-$(basename "$png")"
    cp "$png" "$out"
    if [ -n "${CI:-}" ] && command -v convert > /dev/null; then
      echo "SHOT-B64 $t-mp-$who-$(basename "$png" .png) $(convert "$png" -alpha off -resize 640x -quality 70 jpg:- | base64 -w0)"
    fi
  done
done
for crash in run/crash-reports/*.txt run-a/crash-reports/*.txt run-b/crash-reports/*.txt; do [ -f "$crash" ] && { echo "== $crash"; head -70 "$crash"; }; done

[ $fail = 0 ] && echo "$t: multijugador OK" || echo "$t: multijugador FALLÓ"
exit $fail
