package com.derek.capflix.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.derek.capflix.CapFlixApp
import com.derek.capflix.data.Item
import com.derek.capflix.player.PlayerActivity
import com.derek.capflix.ui.browse.BrowseScreen
import com.derek.capflix.ui.detail.DetailScreen
import com.derek.capflix.ui.home.HomeScreen
import com.derek.capflix.ui.login.LoginScreen
import com.derek.capflix.ui.search.SearchScreen
import com.derek.capflix.ui.settings.SettingsScreen
import com.derek.capflix.ui.theme.Bolt
import com.derek.capflix.ui.theme.CapFlixTheme
import com.derek.capflix.ui.theme.Muted
import com.derek.capflix.ui.theme.Night

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CapFlixTheme { App() } }
    }
}

private val tabs = listOf(
    Triple("home", "Home", Icons.Outlined.Home to Icons.Filled.Home),
    Triple("search", "Search", Icons.Outlined.Search to Icons.Filled.Search),
    Triple("mylist", "My List", Icons.Outlined.Bookmarks to Icons.Filled.Bookmarks),
)

@androidx.compose.runtime.Composable
private fun App() {
    val nav = rememberNavController()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val start = if (CapFlixApp.session.isSignedIn) "home" else "login"
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    fun openItem(item: Item) {
        when (item.type) {
            "BoxSet" -> nav.navigate("browse/" + Uri.encode("parent:${item.id}:${item.name}") + "/Movie,Series")
            "Episode" -> PlayerActivity.start(ctx, item.id)
            else -> nav.navigate("detail/${item.id}")
        }
    }
    fun play(item: Item, mediaSourceId: String? = null) = PlayerActivity.start(ctx, item.id, mediaSourceId)

    Scaffold(
        containerColor = Night,
        bottomBar = {
            if (route in tabs.map { it.first }) BottomBar(nav, route)
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            NavHost(nav, startDestination = start) {
                composable("login") {
                    LoginScreen(onSignedIn = { nav.navigate("home") { popUpTo("login") { inclusive = true } } })
                }
                composable("home") {
                    HomeScreen(
                        openItem = ::openItem,
                        play = { play(it) },
                        openMore = { kind, f -> nav.navigate("browse/" + Uri.encode(kind) + "/" + f.types) },
                        openSettings = { nav.navigate("settings") },
                    )
                }
                composable("search") {
                    SearchScreen(openItem = ::openItem, openGenre = { g -> nav.navigate("browse/" + Uri.encode("genre:$g") + "/Movie,Series") })
                }
                composable("mylist") {
                    BrowseScreen(kind = "mylist", types = "Movie,Series", onBack = null, openItem = ::openItem)
                }
                composable(
                    "browse/{kind}/{types}",
                    arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("types") { type = NavType.StringType }),
                ) { e ->
                    BrowseScreen(
                        kind = e.arguments?.getString("kind") ?: "all",
                        types = e.arguments?.getString("types") ?: "Movie,Series",
                        onBack = { nav.popBackStack() }, openItem = ::openItem,
                    )
                }
                composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    DetailScreen(
                        itemId = e.arguments?.getString("id") ?: "",
                        onBack = { nav.popBackStack() },
                        openItem = ::openItem,
                        play = { item, ms -> play(item, ms) },
                    )
                }
                composable("settings") {
                    SettingsScreen(onBack = { nav.popBackStack() }, onSignedOut = {
                        nav.navigate("login") { popUpTo(nav.graph.id) { inclusive = true } }
                    })
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar(containerColor = Night.copy(alpha = .97f)) {
        tabs.forEach { (r, label, icons) ->
            val on = route == r
            NavigationBarItem(
                selected = on,
                onClick = {
                    if (!on) nav.navigate(r) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(if (on) icons.second else icons.first, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White, selectedTextColor = Color.White,
                    unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Bolt.copy(alpha = .22f),
                ),
            )
        }
    }
}
