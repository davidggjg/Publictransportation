package il.co.kavo.app.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Transit data layer built entirely on open, key-free sources:
 *  - open-bus Stride API (Hasadna / public-knowledge-workshop), which serves
 *    the Israeli Ministry of Transport GTFS + official SIRI real-time feed.
 *
 * Multiple base URLs are kept so requests can be spread across mirrors; each
 * call walks the list until one answers, which also gives us failover.
 */
class TransitApi {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    /** Primary + mirror hosts for load-splitting and failover. */
    private val strideHosts = listOf(
        "https://open-bus-stride-api.hasadna.org.il",
        "https://open-bus-stride-api.k8s.hasadna.org.il"
    )

    private suspend inline fun <reified T> getList(
        path: String,
        params: Map<String, Any?>
    ): T? {
        for (host in strideHosts) {
            try {
                return client.get("$host$path") {
                    header("Accept", ContentType.Application.Json.toString())
                    params.forEach { (k, v) -> if (v != null) parameter(k, v) }
                }.body<T>()
            } catch (e: Exception) {
                Log.w(TAG, "host $host failed for $path: ${e.message}")
            }
        }
        return null
    }

    /** Stops inside a bounding box around [lat]/[lon]. */
    suspend fun stopsNear(lat: Double, lon: Double, radiusDeg: Double = 0.006): List<StopDto> {
        // The /list endpoint doesn't take a bbox directly, so we fetch a page and
        // filter locally; good enough for a neighborhood-sized radius.
        val all = getList<List<StopDto>>(
            "/gtfs_stops/list",
            mapOf("limit" to 400, "order_by" to "code")
        ) ?: emptyList()
        return all.filter {
            kotlin.math.abs(it.lat - lat) <= radiusDeg &&
                kotlin.math.abs(it.lon - lon) <= radiusDeg
        }
    }

    /** Live vehicles inside the current map viewport. */
    suspend fun vehiclesInBox(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double
    ): List<VehicleDto> {
        return getList<List<VehicleDto>>(
            "/siri_vehicle_locations/list",
            mapOf(
                "lat__greater_or_equal" to minLat,
                "lat__lower_or_equal" to maxLat,
                "lon__greater_or_equal" to minLon,
                "lon__lower_or_equal" to maxLon,
                "order_by" to "recorded_at_time desc",
                "limit" to 300
            )
        ) ?: emptyList()
    }

    companion object { private const val TAG = "TransitApi" }
}
