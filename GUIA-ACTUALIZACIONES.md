# Guía paso a paso: que Rocío reciba las actualizaciones sola

Esta guía es para dejar andando lo que se explica en
[features/actualizaciones-sin-costo.md](features/actualizaciones-sin-costo.md) (Nivel 1: GitHub
Actions + Firebase App Distribution, gratis). Está escrita para seguirse **de punta a punta sin
saber nada de antemano**. Cuando termines, el flujo va a ser: vos hacés `git push` → a los pocos
minutos Rocío recibe una notificación en el teléfono → toca "Actualizar" → listo.

**Ya está todo el código/configuración lista** (yo ya lo hice): el `build.gradle.kts` tiene el
plugin de Firebase App Distribution armado, y existe un workflow en
`.github/workflows/release.yml` que compila y distribuye automáticamente. Lo que falta son
**pasos manuales tuyos** en sitios web (Firebase, Google Cloud, GitHub) que yo no puedo hacer por
vos porque requieren tu cuenta de Google/GitHub — nadie más que vos puede crearlos.

Vas a terminar con **6 "secrets"** (valores secretos) cargados en GitHub. **Importante:** no hay
que crear ningún secret todavía — eso se hace **una sola vez, todos juntos, en el Paso 7**. Hasta
ahí, cada vez que un paso diga "esto va al secret X", es solo una etiqueta para que sepas qué es:
anotá el valor en un bloc de notas temporal (Notepad, notas del celular, lo que sea) junto con el
nombre del secret, y seguí al paso siguiente. Al llegar al Paso 7 vas a tener las 6 anotaciones
listas para pegar de una.

---

## Paso 1 — Crear el proyecto de Firebase (5 min)

1. Andá a **https://console.firebase.google.com** e iniciá sesión con tu cuenta de Google.
2. Click en **"Agregar proyecto"** (o "Add project").
3. Ponele un nombre, por ejemplo `Fondo Compartido`. Click **Continuar**.
4. Te va a preguntar por Google Analytics — **no hace falta**, podés desactivar el switch. Click
   **Crear proyecto**, esperá que termine, y click **Continuar**.

## Paso 2 — Registrar la app Android dentro del proyecto (3 min)

1. Ya adentro del proyecto, en la pantalla principal vas a ver íconos de plataformas (Web,
   iOS, Android...). Click en el ícono de **Android**.
2. **"Nombre del paquete de Android"**: pegá exactamente esto (tiene que ser idéntico, es el
   identificador único de la app):
   ```
   com.aistudio.ahorrocompartido.pquzx
   ```
3. Los demás campos (apodo, certificado SHA-1) son **opcionales**, dejalos vacíos.
4. Click **Registrar app**.
5. Te va a ofrecer descargar un archivo `google-services.json` — **no hace falta descargarlo ni
   hacer nada con él**, para esta guía no se usa. Click en **Siguiente** hasta el final, y después
   **"Ir a la consola"** (podés saltearte cualquier paso de "agregar el SDK", no aplica).

## Paso 3 — Copiar el "App ID" (2 min)

1. Arriba a la izquierda, click en el **engranaje ⚙️** al lado de "Descripción general del
   proyecto" → **"Configuración del proyecto"**.
2. Bajá hasta la sección **"Tus apps"** → vas a ver la app Android que registraste en el Paso 2.
3. Copiá el valor de **"ID de la app"** — tiene esta forma: `1:123456789012:android:abcabcabcabcabc`.
4. Anotalo en tu bloc de notas con la etiqueta `FIREBASE_APP_ID` (todavía no hay que ir a GitHub —
   eso es recién en el Paso 7).

## Paso 4 — Crear el grupo de testers e invitar a Rocío (3 min)

1. En el menú de la izquierda, buscá **"Release & Monitor" → "App Distribution"**. Click
   **Comenzar / Get started** si te lo pide.
2. Pestaña **"Testers y grupos"** → **"Agregar grupo"** → nombre: `testers` → **Crear**.
3. Adentro del grupo `testers`, click **"Agregar testers"** y escribí el email de Rocío (el que usa
   en su celular/Gmail). Guardar.

   *(Rocío todavía no tiene que hacer nada — la invitación de verdad le llega recién cuando subamos
   la primera versión, en el Paso 8.)*

## Paso 5 — Generar la keystore de firma (solo si todavía no tenés una) (5 min)

Esto es el "certificado" con el que se firman las versiones de release — tiene que ser siempre el
mismo de acá en adelante (si lo perdés, no vas a poder actualizar la app instalada, tendrías que
reinstalarla de cero). **Guardalo en un lugar seguro** (no se comitea al repo).

Abrí una terminal (PowerShell) en cualquier carpeta y corré:

```bash
keytool -genkeypair -v -keystore release-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Te va a preguntar:
- Una **contraseña** para la keystore (elegí una, anotala en tu bloc de notas con la etiqueta
  `RELEASE_STORE_PASSWORD`).
- Después te vuelve a preguntar la contraseña de la clave "upload" — **poné la misma** (más simple)
  → anotala como `RELEASE_KEY_PASSWORD`.
- Nombre, organización, ciudad, etc. — podés poner cualquier cosa o dejar en blanco.

Al terminar vas a tener un archivo `release-upload-key.jks` en esa carpeta. Ahora convertilo a texto
(base64) — en la misma PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-upload-key.jks")) | Set-Clipboard
```

Este comando lo copia directo al portapapeles. Pegalo (Ctrl+V) en tu bloc de notas temporal con la
etiqueta `RELEASE_KEYSTORE_BASE64` (es un texto larguísimo, es normal).

## Paso 6 — Crear las credenciales de servicio (para que GitHub pueda subir a Firebase) (5 min)

1. Andá a **https://console.cloud.google.com** (mismo login de Google) y arriba, en el selector de
   proyecto, elegí el **mismo proyecto** que creaste en el Paso 1 (mismo nombre).
2. Menú ☰ → **"IAM y administración" → "Cuentas de servicio"**.
3. Click **"Crear cuenta de servicio"**. Nombre: `github-actions-distribution`. Click **Crear y continuar**.
4. En **"Rol"**, buscá y seleccioná: **`Firebase App Distribution Admin`**. Click **Continuar** → **Listo**.
5. En la lista, click sobre la cuenta que acabás de crear → pestaña **"Claves"** → **"Agregar clave"**
   → **"Crear clave nueva"** → tipo **JSON** → **Crear**. Se descarga un archivo `.json` solo.
6. Abrí ese archivo descargado con el Bloc de notas, seleccioná todo el contenido (Ctrl+A, Ctrl+C)
   y pegalo en tu bloc de notas temporal con la etiqueta `FIREBASE_SERVICE_ACCOUNT_JSON` (es el JSON
   completo, tal cual, sin recortar nada).

   Si en algún momento Google te pide **"habilitar una API"** (por ejemplo "Firebase Management
   API"), dale **Habilitar** y seguí.

## Paso 7 — Cargar los 6 secrets en GitHub (5 min)

Andá al repo en GitHub: **https://github.com/HamamuraS/personal-finance** → pestaña **Settings**
→ menú izquierdo **"Secrets and variables" → "Actions"** → botón **"New repository secret"**.

Repetí esto **6 veces**, una por cada fila (nombre exacto a la izquierda, el valor que fuiste
guardando a la derecha):

| Nombre exacto del secret | Valor |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | el texto largo en base64 del Paso 5 |
| `RELEASE_STORE_PASSWORD` | la contraseña de la keystore del Paso 5 |
| `RELEASE_KEY_PASSWORD` | la contraseña de la clave "upload" del Paso 5 |
| `FIREBASE_APP_ID` | el "ID de la app" del Paso 3 |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | el contenido completo del .json del Paso 6 |
| `FIREBASE_TESTER_GROUPS` | `testers` (tal cual, es el nombre del grupo del Paso 4) |

## Paso 8 — Probarlo (2 min + esperar)

1. Hacé cualquier cambio chico (o ninguno) y `git push` a `main` — o, más fácil para la primera
   prueba: andá a la pestaña **"Actions"** del repo en GitHub → click en el workflow
   **"Release y distribución a testers"** en la barra izquierda → botón **"Run workflow"** → rama
   `main` → **Run workflow**.
2. Esperá 2-5 minutos. Si el círculo se pone ✅ verde, funcionó. Si se pone ❌ rojo, entrá a ver el
   log — casi siempre es un secret mal pegado (revisá que no haya espacios de más al copiar/pegar).
3. **Rocío**: le va a llegar un email de Firebase invitándola a probar la app (la primera vez). Tiene
   que abrir el link del mail y aceptar — ahí Firebase le puede pedir instalar la app **"Firebase
   App Tester"** (gratis, de Google) para poder recibir el APK e instalarlo con un toque. Una vez
   aceptada la invitación, **las próximas versiones le llegan solas** como notificación.

---

## Resumen de qué es cada secret (por si te perdés)

- `RELEASE_KEYSTORE_BASE64` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD` → para que GitHub
  pueda **firmar** el APK exactamente igual que si lo compilaras vos a mano.
- `FIREBASE_APP_ID` → le dice a Firebase **a qué app** le corresponde este APK.
- `FIREBASE_SERVICE_ACCOUNT_JSON` → la "llave" con la que GitHub se autentica en Firebase para poder
  subir el archivo (sin esto, Firebase rechazaría la subida de un desconocido).
- `FIREBASE_TESTER_GROUPS` → a **quién** avisarle que hay una versión nueva (el grupo con el email
  de Rocío).

## Si algo sale mal

- El error más común es un secret con espacios/saltos de línea de más al copiar y pegar — volvé a
  copiar con cuidado de no incluir nada extra al principio/final.
- Si el log dice algo de "SHA1" o certificado, es probable que estés reusando una keystore vieja con
  otro alias — lo más simple es generar una nueva con el comando del Paso 5 (solo importa que sea
  siempre la misma **de acá en adelante**).
- Cualquier otra cosa, pegame el error del log de GitHub Actions y lo vemos.
