package com.example.golden

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.MainActivity
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas "de oro" de todas las pantallas, con datos fijos ([GoldenFixture]) en modo demo.
 *
 * No verifican nada por sí solas: se corren con `-Proborazzi.test.record=true` sobre dos versiones
 * del código (antes y después de un refactor) y se comparan las imágenes píxel a píxel. Ventana alta
 * a propósito, para que las listas entren enteras en una sola captura.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h2000dp-xhdpi")
class PantallasGoldenTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() {
        try {
            WorkManager.initialize(context, Configuration.Builder().build())
        } catch (_: IllegalStateException) {
            // Ya inicializado por el proveedor de androidx.startup.
        }
    }

    private var escenario: ActivityScenario<MainActivity>? = null

    @After
    fun cerrar() {
        escenario?.close()
    }

    private fun arrancar(usuario: String, oscuro: Boolean, conIdentidad: Boolean = true) {
        GoldenFixture.sembrar(context, usuario, oscuro, conIdentidad)
        // Reloj de Compose manual: las animaciones infinitas (spinners) no traban la espera y cada
        // captura sale siempre en el mismo instante de la animación.
        compose.mainClock.autoAdvance = false
        escenario = ActivityScenario.launch(MainActivity::class.java)
        if (conIdentidad) {
            try {
                compose.waitUntil(15_000) {
                    compose.mainClock.advanceTimeBy(100)
                    compose.onAllNodesWithText("Pozo Común Total").fetchSemanticsNodes().isNotEmpty() &&
                        compose.onAllNodesWithText("Feria", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
            } catch (e: Throwable) {
                captureScreenRoboImage("build/golden/DEBUG_timeout.png", RoborazziOptions())
                val roots = compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes()
                java.io.File("build/golden/DEBUG_tree.txt").writeText(
                    "roots=${roots.size} chosen=${com.example.data.PreferencesHelper(context).hasChosenIdentity}\n" +
                        compose.onAllNodes(androidx.compose.ui.test.isRoot()).let { n ->
                            (0 until roots.size).joinToString("\n----\n") { i -> n[i].printToString(5) }
                        }
                )
                throw e
            }
        }
        compose.waitForIdle()
    }

    private fun foto(nombre: String) {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        captureScreenRoboImage("build/golden/$nombre.png", RoborazziOptions())
    }

    private fun tab(nombre: String) {
        compose.onNodeWithContentDescription(nombre, useUnmergedTree = true).performClick()
        avanzar()
    }

    private fun click(texto: String, substring: Boolean = false) {
        try {
            val nodos = compose.onAllNodesWithText(texto, substring = substring)
            if (nodos.fetchSemanticsNodes().isNotEmpty()) nodos.onFirst().performClick()
            else compose.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).onFirst().performClick()
        } catch (e: Throwable) {
            captureScreenRoboImage("build/golden/DEBUG_click.png", RoborazziOptions())
            throw e
        }
        avanzar()
    }

    private fun avanzar() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /**
     * El diálogo de pago de cuota no llega nunca a "idle" bajo Robolectric (su campo de texto
     * mantiene trabajo pendiente), así que se captura sin esperar y se cierra con la tecla atrás.
     */
    private fun dialogoDePago(boton: String, nombre: String) {
        compose.onAllNodesWithText(boton).onFirst().performClick()
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        captureScreenRoboImage("build/golden/$nombre.png", RoborazziOptions())
        compose.onAllNodesWithText("Cancelar").onFirst().performClick()
        repeat(20) { compose.mainClock.advanceTimeByFrame() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    private fun sufijo(usuario: String, oscuro: Boolean) =
        (if (usuario == "Santiago") "santi" else "ro") + if (oscuro) "_oscuro" else "_claro"

    private fun recorridoInicio(usuario: String, oscuro: Boolean) {
        arrancar(usuario, oscuro)
        val s = sufijo(usuario, oscuro)
        foto("inicio_$s")
        click("Cena de cumpleaños", substring = true)
        foto("inicio_detalle_$s")
        click("Cerrar")
        click("Feria", substring = true)
        foto("inicio_detalle_otro_$s")
        click("Cerrar")
        click("Gastos")
        click("Categorías")
        foto("inicio_filtro_gastos_$s")
        click("Servicios")
        foto("inicio_filtro_categoria_$s")
    }

    private fun recorridoNuevo(usuario: String, oscuro: Boolean) {
        arrancar(usuario, oscuro)
        val s = sufijo(usuario, oscuro)
        tab("Nuevo")
        foto("nuevo_gasto_$s")
        click("Común (50/50)")
        foto("nuevo_gasto_comun_$s")
        click("Aporte")
        foto("nuevo_aporte_$s")
        click("Transf.")
        foto("nuevo_transf_$s")
        click("Cambio")
        foto("nuevo_cambio_$s")
        click("Con otra persona")
        foto("nuevo_cambio_propio_$s")
        click("Saldo externo")
        foto("nuevo_saldo_externo_$s")
    }

    private fun recorridoCuotas(usuario: String, oscuro: Boolean) {
        arrancar(usuario, oscuro)
        val s = sufijo(usuario, oscuro)
        tab("Cuotas")
        foto("cuotas_lista_$s")
        click("Todos")
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        avanzar()
        foto("cuotas_todos_pagados_$s")
        click("Notebook")
        foto("cuotas_detalle_note_$s")
        if (usuario == "Santiago") dialogoDePago("Pagar", "cuotas_pagar_note_$s")
        compose.onNodeWithContentDescription("Volver").performClick()
        avanzar()
        click("Todos")
        click("Bici")
        foto("cuotas_detalle_bici_$s")
        if (usuario == "Santiago") {
            dialogoDePago("Pagar", "cuotas_pagar_bici_$s")
            compose.onNodeWithContentDescription("Editar").performClick()
            avanzar()
            foto("cuotas_editar_bici_$s")
        }
        compose.onNodeWithContentDescription("Volver").performClick()
        avanzar()
        click("Nueva compra")
        foto("cuotas_nueva_$s")
        compose.onNodeWithContentDescription("Volver").performClick()
        avanzar()
        if (usuario == "Santiago") {
            click("Pagar Visa Santiago")
            foto("cuotas_tarjeta_$s")
        }
    }

    private fun recorridoMetricas(usuario: String, oscuro: Boolean) {
        arrancar(usuario, oscuro)
        val s = sufijo(usuario, oscuro)
        tab("Métricas")
        foto("metricas_$s")
        click("Casa", substring = true)
        foto("metricas_rubro_$s")
    }

    private fun recorridoAjustes(usuario: String, oscuro: Boolean) {
        arrancar(usuario, oscuro)
        tab("Ajustes")
        foto("ajustes_${sufijo(usuario, oscuro)}")
    }

    @Test fun inicioSantiClaro() = recorridoInicio("Santiago", false)
    @Test fun inicioRoOscuro() = recorridoInicio("Rocío", true)
    @Test fun nuevoSantiClaro() = recorridoNuevo("Santiago", false)
    @Test fun nuevoRoOscuro() = recorridoNuevo("Rocío", true)
    @Test fun cuotasSantiClaro() = recorridoCuotas("Santiago", false)
    @Test fun cuotasRoOscuro() = recorridoCuotas("Rocío", true)
    @Test fun metricasSantiClaro() = recorridoMetricas("Santiago", false)
    @Test fun metricasRoOscuro() = recorridoMetricas("Rocío", true)
    @Test fun ajustesSantiClaro() = recorridoAjustes("Santiago", false)
    @Test fun ajustesRoOscuro() = recorridoAjustes("Rocío", true)

    @Test
    fun identidad() {
        arrancar("Santiago", oscuro = false, conIdentidad = false)
        foto("identidad_claro")
    }

    @Test
    fun identidadOscuro() {
        arrancar("Santiago", oscuro = true, conIdentidad = false)
        foto("identidad_oscuro")
    }
}
