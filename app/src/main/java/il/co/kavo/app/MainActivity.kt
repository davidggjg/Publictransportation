package il.co.kavo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.Manifest
import androidx.compose.runtime.LaunchedEffect
import il.co.kavo.app.ui.MainViewModel
import il.co.kavo.app.ui.screens.MainScreen
import il.co.kavo.app.ui.theme.KavoTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KavoTheme {
                // Whole app is RTL (Hebrew-first).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val vm: MainViewModel = viewModel()
                        val state by vm.state.collectAsStateWithLifecycle()
                        val widthClass = calculateWindowSizeClass(this).widthSizeClass

                        val perms = rememberMultiplePermissionsState(
                            listOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        LaunchedEffect(Unit) {
                            if (!perms.allPermissionsGranted) perms.launchMultiplePermissionRequest()
                        }
                        LaunchedEffect(perms.allPermissionsGranted) {
                            if (perms.allPermissionsGranted) vm.locate()
                        }

                        MainScreen(
                            state = state,
                            widthClass = widthClass,
                            onLocate = {
                                if (perms.allPermissionsGranted) vm.locate()
                                else perms.launchMultiplePermissionRequest()
                            },
                            onSelectStop = { vm.selectStop(it.stop) },
                            onClearStop = { vm.clearSelection() },
                            onSearchQuery = { vm.onSearchQuery(it) },
                            onOpenSearch = { vm.openSearch() },
                            onCloseSearch = { vm.closeSearch() },
                            onStopClickByCode = { code -> vm.selectStopByCode(code) },
                            onViewport = { b ->
                                vm.onViewport(
                                    b.latitudeSouth, b.latitudeNorth,
                                    b.longitudeWest, b.longitudeEast
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
