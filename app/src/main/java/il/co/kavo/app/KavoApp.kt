package il.co.kavo.app

import android.app.Application
import org.maplibre.android.MapLibre

class KavoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre needs init before any MapView is created. No API key required.
        MapLibre.getInstance(this)
    }
}
