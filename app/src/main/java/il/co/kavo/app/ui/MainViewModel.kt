package il.co.kavo.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import il.co.kavo.app.data.Arrival
import il.co.kavo.app.data.ArrivalsApi
import il.co.kavo.app.data.NearbyStop
import il.co.kavo.app.data.StopDto
import il.co.kavo.app.data.StopsCatalog
import il.co.kavo.app.data.TransitApi
import il.co.kavo.app.data.VehicleDto
import il.co.kavo.app.util.LocationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val catalogReady: Boolean = false,
    val loading: Boolean = false,
    val userLat: Double? = null,
    val userLon: Double? = null,
    val recenterTick: Int = 0,          // bumps to tell the map to animate to user
    val nearby: List<NearbyStop> = emptyList(),
    val vehicles: List<VehicleDto> = emptyList(),
    val selectedStop: StopDto? = null,
    val arrivals: List<Arrival> = emptyList(),
    val arrivalsRealtime: Boolean = true,
    val arrivalsLoading: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<NearbyStop> = emptyList(),
    val searchActive: Boolean = false,
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val transit = TransitApi()
    private val arrivalsApi = ArrivalsApi(transit)
    private val location = LocationProvider(app)

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var vehiclePoll: Job? = null
    private var arrivalPoll: Job? = null

    init {
        viewModelScope.launch {
            StopsCatalog.load(getApplication())
            _state.update { it.copy(catalogReady = true) }
        }
    }

    fun locate() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            if (!StopsCatalog.ready) StopsCatalog.load(getApplication())
            val loc = location.current()
            if (loc == null) {
                _state.update { it.copy(loading = false, error = "לא הצלחנו לאתר מיקום. ודאו שה-GPS פעיל.") }
                return@launch
            }
            val (lat, lon) = loc
            val nearby = StopsCatalog.nearest(lat, lon, 30)
            _state.update {
                it.copy(
                    loading = false,
                    userLat = lat, userLon = lon,
                    recenterTick = it.recenterTick + 1,
                    nearby = nearby
                )
            }
        }
    }

    fun recenter() = _state.update { it.copy(recenterTick = it.recenterTick + 1) }

    fun selectStop(stop: StopDto) {
        _state.update { it.copy(selectedStop = stop, searchActive = false, arrivalsLoading = true, arrivals = emptyList()) }
        startArrivalPolling(stop)
    }

    private fun startArrivalPolling(stop: StopDto) {
        arrivalPoll?.cancel()
        arrivalPoll = viewModelScope.launch {
            while (true) {
                val (arr, rt) = arrivalsApi.arrivals(stop)
                _state.update {
                    if (it.selectedStop?.code != stop.code) it
                    else it.copy(arrivals = arr, arrivalsRealtime = rt, arrivalsLoading = false)
                }
                delay(20_000) // live refresh, matching industry 20s cadence
            }
        }
    }

    fun selectStopByCode(code: Int) {
        val near = _state.value.nearby.firstOrNull { it.stop.code == code }?.stop
        val found = near ?: StopsCatalog.byCode(code)
        if (found != null) selectStop(found)
    }

    fun clearSelection() {
        arrivalPoll?.cancel()
        _state.update { it.copy(selectedStop = null, arrivals = emptyList()) }
    }

    // ---- search ----
    fun onSearchQuery(q: String) {
        _state.update { it.copy(searchQuery = q, searchActive = true) }
        val results = StopsCatalog.search(q, _state.value.userLat, _state.value.userLon)
        _state.update { it.copy(searchResults = results) }
    }

    fun openSearch() = _state.update { it.copy(searchActive = true) }
    fun closeSearch() = _state.update { it.copy(searchActive = false, searchQuery = "", searchResults = emptyList()) }

    fun onViewport(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        vehiclePoll?.cancel()
        vehiclePoll = viewModelScope.launch {
            while (true) {
                val v = transit.vehiclesInBox(minLat, maxLat, minLon, maxLon)
                _state.update { it.copy(vehicles = v) }
                delay(15_000)
            }
        }
    }

    override fun onCleared() {
        vehiclePoll?.cancel(); arrivalPoll?.cancel()
    }
}
