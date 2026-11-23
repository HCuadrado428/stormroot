#!/usr/bin/env bash
# Quilt = el jar de Fabric probado en Quilt Loader (sin QSL): esta prueba instala un servidor Quilt DE VERDAD
# (no el entorno de desarrollo), mete el jar compilado de Fabric en mods/ junto a Fabric API (y Bloomberto si está)
# y pasa el self-test de servidor completo.
#   scripts/selftest-quilt.sh 1.21.11-fabric
set -u
cd "$(dirname "$0")/.."
t="$1"
mc="${t%-fabric}"
echo "== Quilt con el jar de Fabric ($t)"

# Java de cada versión (en la CI, setup-java deja JAVA_HOME_17_X64 / JAVA_HOME_21_X64).
case "$mc" in
  1.20.*) java_bin="${JAVA_HOME_17_X64:-$JAVA_HOME}/bin/java" ;;
  *) java_bin="${JAVA_HOME_21_X64:-$JAVA_HOME}/bin/java" ;;
esac
[ -x "$java_bin" ] || java_bin=java

# 1. El jar del mod (compilado y remapeado, el mismo que se publica).
./gradlew ":$t:build" -q || { echo "$t: no compila"; exit 1; }
jar="$(find "versions/$t/build/libs" -name 'stormroot-*.jar' ! -name '*-sources*' ! -name '*-dev*' ! -name '*-all*' | head -1)"
[ -n "$jar" ] || { echo "$t: no encuentro el jar en versions/$t/build/libs"; ls -R versions/$t/build/libs 2>/dev/null; exit 1; }
echo "jar del mod: $jar"

# 2. Servidor Quilt limpio.
dir="build/quilt-$t"
rm -rf "$dir"
mkdir -p "$dir"
curl -sSfL -o "$dir/quilt-installer.jar" "https://quiltmc.org/api/v1/download-latest-installer/java-universal"
(cd "$dir" && "$java_bin" -jar quilt-installer.jar install server "$mc" --download-server) || { echo "$t: el instalador de Quilt falló"; exit 1; }
srv="$dir/server"
[ -f "$srv/quilt-server-launch.jar" ] || { echo "$t: no está quilt-server-launch.jar"; ls "$dir" "$srv" 2>/dev/null; exit 1; }

# 3. mods/: el mod, Fabric API (la normal de Fabric, sin QSL) y Bloomberto si hay jar de este target.
mkdir -p "$srv/mods"
cp "$jar" "$srv/mods/"
fapi="$(curl -sSfL "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$mc%22%5D&loaders=%5B%22fabric%22%5D" | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["files"][0]["url"])')"
echo "fabric-api: $fapi"
curl -sSfL -o "$srv/mods/fabric-api.jar" "$fapi"
for b in libs/bloomberto/bloomberto-*+"$t".jar; do [ -f "$b" ] && cp "$b" "$srv/mods/" && echo "con Bloomberto: $b"; done

# 4. Mundo plano y self-test (igual que scripts/selftest.sh; sin pausa del servidor vacío, ver CLAUDE.md).
echo "eula=true" > "$srv/eula.txt"
printf 'online-mode=false\nlevel-name=selftest\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-monsters=false\nspawn-protection=0\npause-when-empty-seconds=-1\n' > "$srv/server.properties"

log="$(mktemp)"
(cd "$srv" && timeout 1200 "$java_bin" -Dstormroot.selfTest=server -jar quilt-server-launch.jar nogui) > "$log" 2>&1

grep -E "\[selftest\] (PASS|FAIL|SKIP|.* done)" "$log" | sed -E "s/^.*\[selftest\]/$t (quilt): [selftest]/"
fail=0
grep -q "\[selftest\] server done" "$log" || { fail=1; echo "$t (quilt): no terminó"; grep -E "Exception|ERROR|Caused by|Incompatible|requires|breaks" "$log" | head -30; tail -40 "$log"; }
grep -q "\[selftest\] FAIL" "$log" && fail=1
for crash in "$srv"/crash-reports/*.txt; do [ -f "$crash" ] && { echo "== $crash"; head -70 "$crash"; }; done
[ $fail = 0 ] && echo "$t: Quilt OK" || echo "$t: Quilt FALLÓ"
exit $fail
