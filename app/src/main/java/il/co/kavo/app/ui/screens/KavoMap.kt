package il.co.kavo.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import il.co.kavo.app.data.StopDto
import il.co.kavo.app.data.VehicleDto
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private val ISRAEL_BOUNDS = LatLngBounds.Builder()
    .include(LatLng(29.45, 34.26))
    .include(LatLng(33.35, 35.90))
    .build()

@Composable
fun KavoMap(
    modifier: Modifier = Modifier,
    userLat: Double?,
    userLon: Double?,
    recenterTick: Int,
    stops: List<StopDto>,
    vehicles: List<VehicleDto>,
    selectedCode: Int?,
    onStopClick: (Int) -> Unit,
    onCameraIdle: (LatLngBounds) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val holder = remember { MapHolder() }

    DisposableEffect(Unit) {
        mapView.onStart(); mapView.onResume()
        mapView.getMapAsync { map ->
            holder.map = map
            map.setStyle(Style.Builder().fromUri("asset://kavo-3d-style.json")) { style ->
                holder.style = style

                style.addSource(GeoJsonSource(SRC_STOPS))
                style.addSource(GeoJsonSource(SRC_VEHICLES))
                style.addSource(GeoJsonSource(SRC_USER))

                style.addLayer(CircleLayer(L_STOPS, SRC_STOPS).withProperties(
                    circleRadius(6.5f),
                    circleColor("#1366FF"),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(2.2f),
                    circleOpacity(0.95f)
                ))
                style.addLayer(CircleLayer(L_VEHICLES, SRC_VEHICLES).withProperties(
                    circleRadius(5f),
                    circleColor("#17C27A"),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(1.6f)
                ))
                style.addLayer(CircleLayer(L_USER, SRC_USER).withProperties(
                    circleRadius(9f),
                    circleColor("#38B6FF"),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(3f)
                ))

                map.setLatLngBoundsForCameraTarget(ISRAEL_BOUNDS)
                map.setMinZoomPreference(6.5)
                map.setMaxZoomPreference(18.5)
                val start = if (userLat != null && userLon != null)
                    LatLng(userLat, userLon) else LatLng(32.0853, 34.7818)
                map.cameraPosition = CameraPosition.Builder()
                    .target(start).zoom(15.5).tilt(55.0).build()
                map.uiSettings.isRotateGesturesEnabled = true
                map.uiSettings.isTiltGesturesEnabled = true
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = true
                map.uiSettings.setAttributionMargins(16, 0, 0, 16)

                holder.pushStops(stops, selectedCode)
                holder.pushVehicles(vehicles)
                holder.pushUser(userLat, userLon)

                map.addOnMapClickListener { latLng ->
                    val pt = map.projection.toScreenLocation(latLng)
                    val feats = map.queryRenderedFeatures(pt, L_STOPS)
                    val code = feats.firstOrNull()?.getNumberProperty("code")?.toInt()
                    if (code != null) { onStopClick(code); true } else false
                }
            }
            map.addOnCameraIdleListener {
                onCameraIdle(map.projection.visibleRegion.latLngBounds)
            }
        }
        onDispose {
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    // Push data updates to the map's sources.
    LaunchedEffect(stops, selectedCode) { holder.pushStops(stops, selectedCode) }
    LaunchedEffect(vehicles) { holder.pushVehicles(vehicles) }
    LaunchedEffect(userLat, userLon) { holder.pushUser(userLat, userLon) }

    // Animate to the user when locate() fires.
    LaunchedEffect(recenterTick) {
        if (recenterTick > 0 && userLat != null && userLon != null) {
            holder.map?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(userLat, userLon)).zoom(16.0).tilt(55.0).build()
                ), 900
            )
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private class MapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null

    fun pushStops(stops: List<StopDto>, selected: Int?) {
        val src = style?.getSourceAs<GeoJsonSource>(SRC_STOPS) ?: return
        val feats = stops.map {
            Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)).apply {
                addNumberProperty("code", it.code)
            }
        }
        src.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    fun pushVehicles(vehicles: List<VehicleDto>) {
        val src = style?.getSourceAs<GeoJsonSource>(SRC_VEHICLES) ?: return
        val feats = vehicles.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }
        src.setGeoJson(FeatureCollection.fromFeatures(feats))
    }

    fun pushUser(lat: Double?, lon: Double?) {
        val src = style?.getSourceAs<GeoJsonSource>(SRC_USER) ?: return
        if (lat == null || lon == null) { src.setGeoJson(FeatureCollection.fromFeatures(emptyList())); return }
        src.setGeoJson(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
    }
}

private const val SRC_STOPS = "kavo-stops"
private const val SRC_VEHICLES = "kavo-vehicles"
private const val SRC_USER = "kavo-user"
private const val L_STOPS = "kavo-stops-l"
private const val L_VEHICLES = "kavo-vehicles-l"
private const val L_USER = "kavo-user-l"
