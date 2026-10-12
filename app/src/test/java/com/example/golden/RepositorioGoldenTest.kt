package com.example.golden

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AhorroRepository
import com.example.data.ImageInfo
import com.example.data.PreferencesHelper
import com.example.data.Usuario
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Caracterización del repositorio contra un Web App simulado: registra qué pedidos salen (URL y
 * cuerpo exactos), qué devuelve cada método y cómo quedan los caches. Se corre sobre dos versiones
 * del código y se comparan los `build/golden/repo.txt` resultantes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepositorioGoldenTest {

    private lateinit var server: MockWebServer
    private val log = StringBuilder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    private fun ok(json: String) = server.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))
    private fun fail(code: Int) = server.enqueue(MockResponse().setResponseCode(code).setBody("<html>error</html>"))

    private fun pedidos() {
        while (server.requestCount > 0) {
            val r = server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            log.append("  >> ${r.method} ${r.path}\n")
            val body = r.body.readUtf8()
            if (body.isNotEmpty()) log.append("     $body\n")
        }
    }

    private fun paso(nombre: String, prefs: PreferencesHelper, bloque: suspend () -> Any?) {
        val res = try { runBlocking { bloque() } } catch (e: Throwable) { "EXCEPCION ${e.javaClass.simpleName}: ${e.message}" }
        log.append("## $nombre\n  = $res\n")
        pedidos()
        log.append("  cache.movs  = ${prefs.getSheetsCache()}\n")
        log.append("  cache.plans = ${prefs.getPlansCache()}\n")
        log.append("  cache.users = ${prefs.getUsersCache()}\n")
        log.append("  local.movs  = ${prefs.getLocalMovements().map { it.id + ":" + it.eliminado }}\n")
        log.append("  local.plans = ${prefs.getLocalPlans().map { it.id + ":" + it.eliminado }}\n")
        log.append("  fetchFresco = ${prefs.lastFetchAt > 0}\n")
    }

    private val movsJson = """{"status":"SUCCESS","data":[
        {"id":"a1","fecha":"2026-10-01 08:00","monto":100.5,"tipo":"Aporte","categoria":"Ingreso","responsable":"Santiago","esComun":false,"propietario":"Santiago","descripcion":"x","metodoPago":"Billetera Virtual","ticketUrl":"","eliminado":false,"planId":"","cuotaNumero":0,"evitable":false},
        {"id":"g1","fecha":"Wed Sep 30 2026 00:00:00 GMT-0300","monto":50,"tipo":"Gasto","categoria":"Otros","responsable":"Rocío","esComun":true,"propietario":"Ambos","descripcion":"","metodoPago":"Efectivo","ticketUrl":"","eliminado":false,"planId":"p1","cuotaNumero":2,"evitable":true}
    ]}"""

    private val plansJson = """{"status":"SUCCESS","plans":[
        {"id":"p1","fechaCreacion":"Wed Jul 01 2026 10:30:00 GMT-0300","descripcion":"Tele","montoPorCuota":1000,"cantidadCuotas":3,"fechaPrimeraCuota":"Sat Aug 01 2026 00:00:00 GMT-0300","propietario":"Rocío","categoria":"Otros","tarjeta":"BBVA Rocío","eliminado":false,"cuotasPagadasPrevias":[1],"evitable":false},
        {"id":"p2","fechaCreacion":"2026-09-10 09:00","descripcion":"Silla","montoPorCuota":500,"cantidadCuotas":2,"fechaPrimeraCuota":"2026-09","propietario":"Santiago","categoria":"Utilería","tarjeta":"","eliminado":false,"cuotasPagadasPrevias":[],"evitable":true},
        {"id":"p2","fechaCreacion":"2026-09-10 09:00","descripcion":"Silla dup","montoPorCuota":500,"cantidadCuotas":2,"fechaPrimeraCuota":"2026-09","propietario":"Santiago","categoria":"Utilería","tarjeta":"","eliminado":false,"cuotasPagadasPrevias":[],"evitable":true}
    ]}"""

    @Test
    fun caracterizacion() {
        val prefs = PreferencesHelper(context)
        prefs.useLocalDemo = false
        val url = server.url("/exec").toString()
        val repo = AhorroRepository(prefs, scriptUrl = url, folderId = "folder-1")
        val repoSinUrl = AhorroRepository(prefs, scriptUrl = "", folderId = "")
        val mov = GoldenFixture.movimientos().first { it.id == "m05" }
        val plan = GoldenFixture.planes().first()

        paso("fetchMovements ok", prefs) { ok(movsJson); repo.fetchMovements() }
        paso("fetchMovements 500 -> cache", prefs) { fail(500); repo.fetchMovements() }
        paso("fetchMovements status ERROR", prefs) { ok("""{"status":"ERROR","message":"x"}"""); repo.fetchMovements() }
        paso("fetchMovements url vacia", prefs) { repoSinUrl.fetchMovements() }
        paso("saveMovement PUT con ticket", prefs) {
            ok("""{"status":"SUCCESS"}""")
            repo.saveMovement(mov, ImageInfo("QUJD"))
        }
        paso("saveMovement PUT existente", prefs) {
            ok("""{"status":"SUCCESS"}""")
            repo.saveMovement(mov.copy(monto = 1.0), null)
        }
        paso("saveMovement rechazado", prefs) { ok("""{"status":"ERROR"}"""); repo.saveMovement(mov.copy(id = "zz")) }
        paso("deleteMovement ok", prefs) { ok("""{"status":"SUCCESS"}"""); repo.deleteMovement(mov) }
        paso("deleteMovement 500", prefs) { fail(500); repo.deleteMovement(mov.copy(id = "a1")) }
        paso("fetchPlans pendientes", prefs) { ok(plansJson); repo.fetchPlans(soloPagos = false) }
        paso("fetchPlans pagos", prefs) { ok(plansJson); repo.fetchPlans(soloPagos = true) }
        paso("fetchPlans pendientes 500", prefs) { fail(500); repo.fetchPlans(soloPagos = false) }
        paso("fetchPlans pagos 500", prefs) { fail(500); repo.fetchPlans(soloPagos = true) }
        paso("cachedPendingPlans", prefs) { repo.cachedPendingPlans() }
        paso("savePlan", prefs) { ok("""{"status":"SUCCESS"}"""); repo.guardarPlan(plan) }
        paso("updatePlan", prefs) { ok("""{"status":"SUCCESS"}"""); repo.guardarPlan(plan.copy(descripcion = "Otra")) }
        paso("deletePlan", prefs) { ok("""{"status":"SUCCESS"}"""); repo.deletePlan(plan) }
        paso("snapshotCuotasPrevias", prefs) { ok("""{"status":"SUCCESS"}"""); repo.snapshotCuotasPrevias("2026-10") }
        paso("snapshotCuotasPrevias error", prefs) { ok("""{"status":"ERROR","message":"no"}"""); repo.snapshotCuotasPrevias("2026-10") }
        paso("fetchUsuarios", prefs) {
            ok("""{"status":"SUCCESS","users":[{"slotKey":"Rocío","nombre":"Ro","colorId":"rose","orden":1,"mensaje":"hola","mensajeFecha":"2026-10-01"}]}""")
            repo.fetchUsuarios()
        }
        paso("fetchUsuarios vacio", prefs) { ok("""{"status":"SUCCESS","users":[]}"""); repo.fetchUsuarios() }
        paso("updateUsuario", prefs) { ok("""{"status":"SUCCESS"}"""); repo.updateUsuario(Usuario("Santiago", "Santi", "teal", 0)) }
        paso("updateUsuario slot raro", prefs) { ok("""{"status":"SUCCESS"}"""); repo.updateUsuario(Usuario("Otro", "X", "amber", 5)) }
        paso("cachedUsuarios", prefs) { repo.cachedUsuarios() }

        // Modo demo: todo contra SharedPreferences.
        prefs.useLocalDemo = true
        prefs.saveLocalMovements(GoldenFixture.movimientos())
        prefs.saveLocalPlans(GoldenFixture.planes())
        paso("demo fetchMovements", prefs) { repo.fetchMovements().map { it.id } }
        paso("demo saveMovement", prefs) { repo.saveMovement(mov.copy(id = "nuevo")) }
        paso("demo deleteMovement", prefs) { repo.deleteMovement(mov) }
        paso("demo fetchPlans pend", prefs) { repo.fetchPlans(false).map { it.id } }
        paso("demo fetchPlans pagos", prefs) { repo.fetchPlans(true).map { it.id } }
        paso("demo cachedPendingPlans", prefs) { repo.cachedPendingPlans().map { it.id } }
        paso("demo savePlan", prefs) { repo.guardarPlan(plan.copy(id = "nuevoPlan")) }
        paso("demo deletePlan", prefs) { repo.deletePlan(plan) }
        paso("demo snapshot", prefs) { repo.snapshotCuotasPrevias("2026-10") }
        paso("demo updateUsuario", prefs) { repo.updateUsuario(Usuario("Rocío", "Rochi", "violet", 1)) }
        paso("demo fetchUsuarios", prefs) { repo.fetchUsuarios() }

        File("build/golden").mkdirs()
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        File("build/golden/repo.txt").writeText(log.toString().replace(url, "URL/").replace(uuid, "<uuid>"))
    }
}
