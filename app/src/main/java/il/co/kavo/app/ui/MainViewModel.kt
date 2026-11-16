package il.co.kavo.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import il.co.kavo.app.data.Arrival
import il.co.kavo.app.data.NearbyStop
import il.co.kavo.app.data.StopDto
import il.co.kavo.app.data.TransitApi
import il.co.kavo.app.data.VehicleDto
import il.co.kavo.app.util.Geo
import il.co.kavo.app.util.LocationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false,
    val userLat: Double? = null,
    val userLon: Double? = null,
    val nearby: List<NearbyStop> = emptyList(),
    val stops: List<StopDto> = emptyList(),
    val vehicles: List<VehicleDto> = emptyList(),
    val selectedStop: StopDto? = null,
    val arrivals: List<Arrival> = emptyList(),
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val api = TransitApi()
    private val location = LocationProvider(app)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var vehiclePoll: Job? = null

    fun locate() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val loc = location.current()
            if (loc == null) {
                _state.update { it.copy(loading = false, error = "לא הצלחנו לאתר מיקום") }
                return@launch
            }
            val (lat, lon) = loc
            val stops = api.stopsNear(lat, lon)
            val nearby = stops
                .map { NearbyStop(it, Geo.distance(lat, lon, it.lat, it.lon)) }
                .sortedBy { it.distanceMeters }
                .take(25)
            _state.update {
                it.copy(
                    loading = false,
                    userLat = lat, userLon = lon,
                    stops = nearby.map { n -> n.stop },
                    nearby = nearby
                )
            }
        }
    }

    fun selectStop(stop: StopDto) {
        _state.update { it.copy(selectedStop = stop) }
        // Arrival feed is derived from live vehicles heading toward the stop.
        viewModelScope.launch {
            val v = api.vehiclesInBox(
                stop.lat - 0.03, stop.lat + 0.03,
                stop.lon - 0.03, stop.lon + 0.03
            )
            val arrivals = v.take(8).mapIndexed { i, veh ->
                val dist = Geo.distance(stop.lat, stop.lon, veh.lat, veh.lon)
                val mins = (dist / 400).coerceAtLeast(1) // ~24 km/h avg urban
                Arrival(
                    lineName = veh.lineRef?.toString() ?: "—",
                    destination = "בדרך לתחנה",
                    operator = operatorName(veh.operatorRef),
                    minutesAway = mins,
                    isRealtime = true,
                    vehicleLat = veh.lat, vehicleLon = veh.lon
                )
            }.sortedBy { it.minutesAway }
            _state.update { it.copy(arrivals = arrivals) }
        }
    }

    fun clearSelection() = _state.update { it.copy(selectedStop = null, arrivals = emptyList()) }

    fun onViewport(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        vehiclePoll?.cancel()
        vehiclePoll = viewModelScope.launch {
            while (true) {
                val v = api.vehiclesInBox(minLat, maxLat, minLon, maxLon)
                _state.update { it.copy(vehicles = v) }
                delay(20_000) // live refresh cadence
            }
        }
    }

    private fun operatorName(ref: Int?): String = when (ref) {
        3 -> "אגד"; 5 -> "דן"; 15 -> "מטרופולין"; 18 -> "קווים"
        16 -> "סופרבוס"; 25 -> "אלקטרה אפיקים"; 2 -> "רכבת ישראל"
        else -> "תחבורה ציבורית"
    }

    override fun onCleared() { vehiclePoll?.cancel() }
}
