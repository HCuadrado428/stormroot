#!/usr/bin/env bash
# Self-test de Stormroot dentro del juego (ver src/main/java/.../selftest/SelfTest.java).
#   scripts/selftest.sh server 1.21.1-fabric [más targets...]   servidor dedicado
#   scripts/selftest.sh client 1.21.1-fabric [más targets...]   un jugador: cliente + servidor integrado (necesita xvfb-run)
# Muestra las líneas "[selftest] ..." de cada target y termina con error si algo falla o no llega a terminar.
# Mundo de pruebas: plano (rápido y siempre igual); la zona de pruebas la construye el propio SelfTest.
set -u
cd "$(dirname "$0")/.."
side="$1"; shift
run="run"
fail=0

server_props() {
  mkdir -p "$run"
  echo "eula=true" > "$run/eula.txt"
  printf 'online-mode=false\nlevel-name=selftest\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-monsters=false\nspawn-protection=0\n' > "$run/server.properties"
}

for t in "$@"; do
  log="$(mktemp)"
  echo "== $t ($side)"
  if [ "$side" = server ]; then
    server_props
    rm -rf "$run/selftest"
    JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dstormroot.selfTest=server" \
      timeout 1200 ./gradlew ":$t:runServer" --args=nogui > "$log" 2>&1
  else
    # El cliente abre el mundo "testworld": se crea con un servidor de un momento (mismo mundo plano).
    server_props
    rm -rf "$run/selftest" "$run/saves/testworld"
    JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dstormroot.selfTest=server" \
      timeout 1200 ./gradlew ":$t:runServer" --args=nogui > /dev/null 2>&1
    mkdir -p "$run/saves" && cp -r "$run/selftest" "$run/saves/testworld"
    JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dstormroot.selfTest=client" \
      LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 MESA_GLSL_VERSION_OVERRIDE=450 \
      timeout 1500 xvfb-run -a -s "-screen 0 1280x720x24" \
      ./gradlew ":$t:runClient" --args="--quickPlaySingleplayer testworld" > "$log" 2>&1
  fi
  grep -E "\[selftest\] (PASS|FAIL|SKIP|.* done|cueva)" "$log" | sed -E "s/^.*\[selftest\]/$t: [selftest]/"
  ok=1
  grep -q "\[selftest\] server done" "$log" || { ok=0; echo "$t: el self-test del servidor no terminó"; }
  if [ "$side" != server ] && ! grep -q "\[selftest\] client done" "$log"; then ok=0; echo "$t: el self-test del cliente no terminó"; fi
  grep -q "\[selftest\] FAIL" "$log" && ok=0
  if [ $ok = 1 ]; then
    echo "$t: OK"
  else
    fail=1
    echo "$t: FALLÓ"
    mkdir -p build/selftest-logs && cp "$log" "build/selftest-logs/$t-$side.log"
    if [ -n "${CI:-}" ]; then grep -E "\[selftest\]|Exception|ERROR|Caused by" "$log" | tail -60; tail -40 "$log"; fi
  fi
done
exit $fail
