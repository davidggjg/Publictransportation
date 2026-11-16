package il.co.kavo.app.data

import android.content.Context
import il.co.kavo.app.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.zip.GZIPInputStream

/**
 * In-memory catalog of ~30,000 Israeli stops, bundled as a gzipped JSON asset
 * (official MoT GTFS via open-bus). Enables instant offline search by name or
 * code and fast nearest-stop lookup — the same local-catalog technique the
 * BusNearby apps use with their bundled SQLite.
 *
 * Row format (compact array): [code, name, city, lat, lon]
 */
object StopsCatalog {

    private var stops: List<StopDto> = emptyList()
    @Volatile var ready = false
        private set

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        if (ready) return@withContext
        val json = Json { ignoreUnknownKeys = true }
        val text = GZIPInputStream(context.assets.open("stops_catalog.json.gz"))
            .bufferedReader().use { it.readText() }
        val arr = json.parseToJsonElement(text) as JsonArray
        stops = arr.map { row ->
            val r = row as JsonArray
            StopDto(
                id = r[0].jsonPrimitive.content.toLong(),
                code = r[0].jsonPrimitive.content.toInt(),
                name = r[1].jsonPrimitive.content,
                city = r[2].jsonPrimitive.content.ifBlank { null },
                lat = r[3].jsonPrimitive.content.toDouble(),
                lon = r[4].jsonPrimitive.content.toDouble()
            )
        }
        ready = true
    }

    fun byCode(code: Int): StopDto? = stops.firstOrNull { it.code == code }

    fun nearest(lat: Double, lon: Double, limit: Int = 30): List<NearbyStop> {
        // Pre-filter by a coarse bounding box before the exact haversine sort.
        val d = 0.012
        return stops.asSequence()
            .filter { kotlin.math.abs(it.lat - lat) < d && kotlin.math.abs(it.lon - lon) < d }
            .map { NearbyStop(it, Geo.distance(lat, lon, it.lat, it.lon)) }
            .sortedBy { it.distanceMeters }
            .take(limit)
            .toList()
    }

    fun search(query: String, userLat: Double?, userLon: Double?, limit: Int = 40): List<NearbyStop> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val asCode = q.toIntOrNull()
        val matches = stops.asSequence().filter { s ->
            (asCode != null && s.code == asCode) ||
                s.name.contains(q) ||
                (s.city?.contains(q) == true)
        }.take(400).toList()
        return matches.map {
            val dist = if (userLat != null && userLon != null)
                Geo.distance(userLat, userLon, it.lat, it.lon) else Int.MAX_VALUE
            NearbyStop(it, dist)
        }.sortedWith(
            compareByDescending<NearbyStop> { it.stop.code == asCode }
                .thenBy { it.distanceMeters }
        ).take(limit)
    }
}
