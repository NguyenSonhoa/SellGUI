package me.aov.sellgui.hooks

import java.util.UUID

/** Keeps optional Geyser, Floodgate and Cumulus classes out of the core plugin's signatures. */
interface BedrockBridge {
    fun isBedrockPlayer(playerId: UUID): Boolean
    fun sendConfirmation(playerId: UUID, title: String, content: String, sell: String, cancel: String, response: (Boolean) -> Unit): Boolean
}
