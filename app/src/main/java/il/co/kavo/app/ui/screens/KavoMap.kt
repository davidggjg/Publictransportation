package il.co.kavo.app.ui.screens

import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions

/** Israel bounding box — camera cannot leave the country. */
private val ISRAEL_BOUNDS = LatLngBounds.Builder()
    .include(LatLng(29.45, 34.26))
    .include(LatLng(33.35, 35.90))
    .build()

@Composable
fun KavoMap(
    modifier: Modifier = Modifier,
    userLat: Double?,
    userLon: Double?,
    stops: List<StopDto>,
    vehicles: List<VehicleDto>,
    onMapReady: (MapLibreMap) -> Unit,
    onCameraIdle: (LatLngBounds) -> Unit
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    DisposableEffect(Unit) {
        mapView.onStart(); mapView.onResume()
        mapView.getMapAsync { map ->
            map.setStyle(
                Style.Builder().fromUri("asset://kavo-3d-style.json")
            ) {
                map.setLatLngBoundsForCameraTarget(ISRAEL_BOUNDS)
                map.setMinZoomPreference(6.5)
                map.setMaxZoomPreference(18.0)
                val start = if (userLat != null && userLon != null)
                    LatLng(userLat, userLon) else LatLng(32.0853, 34.7818)
                map.cameraPosition = CameraPosition.Builder()
                    .target(start).zoom(15.0).tilt(55.0).bearing(0.0).build()
                map.uiSettings.isRotateGesturesEnabled = true
                map.uiSettings.isTiltGesturesEnabled = true
                map.uiSettings.isAttributionEnabled = true
                map.uiSettings.isLogoEnabled = false
                onMapReady(map)
            }
            map.addOnCameraIdleListener {
                onCameraIdle(map.projection.visibleRegion.latLngBounds)
            }
        }
        onDispose {
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
