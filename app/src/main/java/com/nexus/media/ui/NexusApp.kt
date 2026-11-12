package com.nexus.media.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexus.media.core.permissions.MediaPermissions
import com.nexus.media.core.theme.ElectricBlue
import com.nexus.media.di.AppContainer
import com.nexus.media.features.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun NexusApp(c: AppContainer) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(MediaPermissions.hasMedia(ctx)) }
    var introDone by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = MediaPermissions.hasMedia(ctx)
    }
    LaunchedEffect(granted) {
        if (granted) {
            c.repository.startObserving()
            c.repository.rescan()
            c.player.restore()
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!granted) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("NEXUS Media", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Izinkan akses ke musik dan video di perangkat agar library bisa dipindai.")
                Spacer(Modifier.height(20.dp))
                Button(onClick = { launcher.launch(MediaPermissions.required()) }) { Text("Beri izin") }
            }
        } else {
            MainScaffold(c)
        }
        if (!introDone) IntroOverlay { introDone = true }
    }
}

@Composable
private fun IntroOverlay(onDone: () -> Unit) {
    val sc = remember { Animatable(0.55f) }
    val a = remember { Animatable(0f) }
    val fd = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { a.animateTo(1f, tween(450)) }
        launch { sc.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 180f)) }
        delay(1150)
        fd.animateTo(0f, tween(450))
        onDone()
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = fd.value }.background(Color.Black).pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value; alpha = a.value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(104.dp).glowCircle(ElectricBlue, 34.dp, 1f).clip(CircleShape).background(ElectricBlue),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
            Spacer(Modifier.height(26.dp))
            Text("NEXUS", fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp, color = Color.White)
            Text("MEDIA", fontSize = 15.sp, letterSpacing = 8.sp, color = ElectricBlue)
        }
    }
}

@Composable
private fun MainScaffold(c: AppContainer) {
    val nav = rememberNavController()
    var full by remember { mutableStateOf(false) }
    val vm: LibraryViewModel = hiltViewModel()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { 5 }
    val entry by nav.currentBackStackEntryAsState()
    val onMain = entry?.destination?.route == "main"

    val goTab: (Int) -> Unit = { i ->
        scope.launch {
            if (nav.currentDestination?.route != "main") nav.popBackStack("main", false)
            pager.animateScrollToPage(i)
        }
    }
    BackHandler(enabled = onMain && pager.currentPage != 0 && !full) { goTab(0) }

    val drawerItems = listOf(
        NavTab("folders", "Folder", Icons.Rounded.Folder),
        NavTab("library", "Playlist", Icons.Rounded.PlaylistPlay),
        NavTab("recent", "Terakhir diputar", Icons.Rounded.History),
        NavTab("duplicates", "File duplikat", Icons.Rounded.ContentCopy),
        NavTab("search", "Cari", Icons.Rounded.Search)
    )
    CompositionLocalProvider(
        LocalContainer provides c,
        LocalNav provides nav,
        LocalGoTab provides goTab,
        LocalOpenDrawer provides { scope.launch { drawer.open() }; Unit }
    ) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                ModalDrawerSheet {
                    Text("NEXUS Media", Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge)
                    drawerItems.forEach { t ->
                        NavigationDrawerItem(
                            label = { Text(t.label) },
                            icon = { Icon(t.icon, null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawer.close() }
                                if (t.route == "library") goTab(3) else nav.navigate(t.route)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text("Pengaturan") }, icon = { Icon(Icons.Rounded.Settings, null) }, selected = false,
                        onClick = { scope.launch { drawer.close() }; goTab(4) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        ) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        Column {
                            MiniPlayer(c) { full = true }
                            BottomBar(if (onMain) pager.currentPage else -1, goTab)
                        }
                    }
                ) { pad ->
                    NavHost(
                        nav, startDestination = "main", modifier = Modifier.padding(pad),
                        enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 8 } },
                        exitTransition = { fadeOut(tween(180)) },
                        popEnterTransition = { fadeIn(tween(260)) },
                        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { it / 8 } }
                    ) {
                        composable("main") {
                            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                                Box(
                                    Modifier.fillMaxSize().graphicsLayer {
                                        // read in the draw phase: swiping no longer recomposes the whole page
                                        val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                                        val sc = 1f - 0.04f * off
                                        scaleX = sc; scaleY = sc
                                    }
                                ) {
                                    when (page) {
                                        0 -> HomeScreen(vm, nav)
                                        1 -> VideoScreen(vm, nav)
                                        2 -> MusicScreen(vm, nav)
                                        3 -> LibraryScreen(vm, nav)
                                        else -> SettingsScreen(vm)
                                    }
                                }
                            }
                        }
                        composable("search") { SearchScreen(vm, nav) }
                        composable("folders") { FoldersScreen(vm, nav) }
                        composable("recent") { RecentScreen(vm, nav) }
                        composable("duplicates") { DuplicatesScreen(vm, nav) }
                        composable("details/{id}") { DetailsScreen(nav, it.arguments!!.getString("id")!!) }
                        composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                            PlaylistScreen(vm, nav, it.arguments!!.getLong("id"))
                        }
                        composable("folder/{path}") { FolderScreen(vm, nav, dec(it.arguments!!.getString("path")!!)) }
                        composable("group/{kind}/{name}") {
                            GroupScreen(vm, nav, it.arguments!!.getString("kind")!!, dec(it.arguments!!.getString("name")!!))
                        }
                    }
                }
                AnimatedVisibility(
                    visible = full,
                    enter = slideInVertically(tween(320)) { it } + fadeIn(tween(320)),
                    exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(260))
                ) {
                    BackHandler { full = false }
                    FullPlayer(c) { full = false }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        NavTab("home", "Beranda", Icons.Rounded.Home),
        NavTab("video", "Video", Icons.Rounded.Videocam),
        NavTab("music", "Musik", Icons.Rounded.MusicNote),
        NavTab("library", "Pustaka", Icons.Rounded.VideoLibrary),
        NavTab("settings", "Setelan", Icons.Rounded.Settings)
    )
    NavigationBar {
        tabs.forEachIndexed { i, t ->
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Icon(t.icon, null) },
                label = { Text(t.label, maxLines = 1) }
            )
        }
    }
}
