package com.example.omni_guard.ai

import com.example.omni_guard.data.OmniCommand
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceCommandInterpreter @Inject constructor() {

    fun interpret(text: String): OmniCommand {
        val input = text.uppercase()
        
        return when {
            input.contains("ARM") && !input.contains("DISARM") -> OmniCommand.Arm
            input.contains("DISARM") || input.contains("DEACTIVATE") || input.contains("UNARM") -> OmniCommand.Disarm
            input.contains("EMERGENCY") || input.contains("PANIC") || input.contains("ALERT") -> OmniCommand.Emergency
            input.contains("SILENCE") || input.contains("QUIET") || input.contains("STOP AUDIO") || input.contains("MUTE") -> OmniCommand.Silence
            input.contains("POWER OFF") || input.contains("SHUT DOWN") || input.contains("TURN OFF") -> OmniCommand.PowerOff
            input.contains("POWER ON") || input.contains("TURN ON") || input.contains("ACTIVATE") || input.contains("START UP") -> OmniCommand.PowerOff // Power command toggles usually, but here interpreted as Power
            input.contains("START SCAN") || input.contains("AUTO SCAN") || input.contains("SCAN ON") || input.contains("ENABLE AUTO SCAN") -> OmniCommand.AutoScan
            input.contains("STOP SCAN") || input.contains("SCAN OFF") || input.contains("DISABLE AUTO SCAN") -> OmniCommand.StopScan
            input.contains("RESET") || input.contains("REBOOT") || input.contains("RESTART") -> OmniCommand.Reset
            input.contains("LIGHT ON") || input.contains("ILLUMINATE") -> OmniCommand.LightOn
            input.contains("LIGHT OFF") || input.contains("DARK") -> OmniCommand.LightOff
            input.contains("PING") || input.contains("TEST CONNECTION") -> OmniCommand.Ping
            input.contains("VERSION") || input.contains("FIRMWARE") -> OmniCommand.Version
            input.contains("DIAGNOSTICS") || input.contains("HEALTH") || input.contains("STATUS") || input.contains("BRIEF") -> OmniCommand.Status
            input.contains("RECONNECT") || input.contains("BLUETOOTH") -> OmniCommand.Reconnect
            else -> OmniCommand.Unknown
        }
    }
}
