package app.taskdav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
        setContent {
            TaskDavTheme {
                TaskDavNav(app)
            }
        }
    }
}

@Composable
private fun TaskDavNav(app: TaskDavApp) {
    val navController = rememberNavController()
    val start = if (app.repository.accountConfigured()) "home/home" else "settings/account"

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
                onContinueAfterDiscover = {
                    navController.navigate("settings/collections") {
                        popUpTo("settings/account") { inclusive = false }
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
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
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
                        onAccount = { onOpenSettingsPage("settings/account") },
                        onCollections = { onOpenSettingsPage("settings/collections") },
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
        }
    }
}
