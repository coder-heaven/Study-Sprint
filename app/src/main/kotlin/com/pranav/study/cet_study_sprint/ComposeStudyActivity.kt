package com.pranav.study.cet_study_sprint

import android.app.DatePickerDialog
import android.content.Intent
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal val Pine = Color(0xFF157C6D)
internal val DeepPine = Color(0xFF17463C)
internal val MintBackground = Color(0xFFF5F8F5)
internal val LeafMint = Color(0xFFE5F3E9)
internal val MutedInk = Color(0xFF668076)
internal val WarmCream = Color(0xFFFFFCF2)

class ComposeStudyActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("study_sprint", MODE_PRIVATE) }
    private var revision by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            StudyTheme(prefs, revision) {
                var ready by remember { mutableStateOf(prefs.getBoolean("onboarding_v2", false)) }
                if (!ready) WelcomeScreen(prefs) { name, course, grade ->
                    prefs.edit().putString("profile_name", name.trim()).putString("exam", course)
                        .putString("grade", grade).putBoolean("onboarding_v2", true).apply()
                    ready = true; revision++
                } else StudyRoot(prefs, revision, onLegacy = { destination ->
                    startActivity(Intent(this, MainActivity::class.java).putExtra("legacy_screen", destination))
                }, refresh = { revision++ })
            }
        }
    }
    override fun onResume() { super.onResume(); revision++ }
}

@Composable
private fun StudyTheme(prefs: SharedPreferences, revision: Int, content: @Composable () -> Unit) {
    val selectedTheme = remember(revision) { prefs.getString("theme_mode", "system") }
    val dark = when (selectedTheme) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? android.app.Activity
        activity?.window?.let { window ->
            val bar = if (dark) android.graphics.Color.rgb(16, 34, 30)
                else android.graphics.Color.rgb(245, 248, 245)
            window.statusBarColor = bar
            window.navigationBarColor = bar
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF85D7BC), onPrimary = Color(0xFF063E32),
        background = Color(0xFF10221E), surface = Color(0xFF18302A),
        onSurface = Color(0xFFE1F2E9), primaryContainer = Color(0xFF225344)
    ) else lightColorScheme(
        primary = Pine, onPrimary = Color.White, primaryContainer = LeafMint,
        onPrimaryContainer = DeepPine, secondary = Color(0xFF4E9C78),
        background = MintBackground, surface = Color.White, onSurface = DeepPine
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyRoot(prefs: SharedPreferences, revision: Int, onLegacy: (String) -> Unit, refresh: () -> Unit) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route ?: "home"
    val primary = remember { listOf("home", "syllabus", "practice", "plan", "focus", "settings") }
    val drawerDestinations = remember {
        setOf("home", "statistics", "study_history", "focus_history", "notes", "limits", "app_usage", "profile", "settings")
    }
    fun go(target: String) {
        scope.launch {
            val current = nav.currentBackStackEntry?.destination?.route
            if (current != target) {
                nav.navigate(target) {
                    launchSingleTop = true
                    if (target in primary) {
                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                        restoreState = true
                    } else {
                        current?.takeIf { it in drawerDestinations }?.let { currentDrawerRoute ->
                            popUpTo(currentDrawerRoute) { inclusive = true }
                        }
                    }
                }
            }
            if (drawer.isOpen) drawer.close()
        }
    }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(
            modifier = Modifier.width(304.dp).fillMaxHeight(),
            drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        ) {
            val name = prefs.getString("profile_name", "").orEmpty().ifBlank { "Student" }
            val groups = listOf(
                "OVERVIEW" to listOf("Dashboard" to "home", "Statistics" to "statistics"),
                "STUDY" to listOf("Study history" to "study_history", "Focus history" to "focus_history", "What I learned" to "notes"),
                "DIGITAL WELLBEING" to listOf("App Limits" to "limits", "App Usage" to "app_usage"),
                "PERSONAL" to listOf("Profile" to "profile", "Settings" to "settings")
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
            topBar = {
                TopAppBar(
                    title = { Text(when (route) {
                        "home" -> "Study Sprint"
                        "mcq_editor" -> "My MCQs"
                        "arihant" -> "Arihant log"
                        "study_history" -> "Study history"
                        "focus_history" -> "Focus history"
                        "app_usage" -> "App usage"
                        else -> route.replace('_', ' ').replaceFirstChar { it.uppercase() }
                    },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    } },
                    actions = { IconButton(onClick = { go("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    } }
                )
            },
            bottomBar = {
                if (route in primary) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        primary.forEach { item ->
                            val res = when (item) {
                                "home" -> R.drawable.nav_home
                                "syllabus" -> R.drawable.nav_syllabus
                                "practice" -> R.drawable.nav_practice
                                "plan" -> R.drawable.nav_plan
                                "focus" -> R.drawable.nav_focus
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = route == item,
                                onClick = { go(item) },
                                icon = {
                                    if (res == 0) Icon(Icons.Default.Settings, contentDescription = item)
                                    else Icon(painterResource(res), contentDescription = item, modifier = Modifier.size(22.dp))
                                },
                                label = { Text(item.replaceFirstChar { it.uppercase() }, fontSize = 9.sp, maxLines = 1) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.padding(padding),
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
                composable("home") { HomeScreen(prefs, revision, ::go) }
                composable("syllabus") { SyllabusScreen(prefs, revision) }
                composable("practice") { PracticeScreen(prefs, revision) { destination ->
                    when (destination) {
                        "import" -> go("mcq_editor")
                        "arihant" -> go("arihant")
                        else -> onLegacy(destination)
                    }
                } }
                composable("arihant") { ArihantPracticeScreen(prefs, onBack = { go("practice") }) }
                composable("mcq_editor") { McqEditorScreen(prefs, onBack = { go("practice") }) {
                    refresh(); go("practice")
                } }
                composable("plan") { PlannerScreen(prefs, revision) }
                composable("focus") { FocusScreen(prefs, onBack = { go("home") }, onLegacy = { go("limits") }) }
                composable("settings") { SettingsScreen(prefs, ::go, refresh) }
                composable("statistics") { StatisticsScreen(prefs, 0, "Statistics", "Your complete progress, over time.") }
                composable("study_history") { StatisticsScreen(prefs, 0, "Study history", "Tasks, questions and subject progress.") }
                composable("focus_history") { StatisticsScreen(prefs, 0, "Focus history", "Focused minutes and completed sessions.") }
                composable("app_usage") { StatisticsScreen(prefs, 1, "App usage", "Your device usage and limit activity.") }
                composable("limits") { AppLimitsScreen(::go) }
                composable("profile") { ProfileScreen(prefs, refresh, ::go) }
                composable("notes") { NotesHistoryScreen() }
            }
        }
    }
}

@Composable
internal fun AppHeading(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun StudyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp)) { content() }
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
    val weekly by produceState(StudyTotals(), eventRevision) {
        value = withContext(Dispatchers.IO) { events.totals(7) }
    }
    val streak by produceState(0, eventRevision) {
        value = withContext(Dispatchers.IO) { events.streak() }
    }
    val chapters = SyllabusData.chapters(course, grade)
    val total = chapters.values.sumOf { it.size }
    val done = chapters.entries.sumOf { (subject, list) ->
        list.indices.count { prefs.getBoolean(chapterKey(course, grade, subject, it), false) }
    }
    val examDate = prefs.getLong("exam_date", System.currentTimeMillis() + 547L * 86400000L)
    val days = ((examDate - System.currentTimeMillis()) / 86400000L).coerceAtLeast(0)
    val tasks = prefs.getStringSet("tasks", emptySet()).orEmpty().sorted()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Welcome back", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            ProfileAvatar(name, 44.dp) { go("profile") }
        }
        Spacer(Modifier.height(18.dp))
        StudyCard {
            Text("$course • CLASS $grade", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text("$days days to your exam", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(examDate)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth())
            Text("$done of $total chapters complete", style = MaterialTheme.typography.bodySmall)
        }
        SectionLabel("Today")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("${totals.focusedMinutes}m", "of ${prefs.getInt("daily_focus_goal", 120)}m", Modifier.weight(1f))
            MetricCard("${tasks.size}", "Tasks left", Modifier.weight(1f))
            MetricCard("${totals.questions}", "Questions", Modifier.weight(1f))
        }
        Text("$streak-day study streak", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("Quick actions")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeAction("Focus", Modifier.weight(1f)) { go("focus") }
            HomeAction("Practice", Modifier.weight(1f)) { go("practice") }
            HomeAction("Plan", Modifier.weight(1f)) { go("plan") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { go("limits") }, modifier = Modifier.fillMaxWidth()) { Text("App limits") }
        SectionLabel("Next task")
        StudyCard {
            Text(tasks.firstOrNull() ?: "Your plan is clear. Add a task when you're ready.",
                style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { go("plan") }) { Text(if (tasks.isEmpty()) "Add a task" else "Open plan") }
        }
        SectionLabel("Subject progress")
        StudyCard {
            chapters.forEach { (subject, list) ->
                val count = list.indices.count { prefs.getBoolean(chapterKey(course, grade, subject, it), false) }
                Text("$subject  •  $count / ${list.size}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { if (list.isEmpty()) 0f else count.toFloat() / list.size },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
            }
        }
        SectionLabel("Weekly study")
        StudyCard { WeeklyStudyChart(weekly.dailyMinutes) }
        TextButton(onClick = { go("statistics") }) { Text("View statistics →") }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    StudyCard(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeAction(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label, maxLines = 1) }
}

@Composable
internal fun ProfileAvatar(name: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val path = context.getSharedPreferences("study_sprint", android.content.Context.MODE_PRIVATE).getString("profile_photo", null)
    val photo = remember(path) { path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (photo != null) Image(photo.asImageBitmap(), contentDescription = "Profile photo",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.trim().take(1).uppercase(), fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun WelcomeScreen(prefs: SharedPreferences, onContinue: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webClientId = stringResource(R.string.default_web_client_id).trim()
    var name by remember { mutableStateOf(prefs.getString("profile_name", "").orEmpty()) }
    var course by remember { mutableStateOf(prefs.getString("exam", "CET") ?: "CET") }
    var grade by remember { mutableStateOf(prefs.getString("grade", "11") ?: "11") }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var authLoading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Pine),
            contentAlignment = Alignment.Center) {
            Text("S", style = MaterialTheme.typography.headlineMedium, color = Color.White,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text("Welcome to Study Sprint", style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        Text("Plan smarter. Focus better. Keep improving.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        SectionLabel("Choose your exam")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CET", "JEE", "NEET").forEach {
                FilterChip(selected = course == it, onClick = { course = it }, label = { Text(it) })
            }
        }
        SectionLabel("Your class")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("11", "12").forEach {
                FilterChip(selected = grade == it, onClick = { grade = it }, label = { Text("Class $it") })
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onContinue(name, course, grade) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Continue without account")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
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
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (authLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Opening Google…")
            } else Text("Continue with Google")
        }
        TextButton(onClick = {
            authMessage = "Email sign-in needs a secure authentication backend. Google sign-in can be enabled with an OAuth Web client ID."
        }) { Text("Continue with email") }
        Text("Guest mode works fully offline. Google sign-in adds your account identity; cloud sync requires Firebase.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp))
    }
    if (authMessage != null) AlertDialog(onDismissRequest = { authMessage = null },
        title = { Text("Google sign-in") }, text = { Text(authMessage.orEmpty()) },
        confirmButton = { TextButton(onClick = { authMessage = null }) { Text("OK") } })
}@Composable
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
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
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
