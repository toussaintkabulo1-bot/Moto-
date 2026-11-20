package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.WewaRepository
import com.example.models.UiState
import com.example.models.User
import com.example.models.UserRole
import com.example.ui.components.OfflineStatusBanner
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ClientHomeScreen
import com.example.ui.screens.MotardHomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PendingRegistration
import com.example.ui.screens.ProfilScreen
import com.example.ui.screens.StatistiquesScreen
import com.example.ui.theme.WewaTheme
import com.example.util.attemptAutoSignIn
import com.example.util.authStateFlow
import com.example.util.configureAppCheck
import com.example.util.isNetworkAvailable
import com.example.util.observeNetworkConnectivity
import com.example.util.signOut
import com.example.viewmodels.ChatViewModel
import com.example.viewmodels.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

const val ROUTE_HOME = "home"
const val ROUTE_CONVERSATIONS = "conversations"
const val ROUTE_STATISTIQUES = "statistiques"
const val ROUTE_PROFIL = "profil"
const val ROUTE_CHAT = "chat/{rideId}"

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Obligatoire avant setContent et toute opération Firebase / AI Logic
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            WewaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WewaAppRoot()
                }
            }
        }
    }
}

@Composable
fun WewaAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val isOnline by remember(context) { observeNetworkConnectivity(context) }
        .collectAsStateWithLifecycle(initialValue = isNetworkAvailable(context))

    var pendingPhoneHint by remember { mutableStateOf("") }
    var pendingRegistration by remember { mutableStateOf<PendingRegistration?>(null) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null && isNetworkAvailable(context)) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            initialPhoneHint = pendingPhoneHint,
            isOnline = isOnline,
            onAuthSuccess = { phoneHint, registration ->
                pendingPhoneHint = phoneHint
                pendingRegistration = registration
            }
        )
    } else {
        val uid = user.uid
        var retryEpoch by remember { mutableIntStateOf(0) }
        key(uid, retryEpoch) {
            AuthenticatedWewaApp(
                currentUserId = uid,
                defaultDisplayName = user.displayName.orEmpty(),
                defaultPhoneHint = user.phoneNumber.orEmpty().ifBlank { pendingPhoneHint },
                isOnline = isOnline,
                pendingRegistration = pendingRegistration,
                onConsumePendingRegistration = { pendingRegistration = null },
                onRetry = { retryEpoch++ },
                onSignOut = {
                    pendingRegistration = null
                    signOut(credentialManager, scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedWewaApp(
    currentUserId: String,
    defaultDisplayName: String,
    defaultPhoneHint: String,
    isOnline: Boolean,
    pendingRegistration: PendingRegistration?,
    onConsumePendingRegistration: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
) {
    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                MainViewModel(
                    repository = WewaRepository(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val profileState by mainViewModel.userProfileState.collectAsStateWithLifecycle()
    val isSavingProfile by mainViewModel.isSavingProfile.collectAsStateWithLifecycle()
    val actionMessage by mainViewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearActionMessage()
        }
    }

    when (val state = profileState) {
        UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (!isOnline) {
                        "Profil non encore présent dans le cache hors ligne. Connectez-vous une première fois à Internet pour synchroniser votre compte."
                    } else {
                        "Synchronisation de votre compte en cours. Appuyez sur Réessayer pour continuer."
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text("Réessayer")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text("Se déconnecter")
                }
            }
        }

        is UiState.Success -> {
            val userProfile = state.data
            if (userProfile == null) {
                if (pendingRegistration != null) {
                    LaunchedEffect(pendingRegistration) {
                        mainViewModel.saveAccount(
                            nom = pendingRegistration.nom,
                            phone = pendingRegistration.phone.ifBlank { defaultPhoneHint },
                            role = pendingRegistration.role,
                            motoModele = pendingRegistration.motoModele,
                            plaque = pendingRegistration.plaque
                        )
                        onConsumePendingRegistration()
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    OnboardingScreen(
                        defaultName = defaultDisplayName,
                        defaultPhone = defaultPhoneHint,
                        isSaving = isSavingProfile,
                        onSignOut = onSignOut,
                        onSubmitProfile = { nom, phone, role, motoModele, plaque ->
                            mainViewModel.saveAccount(nom, phone, role, motoModele, plaque)
                        }
                    )
                }
            } else {
                if (pendingRegistration != null) {
                    LaunchedEffect(Unit) { onConsumePendingRegistration() }
                }
                WewaMainScaffold(
                    currentUser = userProfile,
                    currentUserId = currentUserId,
                    isOnline = isOnline,
                    mainViewModel = mainViewModel,
                    snackbarHostState = snackbarHostState,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@Composable
private fun WewaMainScaffold(
    currentUser: User,
    currentUserId: String,
    isOnline: Boolean,
    mainViewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isClient = currentUser.role == UserRole.CLIENT.name
    val tabs = remember(isClient) {
        if (isClient) {
            listOf(
                BottomNavTab(
                    route = ROUTE_HOME,
                    label = "Accueil",
                    selectedIcon = Icons.Filled.TwoWheeler,
                    unselectedIcon = Icons.Outlined.TwoWheeler,
                    testTag = "nav_home"
                ),
                BottomNavTab(
                    route = ROUTE_CONVERSATIONS,
                    label = "Chats",
                    selectedIcon = Icons.AutoMirrored.Filled.Chat,
                    unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
                    testTag = "nav_conversations"
                ),
                BottomNavTab(
                    route = ROUTE_STATISTIQUES,
                    label = "Stats",
                    selectedIcon = Icons.Filled.BarChart,
                    unselectedIcon = Icons.Outlined.BarChart,
                    testTag = "nav_stats"
                ),
                BottomNavTab(
                    route = ROUTE_PROFIL,
                    label = "Profil",
                    selectedIcon = Icons.Filled.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    testTag = "nav_profil"
                )
            )
        } else {
            listOf(
                BottomNavTab(
                    route = ROUTE_HOME,
                    label = "Chats",
                    selectedIcon = Icons.AutoMirrored.Filled.Chat,
                    unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
                    testTag = "nav_home"
                ),
                BottomNavTab(
                    route = ROUTE_STATISTIQUES,
                    label = "Stats",
                    selectedIcon = Icons.Filled.BarChart,
                    unselectedIcon = Icons.Outlined.BarChart,
                    testTag = "nav_stats"
                ),
                BottomNavTab(
                    route = ROUTE_PROFIL,
                    label = "Profil",
                    selectedIcon = Icons.Filled.Person,
                    unselectedIcon = Icons.Outlined.Person,
                    testTag = "nav_profil"
                )
            )
        }
    }

    val showBottomOrSideNav = tabs.any { it.route == currentRoute }

    val motardsState by mainViewModel.onlineMotardsState.collectAsStateWithLifecycle()
    val ridesState by mainViewModel.userRidesState.collectAsStateWithLifecycle()
    val withdrawalsState by mainViewModel.withdrawalsState.collectAsStateWithLifecycle()
    val isOpeningConversation by mainViewModel.isOpeningConversation.collectAsStateWithLifecycle()
    val isWithdrawing by mainViewModel.isWithdrawing.collectAsStateWithLifecycle()

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(ROUTE_HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 840.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomOrSideNav && !isExpandedWidth) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                OfflineStatusBanner(visible = !isOnline)

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (showBottomOrSideNav && isExpandedWidth) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_HOME) {
                            if (isClient) {
                                ClientHomeScreen(
                                    currentUser = currentUser,
                                    motardsState = motardsState,
                                    isOpeningConversation = isOpeningConversation,
                                    onSelectMotard = { motard ->
                                        mainViewModel.openOrCreateConversation(currentUser, motard) { rideId ->
                                            navController.navigate("chat/$rideId")
                                        }
                                    }
                                )
                            } else {
                                MotardHomeScreen(
                                    currentUser = currentUser,
                                    ridesState = ridesState,
                                    onToggleOnline = { online ->
                                        mainViewModel.toggleMotardOnline(currentUser, online)
                                    },
                                    onOpenChat = { rideId ->
                                        navController.navigate("chat/$rideId")
                                    }
                                )
                            }
                        }

                        composable(ROUTE_CONVERSATIONS) {
                            MotardHomeScreen(
                                currentUser = currentUser,
                                ridesState = ridesState,
                                onToggleOnline = { online ->
                                    mainViewModel.toggleMotardOnline(currentUser, online)
                                },
                                onOpenChat = { rideId ->
                                    navController.navigate("chat/$rideId")
                                }
                            )
                        }

                        composable(ROUTE_STATISTIQUES) {
                            StatistiquesScreen(
                                currentUser = currentUser,
                                ridesState = ridesState,
                                withdrawalsState = withdrawalsState,
                                isWithdrawing = isWithdrawing,
                                isOnline = isOnline,
                                onWithdraw = { operateur, numero, montant, maxAvailable ->
                                    mainViewModel.withdrawEarnings(
                                        user = currentUser,
                                        operateur = operateur,
                                        numero = numero,
                                        montant = montant,
                                        maxAvailable = maxAvailable
                                    )
                                }
                            )
                        }

                        composable(ROUTE_PROFIL) {
                            ProfilScreen(
                                currentUser = currentUser,
                                onOpenStats = { navigateToTab(ROUTE_STATISTIQUES) },
                                onSignOut = onSignOut
                            )
                        }

                        composable(
                            route = ROUTE_CHAT,
                            arguments = listOf(navArgument("rideId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val rideId = backStackEntry.arguments?.getString("rideId").orEmpty()
                            val chatViewModel: ChatViewModel = viewModel(
                                key = "${currentUserId}_$rideId",
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY])
                                        ChatViewModel(
                                            repository = WewaRepository(app),
                                            rideId = rideId,
                                            currentUserId = currentUserId
                                        )
                                    }
                                }
                            )
                            ChatScreen(
                                currentUser = currentUser,
                                viewModel = chatViewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
