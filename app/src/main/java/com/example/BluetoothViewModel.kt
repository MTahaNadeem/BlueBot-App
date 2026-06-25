package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothViewModel : ViewModel() {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val TAG = "BlueBotVM"

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices = _pairedDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<PairedDevice?>(null)
    val selectedDevice = _selectedDevice.asStateFlow()

    private val _logs = MutableStateFlow<List<CommandLog>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled = _isBluetoothEnabled.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions = _hasPermissions.asStateFlow()

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var listenerJob: Job? = null

    init {
        addLog("Application started. Initialize Bluetooth configuration...", isError = false)
    }

    /**
     * Checks if all required runtime permissions are granted for Bluetooth Classic operations.
     */
    fun checkPermissions(context: Context): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        _hasPermissions.value = allGranted
        return allGranted
    }

    /**
     * Scans and loads paired/bonded Bluetooth Classic devices in the vicinity.
     */
    fun refreshPairedDevices(context: Context) {
        if (!checkPermissions(context)) {
            _hasPermissions.value = false
            addLog("Warning: Bluetooth permissions are missing. Please grant them in settings.", isError = true)
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            addLog("Error: Bluetooth hardware is not supported on this device.", isError = true)
            _isBluetoothEnabled.value = false
            return
        }

        _isBluetoothEnabled.value = adapter.isEnabled
        if (!adapter.isEnabled) {
            addLog("Bluetooth is currently disabled. Please turn on Bluetooth.", isError = true)
            _pairedDevices.value = emptyList()
            return
        }

        try {
            val bondedDevices = adapter.bondedDevices
            val deviceList = bondedDevices.map { device ->
                PairedDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address
                )
            }
            _pairedDevices.value = deviceList
            addLog("Loaded ${deviceList.size} paired devices from system storage.")
        } catch (e: SecurityException) {
            addLog("SecurityException: Failed to fetch bonded devices.", isError = true)
        }
    }

    /**
     * Selects a device from the list of paired devices.
     */
    fun selectDevice(device: PairedDevice) {
        _selectedDevice.value = device
        addLog("Target selected: ${device.name} (${device.address})")
    }

    /**
     * Connects to the selected Bluetooth device over SPP asynchronously on Dispatchers.IO.
     * Guarantees that the main/UI thread remains completely responsive, preventing ANRs.
     */
    fun connect(context: Context) {
        val target = _selectedDevice.value
        if (target == null) {
            addLog("Error: No target device selected for connection.", isError = true)
            return
        }

        if (!checkPermissions(context)) {
            addLog("Error: Cannot connect. Permissions are missing.", isError = true)
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            addLog("Error: Bluetooth is unavailable or disabled.", isError = true)
            return
        }

        // Cancel previous connection/jobs safely
        cleanupConnection()

        _connectionStatus.value = ConnectionStatus.CONNECTING
        addLog("Connecting to ${target.name} [${target.address}]...")

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val device = adapter.getRemoteDevice(target.address)
                    // Standard RFCOMM SPP socket creation
                    val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    bluetoothSocket = socket

                    // Connect blocking call inside Dispatchers.IO
                    socket.connect()
                    outputStream = socket.outputStream
                    true
                } catch (e: IOException) {
                    Log.e(TAG, "Socket connection failed", e)
                    try {
                        bluetoothSocket?.close()
                    } catch (closeException: IOException) {
                        Log.e(TAG, "Could not close the client socket", closeException)
                    }
                    bluetoothSocket = null
                    outputStream = null
                    false
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception connecting socket", e)
                    false
                }
            }

            if (result && bluetoothSocket != null) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                addLog("Successfully CONNECTED to ${target.name}!", isError = false)
                startIncomingStreamListener()
            } else {
                _connectionStatus.value = ConnectionStatus.ERROR
                addLog("Connection failed. Ensure device is powered on, paired, and SPP is supported (Error 507).", isError = true)
            }
        }
    }

    /**
     * Disconnects the current Bluetooth session safely.
     */
    fun disconnect() {
        cleanupConnection()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        addLog("Bluetooth disconnected.", isError = false)
    }

    /**
     * Starts listening to any incoming data (Rx) from the robot.
     */
    private fun startIncomingStreamListener() {
        val socket = bluetoothSocket ?: return
        listenerJob = viewModelScope.launch(Dispatchers.IO) {
            val inputStream = try {
                socket.inputStream
            } catch (e: IOException) {
                Log.e(TAG, "Failed to retrieve input stream", e)
                return@launch
            }

            val buffer = ByteArray(1024)
            addLog("Incoming data stream reader active.", isError = false)

            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val incomingData = String(buffer, 0, bytesRead).trim()
                        if (incomingData.isNotEmpty()) {
                            addLog("Rx: $incomingData", isError = false, isOutgoing = false)
                        }
                    } else if (bytesRead == -1) {
                        // EOF reached, socket closed from other end
                        withContext(Dispatchers.Main) {
                            addLog("Robot closed connection.", isError = true)
                            disconnect()
                        }
                        break
                    }
                } catch (e: IOException) {
                    if (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                        withContext(Dispatchers.Main) {
                            addLog("Connection severed: ${e.localizedMessage}", isError = true)
                            disconnect()
                        }
                    }
                    break
                }
            }
        }
    }

    /**
     * Sends a text command to the connected Bluetooth SPP device.
     * Automatically verifies active connection status before writing, avoiding Error 515.
     */
    fun sendCommand(command: String, label: String = "") {
        if (_connectionStatus.value != ConnectionStatus.CONNECTED) {
            addLog("Blocked sending '$command' - Bluetooth not connected! (Error 515)", isError = true)
            return
        }

        val outStream = outputStream
        if (outStream == null) {
            addLog("Error: Output stream is null. Re-establishing session.", isError = true)
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                outStream.write(command.toByteArray())
                outStream.flush()
                val displayMsg = if (label.isNotEmpty()) "Tx: '$command' ($label)" else "Tx: '$command'"
                addLog(displayMsg, isError = false, isOutgoing = true)
            } catch (e: IOException) {
                Log.e(TAG, "Write failed", e)
                withContext(Dispatchers.Main) {
                    addLog("Failed to send command: Connection error.", isError = true)
                    disconnect()
                }
            }
        }
    }

    /**
     * Cleans up all connections, sockets, and listener threads.
     */
    private fun cleanupConnection() {
        listenerJob?.cancel()
        listenerJob = null
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket during cleanup", e)
        }
        bluetoothSocket = null
        outputStream = null
    }

    /**
     * Clears local terminal activity log.
     */
    fun clearLogs() {
        _logs.value = emptyList()
        addLog("Terminal log cleared.")
    }

    /**
     * Logging utility that prepends timestamp for interactive terminal visualizer.
     */
    private fun addLog(message: String, isError: Boolean = false, isOutgoing: Boolean = false) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val newLog = CommandLog(
            id = UUID.randomUUID().toString(),
            message = message,
            timestamp = timestamp,
            isError = isError,
            isOutgoing = isOutgoing
        )
        // Insert at the top of the list for easy reverse layout scrolling
        _logs.value = listOf(newLog) + _logs.value
    }

    override fun onCleared() {
        super.onCleared()
        cleanupConnection()
    }
}
