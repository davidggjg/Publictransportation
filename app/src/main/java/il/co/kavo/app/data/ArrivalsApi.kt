package il.co.kavo.app.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Real-time arrivals for a single stop.
 *
 * Primary source: curlbus (a thin wrapper over the official MoT SIRI feed),
 * which returns per-stop visits with the real line number, operator, headsign,
 * a precise ETA timestamp and the live vehicle position.
 *
 * Fallback: the open-bus SIRI vehicle feed around the stop, matched by distance.
 */
class ArrivalsApi(private val fallback: TransitApi) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 12_000
            connectTimeoutMillis = 8_000
        }
    }

    private val curlbusHosts = listOf("https://curlbus.app")
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX")

    suspend fun arrivals(stop: StopDto): Pair<List<Arrival>, Boolean> {
        // returns (arrivals, isRealtime)
        for (host in curlbusHosts) {
            try {
                val raw = client.get("$host/${stop.code}") {
                    header("Accept", "application/json")
                }.bodyAsText()
                val parsed = parseCurlbus(raw, stop)
                if (parsed.isNotEmpty()) return parsed to true
            } catch (e: Exception) {
                Log.w(TAG, "curlbus failed: ${e.message}")
            }
        }
        // Fallback: live vehicles near the stop (less precise, no line names).
        val v = fallback.vehiclesInBox(
            stop.lat - 0.03, stop.lat + 0.03, stop.lon - 0.03, stop.lon + 0.03
        )
        val arr = v.take(8).map { veh ->
            val dist = il.co.kavo.app.util.Geo.distance(stop.lat, stop.lon, veh.lat, veh.lon)
            Arrival(
                lineName = veh.lineRef?.toString() ?: "—",
                destination = "",
                operator = operatorName(veh.operatorRef?.toString()),
                minutesAway = (dist / 400).coerceAtLeast(1),
                isRealtime = true,
                vehicleLat = veh.lat, vehicleLon = veh.lon
            )
        }.sortedBy { it.minutesAway }
        return arr to false
    }

    private fun parseCurlbus(raw: String, stop: StopDto): List<Arrival> {
        val root = json.parseToJsonElement(raw).jsonObject
        val visits = root["visits"]?.jsonObject ?: return emptyList()
        val now = OffsetDateTime.now()
        val out = ArrayList<Arrival>()
        for ((_, list) in visits) {
            val items = list as? kotlinx.serialization.json.JsonArray ?: continue
            for (el in items) {
                val o = el.jsonObject
                val line = o["line_name"]?.jsonPrimitive?.contentOrNull ?: continue
                val etaStr = o["eta"]?.jsonPrimitive?.contentOrNull ?: continue
                val mins = try {
                    val eta = OffsetDateTime.parse(etaStr, fmt)
                    Duration.between(now, eta).toMinutes().toInt()
                } catch (e: Exception) { continue }
                if (mins < 0 || mins > 180) continue

                val op = o["operator_id"]?.jsonPrimitive?.contentOrNull
                val agency = o["static_info"]?.jsonObject
                    ?.get("route")?.jsonObject
                    ?.get("agency")?.jsonObject
                    ?.get("name")?.jsonObject
                    ?.get("HE")?.jsonPrimitive?.contentOrNull
                val dest = o["static_info"]?.jsonObject
                    ?.get("route")?.jsonObject
                    ?.get("destination")?.jsonObject
                    ?.get("name")?.jsonObject
                    ?.get("HE")?.jsonPrimitive?.contentOrNull ?: ""
                val loc = o["location"]?.jsonObject
                val vlat = loc?.get("lat")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val vlon = loc?.get("lon")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

                out += Arrival(
                    lineName = line,
                    destination = dest,
                    operator = agency ?: operatorName(op),
                    minutesAway = mins,
                    isRealtime = vlat != null,
                    vehicleLat = vlat, vehicleLon = vlon
                )
            }
        }
        return out.sortedBy { it.minutesAway }.take(12)
    }

    private fun operatorName(ref: String?): String = when (ref) {
        "3" -> "אגד"; "5" -> "דן"; "15" -> "מטרופולין"; "18" -> "קווים"
        "16" -> "סופרבוס"; "25" -> "אלקטרה אפיקים"; "2" -> "רכבת ישראל"
        "4" -> "אגד תעבורה"; "6" -> "ש.א.מ"; "8" -> "נתיב אקספרס"
        "30" -> "מועצה אזורית"; "31" -> "אפיקים"; "34" -> "גלים"
        "91" -> "רכבל חיפה"; "92" -> "נ.ת.ע (קווי הרכבת הקלה)"
        else -> "תחבורה ציבורית"
    }

    companion object { private const val TAG = "ArrivalsApi" }
}
