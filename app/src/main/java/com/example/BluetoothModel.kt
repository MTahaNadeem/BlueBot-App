package com.example

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class PairedDevice(
    val name: String,
    val address: String
)

data class CommandLog(
    val id: String,
    val message: String,
    val timestamp: String,
    val isError: Boolean = false,
    val isOutgoing: Boolean = false
)
