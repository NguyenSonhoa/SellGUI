package me.aov.sellgui.hooks

import org.geysermc.api.Geyser
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.impl.FormDefinitions
import org.geysermc.cumulus.form.impl.modal.ModalFormDefinition
import org.geysermc.floodgate.api.FloodgateApi
import org.geysermc.floodgate.api.InstanceHolder
import org.geysermc.geyser.api.GeyserApi
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.reflect.Proxy
import java.util.UUID

class BedrockBridgeTest {
    @ParameterizedTest
    @ValueSource(strings = ["floodgate", "geyser"])
    fun `API bridges detect UUID and deliver functional native Sell Cancel and close responses`(provider: String) {
        val playerId = UUID.randomUUID()
        val forms = arrayListOf<ModalForm>()
        val results = arrayListOf<Boolean>()
        val apiClass = if (provider == "floodgate") FloodgateApi::class.java else GeyserApi::class.java
        val api = Proxy.newProxyInstance(apiClass.classLoader, arrayOf(apiClass)) { _, method, args ->
            when (method.name) {
                "isFloodgatePlayer", "isBedrockPlayer" -> args!![0] == playerId
                "sendForm" -> {
                    assertEquals(playerId, args!![0])
                    forms += args[1] as ModalForm
                    true
                }
                else -> null
            }
        }
        // Restore the API holder after each test; no real Geyser/Floodgate server is contacted.
        val holder = if (provider == "floodgate") InstanceHolder::class.java else Geyser::class.java
        val apiField = holder.getDeclaredField("api").also { it.isAccessible = true }
        val previous = apiField.get(null)
        apiField.set(null, api)
        try {
            val bridge: BedrockBridge = if (provider == "floodgate") FloodgateBridge() else GeyserBridge()
            assertTrue(bridge.isBedrockPlayer(playerId))
            assertFalse(bridge.isBedrockPlayer(UUID.randomUUID()))
            for (rawResponse in listOf("true", "false", "null", "invalid")) {
                assertTrue(bridge.sendConfirmation(playerId, "Confirm sale", "Sell for $20.00?", "Sell", "Cancel", results::add))
                val form = forms.last()
                assertEquals("Confirm sale", form.title())
                assertEquals("Sell", form.button1())
                assertEquals("Cancel", form.button2())
                val definition: ModalFormDefinition = FormDefinitions.instance().definitionFor(form)
                definition.handleFormResponse(form, rawResponse)
            }
            assertEquals(listOf(true, false, false, false), results)
        } finally {
            apiField.set(null, previous)
        }
    }
}
