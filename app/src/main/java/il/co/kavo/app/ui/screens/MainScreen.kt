package il.co.kavo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import il.co.kavo.app.data.Arrival
import il.co.kavo.app.data.NearbyStop
import il.co.kavo.app.ui.UiState
import il.co.kavo.app.ui.theme.KavoBlue
import il.co.kavo.app.ui.theme.KavoCyan
import il.co.kavo.app.ui.theme.LiveGreen
import org.maplibre.android.geometry.LatLngBounds

@Composable
fun MainScreen(
    state: UiState,
    widthClass: WindowWidthSizeClass,
    onLocate: () -> Unit,
    onSelectStop: (NearbyStop) -> Unit,
    onClearStop: () -> Unit,
    onViewport: (LatLngBounds) -> Unit
) {
    val expanded = widthClass != WindowWidthSizeClass.Compact

    Box(Modifier.fillMaxSize()) {
        KavoMap(
            modifier = Modifier.fillMaxSize(),
            userLat = state.userLat,
            userLon = state.userLon,
            stops = state.stops,
            vehicles = state.vehicles,
            onMapReady = {},
            onCameraIdle = onViewport
        )

        TopBar(Modifier.align(Alignment.TopCenter))

        // Floating locate button
        FloatingActionButton(
            onClick = onLocate,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = KavoBlue,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = if (expanded) 32.dp else 320.dp)
        ) {
            if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = KavoBlue)
            else Icon(Icons.Filled.MyLocation, contentDescription = "המיקום שלי")
        }

        if (expanded) {
            // Tablet / large phone landscape — side panel
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(380.dp)
                    .padding(vertical = 16.dp)
            ) { PanelContent(state, onSelectStop, onClearStop) }
        } else {
            // Phone — bottom sheet
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 460.dp)
            ) { PanelContent(state, onSelectStop, onClearStop) }
        }
    }
}

@Composable
private fun TopBar(modifier: Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .statusBarsPadding(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(KavoCyan, KavoBlue))),
                contentAlignment = Alignment.Center
            ) { Text("K", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Kavo", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("תחבורה ציבורית בזמן אמת", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.Search, contentDescription = "חיפוש",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PanelContent(
    state: UiState,
    onSelectStop: (NearbyStop) -> Unit,
    onClearStop: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Box(
            Modifier.align(Alignment.CenterHorizontally)
                .width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline)
        )
        Spacer(Modifier.height(14.dp))

        val stop = state.selectedStop
        if (stop != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearStop) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "חזרה")
                }
                Column(Modifier.weight(1f)) {
                    Text(stop.name, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("${stop.city ?: ""} · רציף ${stop.code}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.arrivals.isEmpty()) {
                EmptyRow("אין כרגע כלי רכב בזמן אמת סביב התחנה")
            } else {
                LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    items(state.arrivals) { ArrivalRow(it) }
                }
            }
        } else {
            Text("תחנות בקרבתך", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            when {
                state.loading -> EmptyRow("מאתר תחנות…")
                state.nearby.isEmpty() -> EmptyRow("לחצו על כפתור המיקום כדי למצוא תחנות קרובות")
                else -> LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    items(state.nearby) { StopRow(it) { onSelectStop(it) } }
                }
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                modifier = Modifier.padding(20.dp))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun StopRow(item: NearbyStop, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(KavoBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.DirectionsBus, contentDescription = null, tint = KavoBlue) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.stop.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(item.stop.city ?: "", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${item.distanceMeters} מ'", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ArrivalRow(a: Arrival) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(KavoCyan, KavoBlue))),
                contentAlignment = Alignment.Center
            ) { Text(a.lineName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.operator, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (a.isRealtime) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(LiveGreen))
                        Spacer(Modifier.width(5.dp))
                        Text("זמן אמת", fontSize = 12.sp, color = LiveGreen, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${a.minutesAway}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = KavoBlue)
                Text("דק'", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}
