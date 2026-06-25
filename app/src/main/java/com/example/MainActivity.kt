package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFFDFBFF) // Sleek Interface background
                ) {
                    BluetoothControllerApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothControllerApp(
    viewModel: BluetoothViewModel = viewModel()
) {
    val context = LocalContext.current
    val hasPermissions by viewModel.hasPermissions.collectAsStateWithLifecycle()
    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    // Permissions Request Launcher
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.refreshPairedDevices(context)
            Toast.makeText(context, "Bluetooth permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissions are required for controller connectivity.", Toast.LENGTH_LONG).show()
        }
    }

    // Initialize & verify permissions on launch
    LaunchedEffect(Unit) {
        val alreadyGranted = viewModel.checkPermissions(context)
        if (alreadyGranted) {
            viewModel.refreshPairedDevices(context)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Logo",
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "BotControl Pro",
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp,
                            color = Color(0xFF1B1B1F),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFDFBFF),
                    titleContentColor = Color(0xFF1B1B1F)
                ),
                actions = {
                    var showHelpDialog by remember { mutableStateOf(false) }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Help Guide",
                            tint = Color(0xFF6750A4)
                        )
                    }
                    if (showHelpDialog) {
                        AlertDialog(
                            onDismissRequest = { showHelpDialog = false },
                            containerColor = Color(0xFFF3EDF7),
                            title = {
                                Text(
                                    "Robot Controller Guide",
                                    color = Color(0xFF1B1B1F),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif
                                )
                            },
                            text = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                ) {
                                    Text("Command Bindings:", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                                    Text("• Forward Button: Sends 'F'", color = Color(0xFF1B1B1F))
                                    Text("• Backward Button: Sends 'B'", color = Color(0xFF1B1B1F))
                                    Text("• Left Button: Sends 'L'", color = Color(0xFF1B1B1F))
                                    Text("• Right Button: Sends 'R'", color = Color(0xFF1B1B1F))
                                    Text("• Stop Button: Sends 'S'", color = Color(0xFF1B1B1F))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Accessory Bindings:", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                                    Text("• Headlights: ON 'W' / OFF 'w'", color = Color(0xFF1B1B1F))
                                    Text("• Horn Buzzer: Sends 'H'", color = Color(0xFF1B1B1F))
                                    Text("• Speed Control: Slider sends 'V' followed by a number (0-255), e.g., 'V150' on release.", color = Color(0xFF1B1B1F))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("ANR & Error Mitigation:", color = Color(0xFFBA1A1A), fontWeight = FontWeight.Bold)
                                    Text("• Zero UI Lag: Runs all sockets asynchronously in the background.", color = Color(0xFF1B1B1F))
                                    Text("• Deadman Switch: When active, releasing the D-pad button instantly transmits a Stop command ('S') to safeguard hardware.", color = Color(0xFF1B1B1F))
                                    Text("• Connection Check: Commands are automatically checked for active status to prevent typical socket exceptions.", color = Color(0xFF1B1B1F))
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showHelpDialog = false }) {
                                    Text("DISMISS", color = Color(0xFF6750A4))
                                }
                            }
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFFFDFBFF)
    ) { innerPadding ->
        if (!hasPermissions) {
            PermissionRequiredScreen(
                onGrantRequest = { permissionLauncher.launch(requiredPermissions) },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            val configuration = LocalConfiguration.current
            val isWideScreen = configuration.screenWidthDp > 600

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ConnectionCard(
                            viewModel = viewModel,
                            isBluetoothEnabled = isBluetoothEnabled,
                            connectionStatus = connectionStatus,
                            pairedDevices = pairedDevices,
                            selectedDevice = selectedDevice,
                            context = context
                        )
                        DpadControllerCard(viewModel = viewModel)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.8f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.weight(0.4f)) {
                            AccessoryCard(viewModel = viewModel)
                        }
                        Box(modifier = Modifier.weight(0.6f)) {
                            TerminalCard(viewModel = viewModel, logs = logs)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ConnectionCard(
                        viewModel = viewModel,
                        isBluetoothEnabled = isBluetoothEnabled,
                        connectionStatus = connectionStatus,
                        pairedDevices = pairedDevices,
                        selectedDevice = selectedDevice,
                        context = context
                    )
                    DpadControllerCard(viewModel = viewModel)
                    AccessoryCard(viewModel = viewModel)
                    TerminalCard(
                        viewModel = viewModel,
                        logs = logs,
                        modifier = Modifier.height(350.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequiredScreen(
    onGrantRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0xFFF3EDF7), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Bluetooth Offline",
                tint = Color(0xFFBA1A1A),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Bluetooth Permissions Required",
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = Color(0xFF1B1B1F),
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.SansSerif
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Modern Android releases (SDK 31+) require explicit granular permissions to scan and pair with Bluetooth SPP transceivers (HC-05/HC-06). Please tap the button below to grant permission.",
            color = Color(0xFF49454F),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGrantRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("grant_permissions_button")
        ) {
            Text(
                "GRANT PERMISSIONS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
fun ConnectionCard(
    viewModel: BluetoothViewModel,
    isBluetoothEnabled: Boolean,
    connectionStatus: ConnectionStatus,
    pairedDevices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    context: Context
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFE6E0E9)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusColor = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> Color(0xFF15803D)
                        ConnectionStatus.CONNECTING -> Color(0xFFB45309)
                        ConnectionStatus.DISCONNECTED -> Color(0xFFB91C1C)
                        ConnectionStatus.ERROR -> Color(0xFFB91C1C)
                    }

                    val statusText = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> "CONNECTED"
                        ConnectionStatus.CONNECTING -> "CONNECTING"
                        ConnectionStatus.DISCONNECTED -> "DISCONNECTED"
                        ConnectionStatus.ERROR -> "ERROR"
                    }

                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_alpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = statusColor.copy(alpha = if (connectionStatus == ConnectionStatus.CONNECTING) alpha else 1f),
                                shape = CircleShape
                            )
                    )

                    Text(
                        text = "STATUS: $statusText",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = { viewModel.refreshPairedDevices(context) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan Paired Devices",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color(0xFFE6E0E9)), RoundedCornerShape(12.dp))
                    .clickable { dropdownExpanded = true }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "DEVICE TRANSCEIVER",
                            fontSize = 10.sp,
                            color = Color(0xFF6750A4),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = selectedDevice?.let { "${it.name} (${it.address})" }
                                ?: "No Device Selected",
                            fontSize = 14.sp,
                            color = if (selectedDevice != null) Color(0xFF1B1B1F) else Color(0xFF79747E),
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Dropdown Open",
                        tint = Color(0xFF6750A4)
                    )
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(Color.White)
                ) {
                    if (pairedDevices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No bonded devices found. Pair in Android settings.", color = Color(0xFF79747E)) },
                            onClick = { dropdownExpanded = false }
                        )
                    } else {
                        pairedDevices.forEach { dev ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(dev.name, color = Color(0xFF1B1B1F), fontWeight = FontWeight.SemiBold)
                                        Text(dev.address, color = Color(0xFF49454F), fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                                    }
                                },
                                onClick = {
                                    viewModel.selectDevice(dev)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            val connectionBtnColor = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> Color(0xFFBA1A1A)
                else -> Color(0xFF6750A4)
            }

            val connectionBtnText = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> "DISCONNECT SESSION"
                ConnectionStatus.CONNECTING -> "ESTABLISHING..."
                else -> "CONNECT TO TARGET"
            }

            Button(
                onClick = {
                    if (connectionStatus == ConnectionStatus.CONNECTED) {
                        viewModel.disconnect()
                    } else {
                        viewModel.connect(context)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = connectionBtnColor,
                    disabledContainerColor = Color(0xFFE6E0E9)
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = selectedDevice != null && connectionStatus != ConnectionStatus.CONNECTING && isBluetoothEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("connect_action_button")
            ) {
                Text(
                    text = connectionBtnText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }

            if (!isBluetoothEnabled) {
                Text(
                    "⚠️ Bluetooth is disabled. Please turn it on in the system menu.",
                    color = Color(0xFFB45309),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DpadControllerCard(
    viewModel: BluetoothViewModel
) {
    var safetyMode by remember { mutableStateOf(true) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFE6E0E9)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "DRIVE MODE",
                        fontSize = 10.sp,
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = if (safetyMode) "Deadman Switch (Hold to Drive)" else "Momentary Click Mode",
                        fontSize = 13.sp,
                        color = Color(0xFF1B1B1F),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Switch(
                    checked = safetyMode,
                    onCheckedChange = { safetyMode = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF6750A4),
                        checkedTrackColor = Color(0xFFEADDFF),
                        uncheckedThumbColor = Color(0xFF79747E),
                        uncheckedTrackColor = Color(0xFFE6E0E9)
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .size(230.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF3EDF7)),
                            radius = 400f
                        )
                    )
                    .border(BorderStroke(4.dp, Color(0xFFE6E0E9)), CircleShape)
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.sendCommand("S", "Stop") },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFFDAD6), CircleShape)
                        .align(Alignment.Center)
                        .shadow(4.dp, CircleShape)
                        .testTag("dpad_stop_button")
                ) {
                    Text(
                        "STOP",
                        color = Color(0xFF410002),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                DpadDirectionButton(
                    direction = "FORWARD",
                    command = "F",
                    arrowLabel = "▲",
                    safetyMode = safetyMode,
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(54.dp)
                        .testTag("dpad_forward_button")
                )

                DpadDirectionButton(
                    direction = "BACKWARD",
                    command = "B",
                    arrowLabel = "▼",
                    safetyMode = safetyMode,
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(54.dp)
                        .testTag("dpad_backward_button")
                )

                DpadDirectionButton(
                    direction = "LEFT",
                    command = "L",
                    arrowLabel = "◀",
                    safetyMode = safetyMode,
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(54.dp)
                        .testTag("dpad_left_button")
                )

                DpadDirectionButton(
                    direction = "RIGHT",
                    command = "R",
                    arrowLabel = "▶",
                    safetyMode = safetyMode,
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(54.dp)
                        .testTag("dpad_right_button")
                )
            }

            if (safetyMode) {
                Text(
                    "💡 Deadman Switch Active: Releasing the button automatically transmits 'S' (Stop) to safeguard hardware.",
                    color = Color(0xFF6750A4),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun DpadDirectionButton(
    direction: String,
    command: String,
    arrowLabel: String,
    safetyMode: Boolean,
    viewModel: BluetoothViewModel,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (safetyMode) {
            if (isPressed) {
                viewModel.sendCommand(command, direction)
            } else {
                viewModel.sendCommand("S", "Deadman Stop")
            }
        }
    }

    val buttonColor = if (isPressed) Color(0xFFD0BCFF) else Color(0xFFE8DEF8)
    val labelColor = Color(0xFF21005D)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(buttonColor)
            .border(BorderStroke(1.dp, Color(0xFFCAC4D0)), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                if (!safetyMode) {
                    viewModel.sendCommand(command, direction)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = arrowLabel,
            color = labelColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
fun AccessoryCard(
    viewModel: BluetoothViewModel
) {
    var lightsOn by remember { mutableStateOf(false) }
    var speedState by remember { mutableFloatStateOf(150f) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFE6E0E9)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "ROBOT UTILITY & ACCESSORIES",
                fontSize = 11.sp,
                color = Color(0xFF6750A4),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        lightsOn = !lightsOn
                        if (lightsOn) {
                            viewModel.sendCommand("W", "Lights ON")
                        } else {
                            viewModel.sendCommand("w", "Lights OFF")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (lightsOn) Color(0xFFEADDFF) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (!lightsOn) BorderStroke(1.dp, Color(0xFFE6E0E9)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("lights_toggle_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Lights Toggle",
                            tint = if (lightsOn) Color(0xFF21005D) else Color(0xFF79747E)
                        )
                        Text(
                            text = if (lightsOn) "LIGHTS: ON" else "LIGHTS: OFF",
                            color = if (lightsOn) Color(0xFF21005D) else Color(0xFF1B1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                Button(
                    onClick = { viewModel.sendCommand("H", "Horn") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE6E0E9)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("horn_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Horn Buzzer",
                            tint = Color(0xFF6750A4)
                        )
                        Text(
                            text = "HONK HORN",
                            color = Color(0xFF1B1B1F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MOTOR PWM DRIVE VELOCITY",
                        fontSize = 11.sp,
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "V${speedState.toInt()}",
                        color = Color(0xFF21005D),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Slider(
                    value = speedState,
                    onValueChange = { speedState = it },
                    valueRange = 0f..255f,
                    steps = 254,
                    onValueChangeFinished = {
                        val finalSpeed = speedState.toInt()
                        viewModel.sendCommand("V$finalSpeed", "Velocity")
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6750A4),
                        activeTrackColor = Color(0xFF6750A4),
                        inactiveTrackColor = Color(0xFFE6E0E9)
                    ),
                    modifier = Modifier.testTag("speed_slider")
                )
            }
        }
    }
}

@Composable
fun TerminalCard(
    viewModel: BluetoothViewModel,
    logs: List<CommandLog>,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFE6E0E9)),
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Terminal Console",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "SERIAL CONTROLLER TERMINAL",
                        fontSize = 11.sp,
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Text(
                    "CLEAR",
                    color = Color(0xFFBA1A1A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier
                        .clickable { viewModel.clearLogs() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFFDFBFF), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color(0xFFE6E0E9)), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ CONSOLE EMPTY ]\nConnection logs and incoming serial packets (Rx) will appear here in real time.",
                            color = Color(0xFF79747E),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs, key = { it.id }) { log ->
                            val textColor = when {
                                log.isError -> Color(0xFFBA1A1A)
                                log.isOutgoing -> Color(0xFF6750A4)
                                log.message.startsWith("Rx:") -> Color(0xFFB45309)
                                else -> Color(0xFF49454F)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "[${log.timestamp}]",
                                    color = Color(0xFF79747E),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = log.message,
                                    color = textColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            "Type custom command...",
                            color = Color(0xFF79747E),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(
                        color = Color(0xFF1B1B1F),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFE6E0E9)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("custom_command_input")
                )

                Button(
                    onClick = {
                        val txt = textInput.text.trim()
                        if (txt.isNotEmpty()) {
                            viewModel.sendCommand(txt, "Custom String")
                            textInput = TextFieldValue("")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("send_custom_command_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Custom Command",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
