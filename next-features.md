
# Hecho (0.7.6)

- **1 · Modo claro rediseñado desde cero.** Dos problemas distintos, dos arreglos.

  El de los íconos: el tema claro/oscuro lo elige el usuario en Ajustes y **no** sigue al del
  sistema, pero `enableEdgeToEdge()` se llamaba sin argumentos, así que Android decidía el color de
  los íconos de sus barras por su cuenta. Teléfono en oscuro + app en claro = íconos blancos sobre
  fondo blanco. Ahora `MainActivity.aplicarEstiloDeBarras` los ata al tema de la app y se vuelve a
  llamar cada vez que el usuario lo cambia.

  El del contraste: el tema claro era el oscuro dado vuelta. Tarjetas blancas con borde `#E2E8F0`
  (1.2:1 sobre blanco, o sea invisible) y **todo** el texto secundario resuelto con
  `onSurface.copy(alpha = 0.5f)`, que sobre blanco da 3.3:1 — por debajo del 4.5:1 de WCAG AA, con
  tipografías de 10-11sp. Se reemplazó por roles Material 3 completos (`surfaceVariant`,
  `onSurfaceVariant`, `outline`, `outlineVariant`, `errorContainer`) elegidos contra su fondo real:
  texto secundario a 9.6:1, texto atenuado a 4.6:1, bordes que existen. Los ~90 `alpha` sobre
  `onSurface`/`onBackground` desaparecieron de las pantallas.

  De paso se fue `val isDark = MaterialTheme.colorScheme.background == DarkBackground`, repetido en
  21 lugares: era cierto de casualidad, y alcanzaba con que el fondo del tema claro coincidiera con
  esa constante para que toda la UI se pintara al revés. Ahora lo publica el tema
  (`LocalIsDarkTheme`), que es el único que lo sabe. El tema oscuro se dejó como estaba.

- **2 · Categoría "Cambio de dinero".** Agregada justo encima de "Otros" en gastos, aportes y
  transferencias. `CategoriasTest` verifica esa posición en las tres listas.

- **3 · Selector de categorías en dos niveles.** El `FlowRow` plano de 16 chips pasó a ser
  `CategoryPicker`, compartido por el alta de movimientos y la de cuotas:

  - **Frecuentes**: las categorías que se vienen usando, derivadas de los propios movimientos
    (`Categorias.recientes`). Resuelve el caso de todos los días sin abrir nada.
  - **Carpetas**: el resto plegado en Comida / Casa / Salud / Transporte / Ocio / Otros. Se abre una
    por vez y se cierra sola al elegir; la que contiene la categoría elegida queda marcada aunque
    esté cerrada, y la elegida se antepone siempre a los frecuentes para que nunca desaparezca de
    vista.

  Las listas planas siguen siendo la fuente de verdad y los grupos son una vista sobre ellas: el
  orden plano define el default de cada tipo y la posición de "Otros", y derivar la lista de los
  grupos habría cambiado en silencio la categoría por defecto de un gasto nuevo. `CategoriasTest`
  verifica que la unión de las carpetas sea **exactamente** el catálogo de gastos: una categoría
  nueva sin carpeta rompe el test en vez de quedar inalcanzable en la UI. Los tipos con listas
  cortas (aportes, transferencias, condonaciones) se siguen mostrando planos.

- **4 · Retroceso de Android.** `MainActivity` lleva una pila de pestañas visitadas y un
  `BackHandler` que la desapila. La app no usa Navigation-Compose (las pantallas son un `when` sobre
  la pestaña actual), así que el back stack va a mano. Deshabilitado en la raíz, para que desde
  Inicio sin historial el retroceso siga cerrando la app en vez de quedar atrapado. La expulsión
  automática de "Nuevo" al mirar un mes cerrado **no** se apila: no es navegación del usuario, y
  retroceder a una pestaña que ya no existe no tendría sentido. Los `BackHandler` internos de Cuotas
  (detalle, formulario, resumen de tarjeta) siguen ganando por estar más adentro.

- **5 · Versión visible en Ajustes.** `versionName = "0.7.6"` / `versionCode = 76` en Gradle, y el
  pie de Configuración la muestra desde `BuildConfig.VERSION_NAME`. Sale de ahí y no de una constante
  a mano para que lo que se ve en el teléfono sea siempre lo que se compiló.

- **6 · Detección del monto desde las notificaciones de las billeteras.** Documentada aparte en
  `features/deteccion-montos-notificaciones.md`: alcance (solo el importe), allowlist de packages,
  parser con tests, deduplicación, modo descubrimiento, divulgación previa y el estado de compliance
  con Play.

# Pendiente

- **Bloqueante real para publicar en Play.** `AppConfig.SCRIPT_URL` es un endpoint sin autenticación
  horneado en el APK que permite leer y escribir la planilla entera a quien lo tenga, y la app está
  armada alrededor de dos personas concretas. Nada de lo de arriba lo toca.

- **Play Protect bloquea la instalación desde que existe el listener de notificaciones.** El
  mensaje en el teléfono es "se bloqueó la app para proteger tu dispositivo". No es un error del
  build: una app instalada por fuera de Play que declara un `NotificationListenerService` cae en la
  heurística de troyano bancario, porque leer las notificaciones del banco es exactamente la técnica
  que usa ese malware. Play Protect mira la forma, no la intención.

  Se puede instalar igual (*Más detalles* → *Instalar de todos modos*, o apagando el escaneo un
  momento desde Play Store → Play Protect), pero es fricción en **cada** instalación mientras la
  distribución sea por Firebase App Distribution, y además anticipa revisión manual el día que se
  publique en Play. Tres caminos, ninguno obvio: convivir con los dos toques extra, mover el
  servicio a un flavor aparte para que el APK por defecto no lo declare, o bajar la feature —
  el resto de la 0.7.6 no la necesita. Sin decidir.

- **Confirmar los package names de las billeteras.** La lista de
  `BilleterasNotificationListener.PAQUETES_BANCARIOS` es un punto de partida: los nombres cambian
  entre versiones y países. El modo descubrimiento de Ajustes existe para verificarlos con un
  movimiento real de cada app y agregar los que falten.
