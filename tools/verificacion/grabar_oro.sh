#!/bin/bash
# Graba las pruebas "de oro" de una copia del proyecto: las capturas de todas las pantallas y el
# registro del repositorio contra un Web App simulado. Quedan en <raiz>/app/build/golden/.
#
# Uso: tools/verificacion/grabar_oro.sh <raiz-del-proyecto>
#      TESTS="cuotasSantiClaro" tools/verificacion/grabar_oro.sh <raiz>   (solo algunos recorridos)
#
# Cada recorrido corre en su propia JVM: Robolectric arrastra estado entre tests de la misma corrida
# y las capturas salían de la pantalla del test anterior.
cd "$1" || exit 1
# Robolectric con SDK 36 pide Java 21.
export JAVA_HOME="${JAVA_HOME_21:-/c/Program Files/Java/jdk-21}"
[ -z "$TESTS" ] && rm -rf app/build/golden
mkdir -p app/build
fallos=0
for t in ${TESTS:-inicioSantiClaro inicioRoOscuro nuevoSantiClaro nuevoRoOscuro cuotasSantiClaro cuotasRoOscuro metricasSantiClaro metricasRoOscuro ajustesSantiClaro ajustesRoOscuro identidad identidadOscuro}; do
  ./gradlew --console=plain -q testDebugUnitTest --rerun -Pgolden --tests "com.example.golden.PantallasGoldenTest.$t" -Proborazzi.test.record=true > "app/build/golden-$t.log" 2>&1 \
    || { echo "FALLÓ $t (ver app/build/golden-$t.log)"; fallos=1; }
done
./gradlew --console=plain -q testDebugUnitTest --rerun -Pgolden --tests "com.example.golden.RepositorioGoldenTest" > app/build/golden-repo.log 2>&1 \
  || { echo "FALLÓ el registro del repositorio (ver app/build/golden-repo.log)"; fallos=1; }
echo "$(ls app/build/golden 2>/dev/null | wc -l) archivos en app/build/golden"
exit $fallos
