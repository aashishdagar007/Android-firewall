package com.asd.firewall.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.asd.firewall.AegisVpnService
import com.asd.firewall.AppPolicyManager
import com.asd.firewall.BootReceiver
import com.asd.firewall.ui.screens.*
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startFirewallService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Init policy manager early
        AppPolicyManager.init(this)

        setContent {
            AegisTheme {
                val onboardingDone = remember { AppPolicyManager.isOnboardingDone() }
                var showOnboarding by remember { mutableStateOf(!onboardingDone) }

                if (showOnboarding) {
                    WelcomeScreen(
                        onFinished = {
                            AppPolicyManager.setOnboardingDone()
                            showOnboarding = false
                            requestVpnPermission()
                        }
                    )
                } else {
                    AegisApp(
                        onStartVpn = ::requestVpnPermission,
                        onStopVpn  = ::stopFirewallService,
                    )
                }
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startFirewallService()
        }
    }

    private fun startFirewallService() {
        BootReceiver.setEnabled(this, true)
        val intent = Intent(this, AegisVpnService::class.java).apply {
            action = AegisVpnService.ACTION_START
        }
        startForegroundService(intent)
    }

    private fun stopFirewallService() {
        BootReceiver.setEnabled(this, false)
        val intent = Intent(this, AegisVpnService::class.java).apply {
            action = AegisVpnService.ACTION_STOP
        }
        startService(intent)
    }
}

// ── Navigation destinations ───────────────────────────────────────

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard   : Screen("dashboard",   "Dashboard",   Icons.Default.Dashboard)
    object Packets     : Screen("packets",     "Packets",     Icons.Default.List)
    object Rules       : Screen("rules",       "Rules",       Icons.Default.Rule)
    object Processes   : Screen("processes",   "Processes",   Icons.Default.Apps)
    object Threats     : Screen("threats",     "Threats",     Icons.Default.Shield)
    object Anomalies   : Screen("anomalies",   "Anomalies",   Icons.Default.Warning)
    object Connections : Screen("connections", "Connections", Icons.Default.Cable)
    object Ledger      : Screen("ledger",      "Ledger",      Icons.Default.Lock)
    // New screens
    object AppFirewall : Screen("appfirewall", "App FW",      Icons.Default.AppShortcut)
    object ThreatMap   : Screen("threatmap",   "Map",         Icons.Default.Map)
    object Report      : Screen("report",      "Report",      Icons.Default.Assessment)
    object Settings    : Screen("settings",    "Settings",    Icons.Default.Settings)
}

// Primary nav bar tabs (shown in bottom bar)
val PrimaryNavScreens = listOf(
    Screen.Dashboard, Screen.Packets, Screen.Rules, Screen.Processes
)
// Secondary tabs (drawer / bottom bar extension)
val SecondaryNavScreens = listOf(
    Screen.Threats, Screen.Anomalies, Screen.Connections, Screen.Ledger,
    Screen.AppFirewall, Screen.ThreatMap, Screen.Report, Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AegisApp(
    onStartVpn: () -> Unit,
    onStopVpn:  () -> Unit,
) {
    val vm: FirewallViewModel = viewModel()
    val navController         = rememberNavController()
    val vpnRunning            by AegisVpnService.isRunning.collectAsState()
    val threatBadge           by vm.threatBadge.collectAsState()
    val anomalyBadge          by vm.anomalyBadge.collectAsState()
    val navBackStack          by navController.currentBackStackEntryAsState()
    val currentRoute          = navBackStack?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Background radial glow
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(GlowCyan.copy(alpha = 0.06f), BgDark),
                    radius = 900f
                )
            )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                AegisTopBar(
                    currentRoute = currentRoute,
                    vpnRunning   = vpnRunning,
                    onStartVpn   = onStartVpn,
                    onStopVpn    = onStopVpn,
                )
            },
            bottomBar = {
                AegisBottomNavBar(
                    currentRoute  = currentRoute,
                    threatBadge   = threatBadge,
                    anomalyBadge  = anomalyBadge,
                    onNavigate    = { navController.navigate(it) { launchSingleTop = true } },
                )
            }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Screen.Dashboard.route,
                modifier         = Modifier.padding(innerPadding),
                enterTransition  = { fadeIn(animationSpec = tween(200)) + slideInHorizontally { it / 8 } },
                exitTransition   = { fadeOut(animationSpec = tween(200)) },
            ) {
                composable(Screen.Dashboard.route)   { DashboardScreen(vm, navController) }
                composable(Screen.Packets.route)     { PacketsScreen(vm) }
                composable(Screen.Rules.route)       { RulesScreen(vm) }
                composable(Screen.Processes.route)   { ProcessesScreen(vm) }
                composable(Screen.Threats.route)     { ThreatsScreen(vm) }
                composable(Screen.Anomalies.route)   { AnomaliesScreen(vm) }
                composable(Screen.Connections.route) { ConnectionsScreen(vm) }
                composable(Screen.Ledger.route)      { LedgerScreen(vm) }
                // New screens
                composable(Screen.AppFirewall.route) { AppFirewallScreen(vm) }
                composable(Screen.ThreatMap.route)   { ThreatMapScreen(vm) }
                composable(Screen.Report.route)      { ReportScreen(vm) }
                composable(Screen.Settings.route)    { SettingsScreen(vm) }
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AegisTopBar(
    currentRoute: String?,
    vpnRunning: Boolean,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
) {
    val title = when (currentRoute) {
        Screen.Dashboard.route   -> "Dashboard"
        Screen.Packets.route     -> "Packet Log"
        Screen.Rules.route       -> "Firewall Rules"
        Screen.Processes.route   -> "App Traffic"
        Screen.Threats.route     -> "Threat Intelligence"
        Screen.Anomalies.route   -> "Anomaly Detection"
        Screen.Connections.route -> "Live Connections"
        Screen.Ledger.route      -> "Chain Ledger"
        Screen.AppFirewall.route -> "Per-App Firewall"
        Screen.ThreatMap.route   -> "Live Threat Map"
        Screen.Report.route      -> "Security Report"
        Screen.Settings.route    -> "Settings"
        else                     -> "Aegis XII"
    }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = TextMain)
            }
        },
        actions = {
            AnimatedContent(targetState = vpnRunning, label = "vpn") { running ->
                if (running) {
                    TextButton(onClick = onStopVpn) {
                        // Pulsing dot
                        val inf = rememberInfiniteTransition(label = "dot")
                        val a by inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
                        Box(Modifier.size(8.dp).background(AccentGreen.copy(alpha = a), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("Active", color = AccentGreen, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    TextButton(onClick = onStartVpn) {
                        Icon(Icons.Default.PlayArrow, null, tint = AccentCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("Start", color = AccentCyan, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = BgPanel,
            titleContentColor = TextMain,
        )
    )
}

// ── Animated Bottom Navigation Bar ───────────────────────────────

@Composable
fun AegisBottomNavBar(
    currentRoute: String?,
    threatBadge: Int,
    anomalyBadge: Int,
    onNavigate: (String) -> Unit,
) {
    val allScreens = listOf(
        Screen.Dashboard, Screen.Rules, Screen.AppFirewall, Screen.Threats,
        Screen.ThreatMap, Screen.Report, Screen.Connections, Screen.Settings
    )

    NavigationBar(
        containerColor = BgPanel,
        contentColor   = TextMuted,
        tonalElevation = 0.dp,
    ) {
        allScreens.forEach { screen ->
            val selected = currentRoute == screen.route

            // Bounce animation on selection
            val scale by animateFloatAsState(
                targetValue   = if (selected) 1.15f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label         = "scale"
            )

            val badge = when (screen) {
                Screen.Threats  -> threatBadge
                Screen.Anomalies -> anomalyBadge
                else             -> 0
            }

            NavigationBarItem(
                selected = selected,
                onClick  = { onNavigate(screen.route) },
                icon = {
                    BadgedBox(badge = {
                        if (badge > 0) {
                            Badge(containerColor = AccentRed) {
                                Text(if (badge > 9) "9+" else badge.toString(),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }) {
                        Icon(
                            imageVector        = screen.icon,
                            contentDescription = screen.label,
                            modifier           = Modifier.size(20.dp).scale(scale),
                        )
                    }
                },
                label = {
                    Text(
                        screen.label,
                        style    = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor       = AccentCyan,
                    selectedTextColor       = AccentCyan,
                    unselectedIconColor     = TextMuted,
                    unselectedTextColor     = TextMuted,
                    indicatorColor          = AccentCyan.copy(alpha = 0.12f),
                )
            )
        }
    }
}
