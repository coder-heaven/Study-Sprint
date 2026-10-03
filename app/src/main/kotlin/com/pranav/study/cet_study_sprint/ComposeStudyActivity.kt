package com.pranav.study.cet_study_sprint

import android.app.DatePickerDialog
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.app.NotificationManager
import android.os.Build
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val BrandBlue = Color(0xFF07AE88)
internal val BrandIndigo = Color(0xFF0A755E)
internal val Pine = BrandBlue
internal val DeepPine = Color(0xFF102B23)
internal val MintBackground = Color(0xFFF4FAF7)
internal val LeafMint = Color(0xFFD3F2E5)
internal val MutedInk = Color(0xFF526C63)
internal val WarmCream = Color(0xFFFDFDFF)

class ComposeStudyActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("study_sprint", MODE_PRIVATE) }
    private var revision by mutableIntStateOf(0)
    private var alertRoute by mutableStateOf<String?>(null)
    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            alertRoute = AlertDestination.valid(intent.getStringExtra(AlertNavigation.ROUTE))
        }
    }
    private fun handleAlert(intent: Intent) {
        if (intent.action != AlertNavigation.ACTION) return
        alertRoute = AlertDestination.valid(intent.getStringExtra(AlertNavigation.ROUTE))
        if (alertRoute != null) {
            val id = intent.getIntExtra(AlertNavigation.NOTIFICATION_ID, -1)
            if (id in listOf(100, 101, FocusAlarm.NOTIFICATION_ID))
                getSystemService(NotificationManager::class.java).cancel(id)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlert(intent)
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(AlertNavigation.ACTION)
        androidx.core.content.ContextCompat.registerReceiver(
            this, alertReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    override fun onStop() { unregisterReceiver(alertReceiver); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleAlert(intent)
        FocusAlarm.createChannel(this)
        StudyReminders.createChannel(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Leaderboards.repository(applicationContext).runSyncLoop()
            }
        }
        setContent {
            StudyTheme(prefs, revision) {
                RequiredUpdateGate(revision) {
                    OnboardingGate(prefs, refresh = { revision++ }) {
                        StudyRoot(prefs, revision, alertRoute = alertRoute, onAlertHandled = { alertRoute = null }, onLegacy = { destination ->
                            startActivity(Intent(this, MainActivity::class.java).putExtra("legacy_screen", destination))
                        }, refresh = { revision++ })
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); revision++ }
}

@Composable
internal fun StudyTheme(prefs: SharedPreferences, revision: Int, content: @Composable () -> Unit) {
    val selectedTheme = remember(revision) { prefs.getString("theme_mode", "dark") }
    val dark = when (selectedTheme) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF10B981),
        onPrimary = Color(0xFF002B1F),
        primaryContainer = Color(0xFF194D3E),
        onPrimaryContainer = Color(0xFFD3F2E5),
        secondary = Color(0xFF73D1B7),
        onSecondary = Color(0xFF073B2D),
        secondaryContainer = Color(0xFF194D3E),
        onSecondaryContainer = Color(0xFFD3F2E5),
        tertiary = Color(0xFF77DEC3),
        onTertiary = Color(0xFF00392B),
        background = Color(0xFF0B1411),
        onBackground = Color.White,
        surface = Color(0xFF14201B),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1E3027),
        onSurfaceVariant = Color(0xFFACB6B2),
        outline = Color(0xFF6D8A7F),
        outlineVariant = Color(0xFF28483A),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    ) else lightColorScheme(
        primary = BrandBlue,
        onPrimary = Color.White,
        primaryContainer = LeafMint,
        onPrimaryContainer = Color(0xFF073B2D),
        secondary = BrandIndigo,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDFF2E9),
        onSecondaryContainer = Color(0xFF073B2D),
        tertiary = Color(0xFF08745A),
        onTertiary = Color.White,
        background = MintBackground,
        onBackground = DeepPine,
        surface = Color(0xFFFFFFFF),
        onSurface = DeepPine,
        surfaceVariant = Color(0xFFEAF2EC),
        onSurfaceVariant = MutedInk,
        outline = Color(0xFF668174),
        outlineVariant = Color(0xFFC6DDD2),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? android.app.Activity
        activity?.window?.let { window ->
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = FocusIqTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = {
            // MaterialTheme alone does not provide a foreground color to Text.
            // Give every screen, including the required update gate, a themed root.
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
                Box(Modifier.fillMaxSize()) {
                    if (dark) Image(painterResource(R.drawable.figma_focus_background), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    content()
                }
            }
        }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyRoot(prefs: SharedPreferences, revision: Int, alertRoute: String?, onAlertHandled: () -> Unit, onLegacy: (String) -> Unit, refresh: () -> Unit) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route ?: "home"
    val primary = StudyNavigation.tabs.map { it.route }
    val selectedTab = StudyNavigation.tabFor(route)
    val focusModel: FocusViewModel = viewModel()
    val chatModel: StudyChatViewModel = viewModel()
    val focusState by focusModel.state.collectAsStateWithLifecycle()
    var quickTask by remember { mutableStateOf(false) }
    var chapterSubject by remember { mutableStateOf<String?>(null) }
    var chapterToOpen by remember { mutableStateOf<String?>(null) }
    fun go(target: String) {
        if (target == "focus" && !focusState.active) focusModel.task(prefs.getString("focus_intention", "").orEmpty())
        scope.launch {
            val current = nav.currentBackStackEntry?.destination?.route
            if (current != target) {
                nav.navigate(target) {
                    launchSingleTop = true
                    if (target in primary) {
                        // Never restore a saved child stack over Today. Each tab has a
                        // deterministic home-rooted stack; persistent study data is separate.
                        popUpTo("home") { inclusive = target == "home" }
                        restoreState = false
                    }
                }
            }
            if (drawer.isOpen) drawer.close()
        }
    }
    LaunchedEffect(Unit) {
        when (OnboardingStore.stage(prefs)) {
            StartupStage.SETUP -> go("setup")
            StartupStage.TUTORIAL -> go("tutorial")
            else -> Unit
        }
    }
    LaunchedEffect(alertRoute) {
        AlertDestination.valid(alertRoute)?.let { go(it); onAlertHandled() }
    }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(
            modifier = Modifier.width(304.dp).fillMaxHeight(),
            drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        ) {
            val name = prefs.getString("profile_name", "").orEmpty().ifBlank { "Student" }
            val groups = listOf(
                "OVERVIEW" to listOf("Dashboard" to "home", "Statistics" to "statistics", "Leaderboards" to "leaderboard"),
                "STUDY" to listOf("Study buddy" to "chat", "Study history" to "study_history", "Focus history" to "focus_history", "What I learned" to "notes"),
                "DIGITAL WELLBEING" to listOf("App Limits" to "limits", "App Usage" to "app_usage"),
                "PERSONAL" to listOf("Profile" to "profile", "Settings" to "settings", "Setup checklist" to "setup", "How to use the app" to "tutorial")
            )
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        ProfileAvatar(name, 54.dp) { go("profile") }
                        Spacer(Modifier.height(10.dp))
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${prefs.getString("exam", "CET")} • Class ${prefs.getString("grade", "11")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
                groups.forEach { (group, entries) ->
                    item {
                        Text(group, modifier = Modifier.padding(start = 26.dp, top = 14.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    entries.forEach { (label, destination) ->
                        item(key = destination) {
                            NavigationDrawerItem(
                                label = { Text(label, maxLines = 1) },
                                selected = route == destination,
                                onClick = { go(destination) },
                                shape = RoundedCornerShape(14.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                item {
                    Text(prefs.getString("account_email", null)?.let { "Signed in as $it" } ?: "Using Study Sprint locally", modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(28.dp))
                        Text(when (route) {
                        "chat" -> "Study buddy"
                        "home" -> "Today"
                        "study" -> "Study"
                        "statistics" -> "Progress"
                        "search" -> "Search"
                        "mcq_editor" -> "My MCQs"
                        "arihant" -> "Arihant log"
                        "study_history" -> "Study history"
                        "focus_history" -> "Focus history"
                        "app_usage" -> "App usage"
                        "privacy" -> "Privacy Policy"
                        "terms" -> "Terms of Use"
                        "setup" -> "Setup checklist"
                        "tutorial" -> "How to use Study Sprint"
                        else -> route.replace('_', ' ').replaceFirstChar { it.uppercase() }
                    },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) } },
                    navigationIcon = {
                        if (route in primary) IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "All features")
                        } else IconButton(onClick = {
                            if (!nav.popBackStack()) go("home")
                        }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        if (route == "home" || route == "plan") IconButton(onClick = { quickTask = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add study task")
                        }
                        IconButton(onClick = { go("search") }) { Icon(Icons.Default.Search, contentDescription = "Search study materials and features") }
                        IconButton(onClick = { go("settings") }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            bottomBar = {
                if (route != "chat" || WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                Column {
                    if (focusState.active && route != "focus") ActiveFocusBar(focusState) { go("focus") }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                        StudyNavigation.tabs.forEach { item ->
                            val res = when (item.route) {
                                "home" -> R.drawable.nav_home
                                "study" -> R.drawable.nav_syllabus
                                "plan" -> R.drawable.nav_plan
                                "focus" -> R.drawable.nav_focus
                                else -> R.drawable.nav_progress
                            }
                            NavigationBarItem(
                                modifier = Modifier.testTag("tab_${item.route}"),
                                selected = selectedTab == item.route,
                                onClick = { go(item.route) },
                                icon = { Icon(painterResource(res), contentDescription = null, modifier = Modifier.size(24.dp)) },
                                label = { Text(item.label, style = MaterialTheme.typography.labelMedium, fontSize = 10.sp, letterSpacing = 0.sp, maxLines = 1) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.padding(padding).testTag("screen_$route"),
                enterTransition = {
                    if (initialState.destination.route in primary && targetState.destination.route in primary)
                        EnterTransition.None else fadeIn(tween(140))
                },
                exitTransition = {
                    if (initialState.destination.route in primary && targetState.destination.route in primary)
                        ExitTransition.None else fadeOut(tween(90))
                },
                popEnterTransition = {
                    if (initialState.destination.route in primary && targetState.destination.route in primary)
                        EnterTransition.None else fadeIn(tween(140))
                },
                popExitTransition = {
                    if (initialState.destination.route in primary && targetState.destination.route in primary)
                        ExitTransition.None else fadeOut(tween(90))
                }
            ) {
                composable("chat") { StudyChatScreen(chatModel, ::go) }
                composable("home") { HomeScreen(prefs, revision, ::go) }
                composable("study") { StudyHubScreen(prefs, revision, ::go) { subject ->
                    chapterSubject = subject; chapterToOpen = null; go("syllabus")
                } }
                composable("search") { StudySearchScreen(prefs, revision, ::go) { subject, key ->
                    chapterSubject = subject; chapterToOpen = key; go("syllabus")
                } }
                composable("syllabus") { SyllabusScreen(prefs, revision, chapterSubject, chapterToOpen) }
                composable("practice") { PracticeScreen(prefs, revision) { destination ->
                    when (destination) {
                        "import" -> go("mcq_editor")
                        "arihant" -> go("arihant")
                        else -> onLegacy(destination)
                    }
                } }
                composable("arihant") { ArihantPracticeScreen(prefs, onBack = { go("practice") }) }
                composable("mcq_editor") { McqEditorScreen(prefs, onBack = { go("practice") }) {
                    refresh(); go("my_quiz")
                } }
                composable("my_quiz") {
                    QuizScreen("My questions", savedQuestions(prefs), onBack = { go("practice") })
                }
                composable("plan") { PlannerScreen(prefs, revision, ::go) }
                composable("focus") { FocusScreen(prefs, onBack = { go("home") }, onLegacy = ::go, model = focusModel, onHistory = { go("focus_history") }) }
                composable("settings") { SettingsScreen(prefs, ::go, refresh) }
                composable("setup") { InitialSetupScreen(prefs, ::go, refresh) }
                composable("tutorial") {
                    var error by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { AppTutorialScreen(::go) }
                        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {
                                if (OnboardingStore.finishTutorial(prefs)) { refresh(); go("home") }
                                else error = "Could not save. Please try again."
                            }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("tutorial_skip")) { Text("Skip") }
                            Button(onClick = {
                                if (OnboardingStore.finishTutorial(prefs)) { refresh(); go("home") }
                                else error = "Could not save. Please try again."
                            }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("tutorial_done")) { Text("Finish") }
                        }
                    }
                }
                composable("leaderboard") { LeaderboardScreen(prefs, ::go) }
                composable("privacy") { PrivacyDocumentScreen(false) }
                composable("terms") { PrivacyDocumentScreen(true) }
                composable("statistics") {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { go("leaderboard") }) { Text("Leaderboard") }
                            OutlinedButton(onClick = { chapterSubject = null; chapterToOpen = null; go("syllabus") }) { Text("Syllabus") }
                            OutlinedButton(onClick = { go("focus_history") }) { Text("History") }
                        }
                        Box(Modifier.weight(1f)) { StatisticsScreen(prefs, 0, "Your progress", "Every session adds up.") }
                    }
                }
                composable("study_history") { StatisticsScreen(prefs, 0, "Study history", "Tasks, questions and subject progress.") }
                composable("focus_history") { StatisticsScreen(prefs, 0, "Focus history", "Focused minutes and completed sessions.") }
                composable("app_usage") { StatisticsScreen(prefs, 1, "App usage", "Your device usage and limit activity.") }
                composable("limits") { AppLimitsScreen(::go) }
                composable("profile") { ProfileScreen(prefs, refresh, ::go) }
                composable("notes") { NotesHistoryScreen() }
            }
        }
    }
    if (quickTask) QuickStudyTaskDialog(prefs, onDismiss = { quickTask = false }) {
        quickTask = false; refresh()
    }
}

@Composable
internal fun AppHeading(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing() else FeatureArtwork(headingArtwork(title))
    }
}

@Composable
internal fun StudyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth().focusIqGlow(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) { content() }
    }
}

@Composable
internal fun BlueHeroCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)))
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
internal fun SectionLabel(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
}
internal fun chapterKey(course: String, grade: String, subject: String, index: Int): String =
    if (course == "CET") "${grade}_${subject}_$index" else "${course}_${grade}_${subject}_$index"

@Composable
private fun HomeScreen(prefs: SharedPreferences, revision: Int, go: (String) -> Unit) {
    val course = prefs.getString("exam", "CET") ?: "CET"
    val grade = prefs.getString("grade", "11") ?: "11"
    val name = prefs.getString("profile_name", "").orEmpty().trim().ifBlank { "Student" }
    val context = LocalContext.current
    val events = remember { StudyData.events(context) }
    val eventRevision by events.revision.collectAsState()
    val totals by produceState(StudyTotals(), eventRevision, revision) {
        value = withContext(Dispatchers.IO) { events.totals(1) }
    }
    val streak by produceState(0, eventRevision) {
        value = withContext(Dispatchers.IO) { events.streak() }
    }
    val tasks = orderedTasks(prefs)
    val goal = prefs.getInt("daily_focus_goal", 120).coerceAtLeast(1)
    val progress = (totals.focusedMinutes.toFloat() / goal).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp)) {
        FocusIqHeader(prefs, "Welcome back", go)
        Spacer(Modifier.height(24.dp))
        StudyCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Today's Focus Time", style = MaterialTheme.typography.titleMedium)
                    Text("${totals.focusedMinutes / 60}h ${totals.focusedMinutes % 60}m",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))
                    FocusIqTaskButton { go("plan") }
                }
                FeatureArtwork(R.drawable.art_focus_3d, 88.dp)
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text("${(progress * 100).toInt()}% of your ${goal} min daily goal",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(18.dp))
        FocusIqButton("Start focus", Modifier.testTag("home_start_focus")) { go("focus") }
        Spacer(Modifier.height(18.dp))
        StudyCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(R.drawable.figma_suggestion), contentDescription = null, modifier = Modifier.size(24.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
                Text("Study suggestion", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(if (tasks.isEmpty()) "Try a 25-minute focus session. Add one clear next step and give it your full attention."
                else "Start with ${tasks.first()}. Give this one task 25 minutes of uninterrupted attention.",
                style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(22.dp))
        tasks.take(3).forEach { task ->
            StudyCard(Modifier.clickable { go("plan") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(20.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
                    Text(task, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Surface(shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, Color(0xFF428273))) {
                        Text("Study", modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$course · Class $grade", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = {
                        prefs.edit().putString("focus_intention", task).apply(); go("focus")
                    }) { Image(painterResource(R.drawable.figma_task_play), "Focus on $task", Modifier.size(24.dp)) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (tasks.isEmpty()) StudyCard(Modifier.clickable { go("plan") }) {
            Text("Your next step", style = MaterialTheme.typography.titleMedium)
            Text("Add your first study task", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
        }
        HomeShortcuts(go)
        SectionLabel("At a glance")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeSnapshotCard("Tasks to do", "${tasks.size}", LeafMint, Modifier.weight(1f)) { go("plan") }
            HomeSnapshotCard("Study streak", "$streak days", LeafMint, Modifier.weight(1f)) { go("statistics") }
        }
        TextButton(onClick = { go("statistics") }) { Text("See your progress and leaderboard →") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeSnapshotCard(label: String, value: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = MaterialTheme.colorScheme
    val cardColor = if (palette.background == MintBackground) tint else palette.surfaceVariant
    Surface(modifier = modifier.heightIn(min = 112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp), color = cardColor) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = palette.onSurface)
        }
    }
}

@Composable
internal fun ProfileAvatar(name: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val profilePrefs = context.getSharedPreferences("study_sprint", android.content.Context.MODE_PRIVATE)
    val path = profilePrefs.getString("profile_photo", null)
    val accountPhoto = profilePrefs.getString("account_photo_url", null)
    val photo by produceState<android.graphics.Bitmap?>(null, path, accountPhoto) {
        value = LeaderboardPhotos.bitmap(context)
    }
    val displayedPhoto = photo
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (displayedPhoto != null) Image(displayedPhoto.asImageBitmap(), contentDescription = "Profile photo",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.trim().take(1).uppercase(), fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
internal fun WelcomeScreen(prefs: SharedPreferences, onContinue: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val appVersion = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.2.0" }
    val scope = rememberCoroutineScope()
    val webClientId = stringResource(R.string.default_web_client_id).trim()
    var name by remember { mutableStateOf(prefs.getString("profile_name", "").orEmpty()) }
    var course by remember { mutableStateOf(prefs.getString("exam", "CET") ?: "CET") }
    var grade by remember { mutableStateOf(prefs.getString("grade", "11") ?: "11") }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var authLoading by remember { mutableStateOf(false) }
    val loginEmerald = Color(0xFF0B2B23)
    var policyDocument by remember { mutableStateOf<String?>(null) }
    if (policyDocument != null) PrivacyDocumentDialog(policyDocument == "terms") { policyDocument = null }
    val loginDeep = Color(0xFF145C46)

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().height(280.dp)
                .background(Brush.linearGradient(listOf(loginEmerald, loginDeep))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(width = 310.dp, height = 96.dp)
                    .offset(x = (-110).dp, y = (-82).dp)
                    .graphicsLayer(rotationZ = -12f, alpha = 0.12f)
                    .background(Color.White, RoundedCornerShape(36.dp))
            )
            Box(
                Modifier.size(width = 280.dp, height = 88.dp)
                    .offset(x = 130.dp, y = 105.dp)
                    .graphicsLayer(rotationZ = -16f, alpha = 0.11f)
                    .background(Color.White, RoundedCornerShape(36.dp))
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.art_login_3d),
                    contentDescription = "3D study book, graduation cap and focus timer",
                    modifier = Modifier.size(width = 240.dp, height = 160.dp).testTag("login_3d_art"),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(10.dp))
                Text("Study Sprint", color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium)
                Text("Focus • Practice • Progress", color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().offset(y = (-28).dp),
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Your study profile", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Step 2 · Choose your learning path. Google sign-in is optional.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    placeholder = { Text("Student name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Text("Choose your exam", fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 22.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CET", "JEE", "NEET").forEach { option ->
                        FilterChip(
                            selected = course == option,
                            onClick = { course = option },
                            label = { Text(option) }
                        )
                    }
                }

                Text("Your class", fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("11", "12").forEach { option ->
                        FilterChip(
                            selected = grade == option,
                            onClick = { grade = option },
                            label = { Text("Class $option") }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        scope.launch {
                            authLoading = true
                            try {
                                val account = GoogleAccountAuth.signIn(context, webClientId)
                                val chosenName = name.trim().ifBlank { account.displayName }
                                prefs.edit()
                                    .putBoolean("google_signed_in", true)
                                    .putString("account_email", account.email)
                                    .putString("account_name", account.displayName)
                                    .putString("account_photo_url", account.photoUrl)
                                    .apply()
                                onContinue(chosenName, course, grade)
                            } catch (error: Throwable) {
                                authMessage = GoogleAccountAuth.userMessage(error)
                            } finally {
                                authLoading = false
                            }
                        }
                    },
                    enabled = !authLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (authLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Opening Google…")
                    } else {
                        Text("Sign in with Google")
                    }
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("  OR  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f))
                }

                OutlinedButton(
                    onClick = { onContinue(name.trim().ifBlank { "Student" }, course, grade) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Continue as guest", color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Google sign-in is optional. Guest mode keeps your study data on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text("Study Sprint • v$appVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { policyDocument = "privacy" }) { Text("Privacy Policy") }
                    TextButton(onClick = { policyDocument = "terms" }) { Text("Terms of Use") }
                }
            }
        }
    }

    if (authMessage != null) AlertDialog(
        onDismissRequest = { authMessage = null },
        title = { Text("Google sign-in") },
        text = { Text(authMessage.orEmpty()) },
        confirmButton = { TextButton(onClick = { authMessage = null }) { Text("OK") } }
    )
}
@Composable
private fun ProfileScreen(prefs: SharedPreferences, refresh: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webClientId = stringResource(R.string.default_web_client_id).trim()
    var accountEmail by remember { mutableStateOf(prefs.getString("account_email", null)) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(prefs.getString("profile_name", "").orEmpty()) }
    var course by remember { mutableStateOf(prefs.getString("exam", "CET") ?: "CET") }
    var grade by remember { mutableStateOf(prefs.getString("grade", "11") ?: "11") }
    var examDate by remember { mutableLongStateOf(prefs.getLong("exam_date", System.currentTimeMillis() + 547L * 86400000L)) }
    var photoVersion by remember { mutableIntStateOf(0) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && ProfilePhotos.fromUri(context, uri) != null) { photoVersion++; refresh() }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null && ProfilePhotos.save(context, bitmap) != null) { photoVersion++; refresh() }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(null)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        AppHeading("Your profile", "Your learning path, photo and exam goal.")
        key(photoVersion) { ProfileAvatar(name.ifBlank { "Student" }, 76.dp) {} }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { gallery.launch("image/*") }) { Text("Choose photo") }
            TextButton(onClick = { cameraPermission.launch(android.Manifest.permission.CAMERA) }) { Text("Take photo") }
        }
        if (prefs.getString("profile_photo", null) != null) TextButton(onClick = {
            ProfilePhotos.remove(context); photoVersion++; refresh()
        }) { Text("Remove photo") }
        Spacer(Modifier.height(8.dp))
        Text(name.ifBlank { "Student" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("$course • Class $grade", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        StudyCard {
            Text("Google account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (accountEmail == null) {
                Text("Optional. Your study data continues to work locally.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            accountBusy = true
                            try {
                                val account = GoogleAccountAuth.signIn(context, webClientId)
                                accountEmail = account.email
                                if (name.isBlank()) name = account.displayName
                                prefs.edit().putBoolean("google_signed_in", true)
                                    .putString("account_email", account.email)
                                    .putString("account_name", account.displayName)
                                    .putString("account_photo_url", account.photoUrl).apply()
                                refresh()
                            } catch (error: Throwable) {
                                accountMessage = GoogleAccountAuth.userMessage(error)
                            } finally { accountBusy = false }
                        }
                    },
                    enabled = !accountBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (accountBusy) "Opening Google…" else "Sign in with Google") }
            } else {
                Text(accountEmail.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    scope.launch {
                        accountBusy = true
                        try {
                            GoogleAccountAuth.signOut(context)
                            prefs.edit().remove("google_signed_in").remove("account_email")
                                .remove("account_name").remove("account_photo_url").apply()
                            accountEmail = null
                            refresh()
                        } catch (error: Throwable) {
                            accountMessage = GoogleAccountAuth.userMessage(error)
                        } finally { accountBusy = false }
                    }
                }, enabled = !accountBusy) { Text("Sign out") }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel("Learning path")
        StudyCard {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        SectionLabel("Exam")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CET", "JEE", "NEET").forEach {
                FilterChip(selected = course == it, onClick = { course = it }, label = { Text(it) })
            }
        }
        SectionLabel("Class")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("11", "12").forEach {
                FilterChip(selected = grade == it, onClick = { grade = it }, label = { Text(it) })
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = {
            val calendar = Calendar.getInstance().apply { timeInMillis = examDate }
            DatePickerDialog(context, { _, year, month, day ->
                examDate = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }.timeInMillis
                prefs.edit().putLong("exam_date", examDate).apply(); refresh()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Exam date: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(examDate))}")
        }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            prefs.edit().putString("profile_name", name.trim()).putString("exam", course)
                .putString("grade", grade).apply()
            refresh(); go("home")
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save profile") }
        Text(prefs.getString("account_email", null)?.let { "Signed in as $it. Study data remains local until Firebase sync is configured." } ?: "Using Study Sprint locally. Account sync is not configured.",
            modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall)
    }
    if (accountMessage != null) AlertDialog(onDismissRequest = { accountMessage = null },
        title = { Text("Google account") }, text = { Text(accountMessage.orEmpty()) },
        confirmButton = { TextButton(onClick = { accountMessage = null }) { Text("OK") } })
}
