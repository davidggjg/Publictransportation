package il.co.kavo.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A physical stop as returned by the open-bus GTFS API. */
@Serializable
data class StopDto(
    val id: Long,
    val code: Int,
    val lat: Double,
    val lon: Double,
    val name: String,
    val city: String? = null
)

/** A live vehicle position (SIRI snapshot). */
@Serializable
data class VehicleDto(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val bearing: Int? = null,
    val velocity: Int? = null,
    @SerialName("siri_route__line_ref") val lineRef: Long? = null,
    @SerialName("siri_route__operator_ref") val operatorRef: Int? = null,
    @SerialName("siri_ride__vehicle_ref") val vehicleRef: String? = null,
    @SerialName("recorded_at_time") val recordedAt: String? = null
)

/** A GTFS route (line number + headsign). */
@Serializable
data class RouteDto(
    val id: Long,
    @SerialName("line_ref") val lineRef: Long? = null,
    @SerialName("operator_ref") val operatorRef: Int? = null,
    @SerialName("route_short_name") val shortName: String? = null,
    @SerialName("route_long_name") val longName: String? = null,
    @SerialName("agency_name") val agency: String? = null,
    @SerialName("route_type") val routeType: Int? = null
)

/** UI-facing arrival row. */
data class Arrival(
    val lineName: String,
    val destination: String,
    val operator: String,
    val minutesAway: Int,
    val isRealtime: Boolean,
    val vehicleLat: Double? = null,
    val vehicleLon: Double? = null
)

data class NearbyStop(
    val stop: StopDto,
    val distanceMeters: Int
)
