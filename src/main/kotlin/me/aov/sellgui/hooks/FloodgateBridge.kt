package me.aov.sellgui.hooks

import org.geysermc.floodgate.api.FloodgateApi
import java.util.UUID

class FloodgateBridge : BedrockBridge {
    override fun isBedrockPlayer(playerId: UUID): Boolean = FloodgateApi.getInstance()?.isFloodgatePlayer(playerId) == true
    override fun sendConfirmation(playerId: UUID, title: String, content: String, sell: String, cancel: String, response: (Boolean) -> Unit): Boolean =
        FloodgateApi.getInstance()?.sendForm(playerId, BedrockForms.confirmation(title, content, sell, cancel, response)) == true
}
