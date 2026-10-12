# Verificación de refactors

Herramientas para comprobar que un cambio interno **no cambia lo que ve el usuario**. Se armaron
para la refactorización de la v8.2 y sirven para cualquier refactor futuro. No forman parte del
build ni de los tests normales.

Todas comparan **dos versiones del código** (la de un commit contra la copia de trabajo), así que
no hay ningún resultado "esperado" guardado que mantener al día.

## 1. Pantallas y repositorio (Android)

`app/src/test/java/com/example/golden/`:

- **`PantallasGoldenTest`**: arranca la app en Modo Local con datos fijos (`GoldenFixture`),
  recorre todas las pantallas en los dos usuarios y en claro/oscuro, y saca una captura de cada
  paso.
- **`RepositorioGoldenTest`**: corre el repositorio contra un Web App simulado (MockWebServer).
  Registra los pedidos exactos (URL y cuerpo), lo que devuelve cada método y cómo quedan los
  caches.

Corren solo con `-Pgolden`, porque graban en vez de verificar. Para comparar el commit actual contra
la copia de trabajo:

```bash
git worktree add ../pf-antes HEAD
cp .env local.properties ../pf-antes/
cp -r app/src/test/java/com/example/golden ../pf-antes/app/src/test/java/com/example/   # si el commit no los tiene
tools/verificacion/grabar_oro.sh ../pf-antes
tools/verificacion/grabar_oro.sh .
python tools/verificacion/comparar_capturas.py ../pf-antes/app/build/golden app/build/golden
diff ../pf-antes/app/build/golden/repo.txt app/build/golden/repo.txt
git worktree remove ../pf-antes
```

- **`comparar_capturas.py`** compara píxel a píxel. Ignora las diferencias de muy baja intensidad
  (umbral 40 de 255): el rastro de la animación de un toque varía entre corridas aunque el código
  sea el mismo. Una diferencia real de layout o de color queda por encima del umbral.
- **`lado_a_lado.py <dirA> <dirB> <archivo.png> <salida.png> [y0 y1]`** arma una imagen con las
  dos versiones y la máscara de diferencias, para mirar qué cambió.
- Requisitos: Java 21 (Robolectric con SDK 36), Python con Pillow.
- Los datos dependen del mes en curso: grabar las dos versiones el mismo día.
- Si el commit viejo tiene otra API del repositorio, `RepositorioGoldenTest` puede necesitar
  ajustes en esa copia.

## 2. Apps Script

`apps-script/` es un entorno simulado de Apps Script en Node: una planilla en memoria y mocks de
`SpreadsheetApp`, `LockService`, `DriveApp`, `UrlFetchApp` (Gemini), triggers y propiedades, con el
reloj fijo. `scenario.js` ejercita todos los `doGet`/`doPost` y todas las funciones del editor y de
los triggers. Después de cada paso registra la respuesta, la planilla entera, los logs, los
candados y las llamadas externas.

```bash
tools/verificacion/comparar_script.sh          # contra HEAD
tools/verificacion/comparar_script.sh v8.1     # contra otro commit
```

Hace falta solo Node (sin `npm install`). `node apps-script/compare.js a.json b.json --all` da el
detalle de cada paso distinto.

Si una diferencia aparece en un paso, la planilla de los pasos siguientes también difiere: hay que
leer las diferencias desde la primera.
