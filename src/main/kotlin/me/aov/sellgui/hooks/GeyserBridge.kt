package me.aov.sellgui.hooks

import org.geysermc.geyser.api.GeyserApi
import java.util.UUID

class GeyserBridge : BedrockBridge {
    override fun isBedrockPlayer(playerId: UUID): Boolean = GeyserApi.api()?.isBedrockPlayer(playerId) == true
    override fun sendConfirmation(playerId: UUID, title: String, content: String, sell: String, cancel: String, response: (Boolean) -> Unit): Boolean =
        GeyserApi.api()?.sendForm(playerId, BedrockForms.confirmation(title, content, sell, cancel, response)) == true
}
