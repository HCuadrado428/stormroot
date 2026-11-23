#!/usr/bin/env bash
# Compatibilidad con mundos guardados por versiones anteriores del mod:
#   scripts/selftest-oldworld.sh 1.21.11-fabric
# 1. Construye el mod en el commit apuntado por compat.ref (worktree en build/oldworld-src) y corre su self-test de
#    servidor, que genera y guarda el mundo run/selftest con frentes, filtración y redes de micelio.
# 2. Abre ESE mundo con el código actual en modo -Dstormroot.selfTest=oldworld (selftest/OldWorldSelfTest):
#    los datos guardados deben cargar y la simulación seguir andando.
set -u
cd "$(dirname "$0")/.."
t="$1"
ref="$(cat compat.ref)"
echo "== mundo guardado por $ref, abierto por $(git rev-parse --short HEAD) ($t)"

# El checkout de la CI es superficial: hay que traer el commit viejo.
git rev-parse -q --verify "$ref^{commit}" > /dev/null 2>&1 || git fetch -q --depth 1 origin "$ref"
git worktree remove -f build/oldworld-src 2>/dev/null || true
rm -rf build/oldworld-src
git worktree add -f -q build/oldworld-src "$ref"

(
  cd build/oldworld-src
  chmod +x gradlew scripts/fetch-bloomberto.sh scripts/selftest.sh
  scripts/fetch-bloomberto.sh
  scripts/selftest.sh server "$t"
) || { echo "$t: el self-test del commit viejo falló (el mundo no vale)"; exit 1; }

# El mundo viejo, al código actual.
mkdir -p run
rm -rf run/selftest
cp -r build/oldworld-src/run/selftest run/selftest
rm -f run/selftest/session.lock
echo "eula=true" > run/eula.txt
printf 'online-mode=false\nlevel-name=selftest\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-monsters=false\nspawn-protection=0\n' > run/server.properties

log="$(mktemp)"
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dstormroot.selfTest=oldworld" \
  timeout 1200 ./gradlew ":$t:runServer" --args=nogui > "$log" 2>&1

grep -E "\[selftest\] (PASS|FAIL|oldworld)" "$log" | sed -E "s/^.*\[selftest\]/$t: [selftest]/"
fail=0
grep -q "\[selftest\] oldworld done" "$log" || { fail=1; echo "$t: no terminó"; grep -E "Exception|ERROR|Caused by" "$log" | head -20; tail -40 "$log"; }
grep -q "\[selftest\] FAIL" "$log" && fail=1
for crash in run/crash-reports/*.txt; do [ -f "$crash" ] && { echo "== $crash"; head -70 "$crash"; }; done
[ $fail = 0 ] && echo "$t: mundo antiguo OK" || echo "$t: mundo antiguo FALLÓ"
exit $fail
