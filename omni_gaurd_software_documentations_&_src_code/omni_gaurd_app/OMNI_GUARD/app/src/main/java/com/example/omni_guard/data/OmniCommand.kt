package com.example.omni_guard.data

sealed class OmniCommand {
    data object Arm : OmniCommand()
    data object Disarm : OmniCommand()
    data object Silence : OmniCommand()
    data object Emergency : OmniCommand()
    data object PowerOff : OmniCommand()
    data object AutoScan : OmniCommand()
    data object StopScan : OmniCommand()
    data object Reset : OmniCommand()
    data object LightOn : OmniCommand()
    data object LightOff : OmniCommand()
    data object Ping : OmniCommand()
    data object Version : OmniCommand()
    data object Status : OmniCommand()
    data object Reconnect : OmniCommand()
    data object Unknown : OmniCommand()
}
