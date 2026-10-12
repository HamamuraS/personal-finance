#!/bin/bash
# Compara el comportamiento del Apps Script de la copia de trabajo contra el de un commit (por
# defecto HEAD), corriendo los dos en el entorno simulado de apps-script/ con el mismo escenario.
#
# Uso: tools/verificacion/comparar_script.sh [commit]
# Sale con 0 si se comportan igual; si no, muestra el primer paso distinto y lista los demás.
set -e
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
REF="${1:-HEAD}"
TMP="$(mktemp -d)"
git -C "$RAIZ" show "$REF:google-apps-script.js" > "$TMP/antes.js"
node "$RAIZ/tools/verificacion/apps-script/harness.js" "$TMP/antes.js" "$TMP/antes.json"
node "$RAIZ/tools/verificacion/apps-script/harness.js" "$RAIZ/google-apps-script.js" "$TMP/despues.json"
node "$RAIZ/tools/verificacion/apps-script/compare.js" "$TMP/antes.json" "$TMP/despues.json"
