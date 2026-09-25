package app.taskdav

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.taskdav.data.SyncBackend
import app.taskdav.ui.calendar.CalendarScreen
import app.taskdav.ui.calendar.CalendarViewModel
import app.taskdav.ui.editor.EditorScreen
import app.taskdav.ui.editor.EditorViewModel
import app.taskdav.ui.home.HomeScreen
import app.taskdav.ui.home.HomeViewModel
import app.taskdav.ui.notes.NoteEditorScreen
import app.taskdav.ui.notes.NoteEditorViewModel
import app.taskdav.ui.notes.NotesScreen
import app.taskdav.ui.notes.NotesViewModel
import app.taskdav.ui.settings.AccountSettingsScreen
import app.taskdav.ui.settings.AppearanceSettingsScreen
import app.taskdav.ui.settings.CollectionsSettingsScreen
import app.taskdav.ui.settings.CreditsSettingsScreen
import app.taskdav.ui.settings.ExportSettingsScreen
import app.taskdav.ui.settings.PrivacySettingsScreen
import app.taskdav.ui.settings.SettingsHubScreen
import app.taskdav.ui.setup.SetupViewModel
import app.taskdav.ui.task.TaskDetailScreen
import app.taskdav.ui.task.TaskDetailViewModel
import app.taskdav.ui.tasks.TasksScreen
import app.taskdav.ui.tasks.TasksViewModel
import app.taskdav.ui.theme.TaskDavTheme
import app.taskdav.sync.CalDavSyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TaskDavApp
        val fromCalendarIntent = app.consumeCalendarIntent(intent)
        setContent {
            TaskDavTheme {
                TaskDavNav(app, preferCalendarTab = fromCalendarIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (application as TaskDavApp).consumeCalendarIntent(intent)
    }
}

@Composable
private fun TaskDavNav(app: TaskDavApp, preferCalendarTab: Boolean = false) {
    val navController = rememberNavController()
    val start = when {
        preferCalendarTab &&
            (app.repository.syncBackend() == SyncBackend.LOCAL || app.repository.accountConfigured()) ->
            "home/calendar"
        app.repository.syncBackend() == SyncBackend.LOCAL -> "home/home"
        app.repository.accountConfigured() -> "home/home"
        else -> "settings/account"
    }

    LaunchedEffect(Unit) {
        app.navigateToCalendar.collect {
            navController.navigate("home/calendar") {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable("home/{tab}") { entry ->
            val tab = entry.arguments?.getString("tab") ?: "home"
            HomeScaffold(
                app = app,
                selectedTab = tab,
                onSelectTab = { navController.navigate("home/$it") { launchSingleTop = true } },
                onOpenSettingsPage = { route -> navController.navigate(route) },
                onEditTask = { id, isCategory, collectionId ->
                    when {
                        id != null -> navController.navigate("task/$id")
                        else -> {
                            val parts = mutableListOf("taskId=-1")
                            if (isCategory) parts += "isCategory=1"
                            if (collectionId != null) parts += "collectionId=$collectionId"
                            navController.navigate("editor?${parts.joinToString("&")}")
                        }
                    }
                },
                onAddSubtask = { parentUid, collectionId ->
                    navController.navigate(
                        "editor?taskId=-1&parentUid=$parentUid&collectionId=$collectionId",
                    )
                },
                onEditNote = { id ->
                    if (id == null) navController.navigate("noteEditor?noteId=-1")
                    else navController.navigate("noteEditor?noteId=$id")
                },
            )
        }

        composable(
            route = "task/{taskId}",
            arguments = listOf(
                navArgument("taskId") { type = NavType.LongType },
            ),
        ) { entry ->
            val taskId = entry.arguments?.getLong("taskId") ?: return@composable
            val factory = remember(taskId) {
                TaskDetailViewModel.Factory(app, app.repository, taskId)
            }
            val vm: TaskDetailViewModel = viewModel(factory = factory)
            TaskDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("editor?taskId=$taskId") },
                onOpenSubtask = { id -> navController.navigate("task/$id") },
            )
        }

        composable("settings/account") {
            val vm: SetupViewModel = viewModel(
                factory = SetupViewModel.Factory(app, app.repository),
            )
            AccountSettingsScreen(
                viewModel = vm,
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate("home/settings")
                    }
                },
                onOpenCollections = {
                    navController.navigate("settings/collections") {
                        popUpTo("settings/account") { inclusive = false }
                    }
                },
                onLocalReady = {
                    navController.navigate("home/home") {
                        popUpTo("settings/account") { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable("settings/collections") {
            val vm: SetupViewModel = viewModel(
                factory = SetupViewModel.Factory(app, app.repository),
            )
            CollectionsSettingsScreen(
                viewModel = vm,
                onBack = {
                    if (app.repository.accountConfigured()) {
                        navController.navigate("home/settings") {
                            popUpTo("settings/account") { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onDone = {
                    CalDavSyncWorker.enqueueNow(app)
                    navController.navigate("home/home") {
                        popUpTo("settings/account") { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable("settings/appearance") {
            AppearanceSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/export") {
            ExportSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/privacy") {
            PrivacySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("settings/credits") {
            CreditsSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "editor?taskId={taskId}&parentUid={parentUid}&collectionId={collectionId}&isCategory={isCategory}",
            arguments = listOf(
                navArgument("taskId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("parentUid") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("collectionId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("isCategory") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            val taskId = (entry.arguments?.getLong("taskId") ?: -1L).takeIf { it >= 0 }
            val parentUid = entry.arguments?.getString("parentUid")
            val collectionId = (entry.arguments?.getLong("collectionId") ?: -1L).takeIf { it >= 0 }
            val isCategory = (entry.arguments?.getInt("isCategory") ?: 0) == 1
            val factory = remember(taskId, parentUid, collectionId, isCategory) {
                EditorViewModel.Factory(app, app.repository, taskId, parentUid, collectionId, isCategory)
            }
            val vm: EditorViewModel = viewModel(factory = factory)
            EditorScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = "noteEditor?noteId={noteId}",
            arguments = listOf(
                navArgument("noteId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val noteId = (entry.arguments?.getLong("noteId") ?: -1L).takeIf { it >= 0 }
            val factory = remember(noteId) {
                NoteEditorViewModel.Factory(app, app.repository, noteId)
            }
            val vm: NoteEditorViewModel = viewModel(factory = factory)
            NoteEditorScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun HomeScaffold(
    app: TaskDavApp,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    onOpenSettingsPage: (String) -> Unit,
    onEditTask: (Long?, Boolean, Long?) -> Unit,
    onAddSubtask: (String, Long) -> Unit,
    onEditNote: (Long?) -> Unit,
) {
    val syncBackend by app.syncBackend.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == "home",
                    onClick = { onSelectTab("home") },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = selectedTab == "calendar",
                    onClick = { onSelectTab("calendar") },
                    icon = { Icon(Icons.Default.Event, contentDescription = null) },
                    label = { Text("Calendar") },
                )
                NavigationBarItem(
                    selected = selectedTab == "tasks",
                    onClick = { onSelectTab("tasks") },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    label = { Text("Tasks") },
                )
                NavigationBarItem(
                    selected = selectedTab == "notes",
                    onClick = { onSelectTab("notes") },
                    icon = { Icon(Icons.Default.Description, contentDescription = null) },
                    label = { Text("Notes") },
                )
                NavigationBarItem(
                    selected = selectedTab == "settings",
                    onClick = { onSelectTab("settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                "home" -> {
                    val vm: HomeViewModel = viewModel(
                        factory = HomeViewModel.Factory(app.repository),
                    )
                    HomeScreen(
                        viewModel = vm,
                        onOpenTask = { onEditTask(it, false, null) },
                        onOpenNote = { onEditNote(it) },
                        onSeeAllTasks = { onSelectTab("tasks") },
                        onSeeAllNotes = { onSelectTab("notes") },
                        onSeeCalendar = { onSelectTab("calendar") },
                    )
                }
                "calendar" -> {
                    val vm: CalendarViewModel = viewModel(
                        factory = CalendarViewModel.Factory(app, app.repository),
                    )
                    CalendarScreen(
                        viewModel = vm,
                        onOpenTask = { id -> onEditTask(id, false, null) },
                    )
                }
                "notes" -> {
                    val vm: NotesViewModel = viewModel(
                        factory = NotesViewModel.Factory(app, app.repository),
                    )
                    NotesScreen(
                        viewModel = vm,
                        onEditNote = onEditNote,
                    )
                }
                "settings" -> {
                    SettingsHubScreen(
                        onBack = null,
                        onSyncing = { onOpenSettingsPage("settings/account") },
                        onAppearance = { onOpenSettingsPage("settings/appearance") },
                        onExport = { onOpenSettingsPage("settings/export") },
                        onPrivacy = { onOpenSettingsPage("settings/privacy") },
                        onCredits = { onOpenSettingsPage("settings/credits") },
                    )
                }
                else -> {
                    val vm: TasksViewModel = viewModel(
                        factory = TasksViewModel.Factory(app, app.repository),
                    )
                    TasksScreen(
                        viewModel = vm,
                        onEditTask = { id, isCategory, collectionId ->
                            onEditTask(id, isCategory, collectionId)
                        },
                        onAddSubtask = onAddSubtask,
                    )
                }
            }

            SyncModeBadge(
                backend = syncBackend,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun SyncModeBadge(
    backend: SyncBackend,
    modifier: Modifier = Modifier,
) {
    val container = when (backend) {
        SyncBackend.LOCAL -> MaterialTheme.colorScheme.tertiaryContainer
        SyncBackend.CALDAV -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when (backend) {
        SyncBackend.LOCAL -> MaterialTheme.colorScheme.onTertiaryContainer
        SyncBackend.CALDAV -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = container,
        tonalElevation = 1.dp,
    ) {
        Text(
            backend.badgeLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}
