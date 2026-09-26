package app.taskdav

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.taskdav.data.SyncBackend
import app.taskdav.sync.CalDavSyncWorker
import app.taskdav.ui.calendar.CalendarScreen
import app.taskdav.ui.calendar.CalendarViewModel
import app.taskdav.ui.common.BottomEdgeFade
import app.taskdav.ui.common.SyncBadgeClearance
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
import app.taskdav.ui.theme.TaskDavRadii
import app.taskdav.ui.theme.TaskDavTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TaskDavApp
        val fromCalendarIntent = app.consumeCalendarIntent(intent)
        val widgetNav = app.parseWidgetIntent(intent)
        setContent {
            TaskDavTheme {
                TaskDavNav(
                    app = app,
                    preferCalendarTab = fromCalendarIntent,
                    initialWidgetNav = widgetNav,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as TaskDavApp
        app.consumeCalendarIntent(intent)
        app.parseWidgetIntent(intent)?.let { app.offerWidgetNavigation(it) }
    }
}

@Composable
private fun TaskDavNav(
    app: TaskDavApp,
    preferCalendarTab: Boolean = false,
    initialWidgetNav: WidgetNavigation? = null,
) {
    val navController = rememberNavController()
    val start = when {
        preferCalendarTab &&
            (app.repository.syncBackend() == SyncBackend.LOCAL || app.repository.accountConfigured()) ->
            "home/calendar"
        initialWidgetNav != null &&
            (app.repository.syncBackend() == SyncBackend.LOCAL || app.repository.accountConfigured()) ->
            "home/${initialWidgetNav.tab}"
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

    LaunchedEffect(Unit) {
        fun applyWidgetNav(nav: WidgetNavigation) {
            navController.navigate("home/${nav.tab}") {
                launchSingleTop = true
            }
            when {
                nav.taskId != null -> navController.navigate("task/${nav.taskId}")
                nav.noteId != null -> navController.navigate("noteEditor?noteId=${nav.noteId}")
            }
        }
        if (initialWidgetNav?.taskId != null || initialWidgetNav?.noteId != null) {
            applyWidgetNav(initialWidgetNav)
        }
        app.widgetNavigation.collect { applyWidgetNav(it) }
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
    val isOnline by app.isOnline.collectAsStateWithLifecycle()

    // Keep visited tabs composed so switches crossfade instead of remounting to blank.
    var visitedTabs by remember { mutableStateOf(setOf(selectedTab)) }
    LaunchedEffect(selectedTab) {
        visitedTabs = visitedTabs + selectedTab
    }

    val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(app.repository))
    val calendarVm: CalendarViewModel = viewModel(
        factory = CalendarViewModel.Factory(app, app.repository),
    )
    val tasksVm: TasksViewModel = viewModel(factory = TasksViewModel.Factory(app, app.repository))
    val notesVm: NotesViewModel = viewModel(factory = NotesViewModel.Factory(app, app.repository))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = SyncBadgeClearance),
            ) {
                if ("home" in visitedTabs) {
                    KeepAliveTab(visible = selectedTab == "home") {
                        HomeScreen(
                            viewModel = homeVm,
                            onOpenTask = { onEditTask(it, false, null) },
                            onOpenNote = { onEditNote(it) },
                            onSeeAllTasks = { onSelectTab("tasks") },
                            onSeeAllNotes = { onSelectTab("notes") },
                            onSeeCalendar = { onSelectTab("calendar") },
                        )
                    }
                }
                if ("calendar" in visitedTabs) {
                    KeepAliveTab(visible = selectedTab == "calendar") {
                        CalendarScreen(
                            viewModel = calendarVm,
                            onOpenTask = { id -> onEditTask(id, false, null) },
                        )
                    }
                }
                if ("tasks" in visitedTabs) {
                    KeepAliveTab(visible = selectedTab == "tasks") {
                        TasksScreen(
                            viewModel = tasksVm,
                            onEditTask = { id, isCategory, collectionId ->
                                onEditTask(id, isCategory, collectionId)
                            },
                            onAddSubtask = onAddSubtask,
                        )
                    }
                }
                if ("notes" in visitedTabs) {
                    KeepAliveTab(visible = selectedTab == "notes") {
                        NotesScreen(
                            viewModel = notesVm,
                            onEditNote = onEditNote,
                        )
                    }
                }
                if ("settings" in visitedTabs) {
                    KeepAliveTab(visible = selectedTab == "settings") {
                        SettingsHubScreen(
                            onBack = null,
                            onSyncing = { onOpenSettingsPage("settings/account") },
                            onAppearance = { onOpenSettingsPage("settings/appearance") },
                            onExport = { onOpenSettingsPage("settings/export") },
                            onPrivacy = { onOpenSettingsPage("settings/privacy") },
                            onCredits = { onOpenSettingsPage("settings/credits") },
                        )
                    }
                }
            }

            BottomEdgeFade(
                modifier = Modifier.align(Alignment.BottomCenter),
                height = 120.dp,
            )

            ExpressiveBottomNav(
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            SyncModeBadge(
                backend = syncBackend,
                isOnline = isOnline,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun KeepAliveTab(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "tabAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .graphicsLayer { this.alpha = alpha },
    ) {
        content()
    }
}

private data class NavDest(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
private fun ExpressiveBottomNav(
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = listOf(
        NavDest("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        NavDest("calendar", "Cal", Icons.Filled.Event, Icons.Outlined.Event),
        NavDest("tasks", "Tasks", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
        NavDest("notes", "Notes", Icons.Filled.Description, Icons.Outlined.Description),
        NavDest("settings", "More", Icons.Filled.Settings, Icons.Outlined.Settings),
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            shape = RoundedCornerShape(TaskDavRadii.nav),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 10.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { dest ->
                    val selected = selectedTab == dest.id
                    val container by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "navBg",
                    )
                    val content by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "navFg",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(TaskDavRadii.pill))
                            .background(container)
                            .semantics {
                                this.selected = selected
                                role = Role.Tab
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectTab(dest.id) },
                            )
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label,
                            tint = content,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncModeBadge(
    backend: SyncBackend,
    isOnline: Boolean,
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
    val showOffline = backend == SyncBackend.CALDAV && !isOnline
    Surface(
        modifier = modifier.padding(top = 10.dp),
        shape = RoundedCornerShape(TaskDavRadii.pill),
        color = container,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                backend.badgeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                textAlign = TextAlign.Center,
            )
            if (showOffline) {
                Icon(
                    Icons.Filled.CloudOff,
                    contentDescription = "Offline",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
