package com.ivan.tabiplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.ivan.tabiplan.ai.TravelAi
import com.ivan.tabiplan.data.Settings
import com.ivan.tabiplan.data.TripStore
import com.ivan.tabiplan.maps.GoogleMaps
import com.ivan.tabiplan.ui.AssistantScreen
import com.ivan.tabiplan.ui.GuideScreen
import com.ivan.tabiplan.ui.GuidesScreen
import com.ivan.tabiplan.ui.SettingsScreen
import com.ivan.tabiplan.ui.TabiTheme
import com.ivan.tabiplan.ui.TripScreen
import com.ivan.tabiplan.ui.TripsScreen

/** Dependencias compartidas por todas las pantallas. */
class AppDeps(activity: ComponentActivity) {
    val store = TripStore(activity.applicationContext)
    val settings = Settings(activity.applicationContext)
    val maps = GoogleMaps(activity.applicationContext) { settings.mapsKey.ifBlank { BuildConfig.MAPS_API_KEY } }
    val ai = TravelAi(settings)
    val navitime = com.ivan.tabiplan.maps.Navitime(activity.applicationContext, settings) { settings.navitimeKey.ifBlank { BuildConfig.NAVITIME_KEY } }
}

sealed interface Screen {
    data object Trips : Screen
    data class Trip(val id: String) : Screen
    data class Guides(val country: com.ivan.tabiplan.data.Country?) : Screen
    data class Guide(val id: String) : Screen
    data class Assistant(val tripId: String?, val question: String = "") : Screen
    data object Settings : Screen
    data class HotelSearch(val tripId: String) : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Trips)
    val current get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deps = AppDeps(this)
        setContent { TabiTheme { App(deps) } }
    }
}

@Composable
fun App(deps: AppDeps) {
    val nav = remember { Nav() }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    when (val s = nav.current) {
        Screen.Trips -> TripsScreen(deps, nav)
        is Screen.Trip -> TripScreen(deps, nav, s.id)
        is Screen.Guides -> GuidesScreen(nav, s.country)
        is Screen.Guide -> GuideScreen(nav, s.id)
        is Screen.Assistant -> AssistantScreen(deps, nav, s.tripId, s.question)
        Screen.Settings -> SettingsScreen(deps, nav)
        is Screen.HotelSearch -> com.ivan.tabiplan.ui.HotelSearchScreen(deps, nav, s.tripId)
    }
}
