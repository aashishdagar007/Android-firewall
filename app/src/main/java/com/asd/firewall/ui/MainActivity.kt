package com.asd.firewall.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.asd.firewall.AegisVpnService
import com.asd.firewall.BootReceiver
import com.asd.firewall.R
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
        setContent {
            AegisTheme {
                AegisApp(
                    onStartVpn  = ::requestVpnPermission,
                    onStopVpn   = ::stopFirewallService,
                )
            }
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // Show system VPN permission dialog
            vpnPermissionLauncher.launch(intent)
        } else {
            // Permission already granted
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

// ── Navigation destinations ──────────────────────────────────────
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard   : Screen("dashboard",   "Dashboard",   Icons.Default.Dashboard)
    object Packets     : Screen("packets",     "Packets",     Icons.Default.List)
    object Rules       : Screen("rules",       "Rules",       Icons.Default.Rule)
    object Processes   : Screen("processes",   "Processes",   Icons.Default.Apps)
    object Threats     : Screen("threats",     "Threats",     Icons.Default.Shield)
    object Anomalies   : Screen("anomalies",   "Anomalies",   Icons.Default.Warning)
    object Connections : Screen("connections", "Connections", Icons.Default.Cable)
    object Ledger      : Screen("ledger",      "Ledger",      Icons.Default.Lock)
}

val BottomNavScreens = listOf(
    Screen.Dashboard, Screen.Packets, Screen.Rules, Screen.Processes
)
val DrawerScreens = listOf(
    Screen.Threats, Screen.Anomalies, Screen.Connections, Screen.Ledger
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AegisApp(
    onStartVpn: () -> Unit,
    onStopVpn:  () -> Unit,
) {
    val vm: FirewallViewModel = viewModel()
    val navController = rememberNavController()
    val vpnRunning by AegisVpnService.isRunning.collectAsState()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Subtle background gradient glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(GlowCyan.copy(alpha = 0.07f), BgDark),
                        radius = 800f
                    )
                )
        )

        Scaffold(
            containerColor = BgDark,
            topBar = {
                AegisTopBar(
                    currentRoute   = currentRoute,
                    vpnRunning     = vpnRunning,
                    onStartVpn     = onStartVpn,
                    onStopVpn      = onStopVpn,
                )
            },
            bottomBar = {
                AegisBottomNavBar(
                    currentRoute  = currentRoute,
                    onNavigate    = { navController.navigate(it) { launchSingleTop = true } },
                )
            }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Screen.Dashboard.route,
                modifier         = Modifier.padding(innerPadding),
                enterTransition  = { fadeIn(animationSpec = tween(220)) },
                exitTransition   = { fadeOut(animationSpec = tween(220)) },
            ) {
                composable(Screen.Dashboard.route)   { DashboardScreen(vm, navController) }
                composable(Screen.Packets.route)     { PacketsScreen(vm) }
                composable(Screen.Rules.route)       { RulesScreen(vm) }
                composable(Screen.Processes.route)   { ProcessesScreen(vm) }
                composable(Screen.Threats.route)     { ThreatsScreen(vm) }
                composable(Screen.Anomalies.route)   { AnomaliesScreen(vm) }
                composable(Screen.Connections.route) { ConnectionsScreen(vm) }
                composable(Screen.Ledger.route)      { LedgerScreen(vm) }
            }
        }
    }
}

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
        else                     -> "Aegis XII"
    }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.Shield,
                    contentDescription = null,
                    tint               = AccentCyan,
                    modifier           = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextMain,
                )
            }
        },
        actions = {
            // VPN status badge
            AnimatedContent(targetState = vpnRunning) { running ->
                if (running) {
                    TextButton(onClick = onStopVpn) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = AccentRed)
                        Spacer(Modifier.width(4.dp))
                        Text("Active", color = AccentGreen, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    TextButton(onClick = onStartVpn) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = AccentCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("Start", color = AccentCyan, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = BgPanel,
            titleContentColor = TextMain,
        )
    )
}

@Composable
fun AegisBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        containerColor = BgPanel,
        contentColor   = TextMuted,
        tonalElevation = 0.dp,
    ) {
        val allScreens = BottomNavScreens + DrawerScreens
        allScreens.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                selected = selected,
                onClick  = { onNavigate(screen.route) },
                icon = {
                    Icon(
                        imageVector        = screen.icon,
                        contentDescription = screen.label,
                        modifier           = Modifier.size(20.dp),
                    )
                },
                label   = { Text(screen.label, style = MaterialTheme.typography.labelSmall) },
                colors  = NavigationBarItemDefaults.colors(
                    selectedIconColor       = AccentCyan,
                    selectedTextColor       = AccentCyan,
                    unselectedIconColor     = TextMuted,
                    unselectedTextColor     = TextMuted,
                    indicatorColor          = BgPanel,
                )
            )
        }
    }
}
