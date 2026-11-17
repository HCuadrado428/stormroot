#!/usr/bin/env bash
# Compila Bloomberto (commit fijado en bloomberto.ref) y copia sus jars a libs/bloomberto/.
#
# Bloomberto es OPCIONAL: sin estos jars Stormroot compila igual, pero sin la integración
# (config con pantalla, efectos de tormenta, temblor de cámara, indicador del clima).
#
# Uso:   scripts/fetch-bloomberto.sh            (desde la raíz del repo)
# Java:  Bloomberto se compila con JDK 21. Si tu JAVA_HOME es otro, exporta BLOOMBERTO_JAVA_HOME.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REF="$(tr -d '[:space:]' < "$ROOT/bloomberto.ref")"
REPO="${BLOOMBERTO_REPO:-https://github.com/HCuadrado428/Bloomberto.git}"
OUT="$ROOT/libs/bloomberto"
WORK="$ROOT/build/bloomberto-src"
# Targets de Bloomberto que coinciden con los de Stormroot (Bloomberto no tiene 1.21.11).
TARGETS=(1.20.1-fabric 1.20.1-forge 1.21.1-fabric 1.21.1-neoforge)

if [ -f "$OUT/.ref" ] && [ "$(cat "$OUT/.ref")" = "$REF" ]; then
    echo "Bloomberto $REF ya está en libs/bloomberto/"
    exit 0
fi

export JAVA_HOME="${BLOOMBERTO_JAVA_HOME:-${JAVA_HOME_21_X64:-${JAVA_HOME:-}}}"
echo "Compilando Bloomberto $REF con JAVA_HOME=${JAVA_HOME:-<por defecto>}"

rm -rf "$WORK"
mkdir -p "$WORK"
git -C "$WORK" init -q
git -C "$WORK" fetch -q --depth 1 "$REPO" "$REF"
git -C "$WORK" checkout -q FETCH_HEAD

tasks=()
for t in "${TARGETS[@]}"; do tasks+=(":$t:remapJar"); done
(cd "$WORK" && chmod +x gradlew && ./gradlew "${tasks[@]}" --no-daemon)

rm -rf "$OUT"
mkdir -p "$OUT"
for t in "${TARGETS[@]}"; do
    jar="$(find "$WORK/versions/$t/build/libs" -maxdepth 1 -name "bloomberto-*+$t.jar" | head -n 1)"
    if [ -z "$jar" ]; then
        echo "No se encontró el jar de Bloomberto para $t" >&2
        exit 1
    fi
    cp "$jar" "$OUT/"
done
echo "$REF" > "$OUT/.ref"
ls -l "$OUT"
