package me.aov.sellgui

import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Material
import org.bukkit.plugin.ServicePriority
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.util.UnsafeValuesMock
import java.lang.reflect.Proxy

abstract class SellGUITestFixture {
    protected lateinit var server: ServerMock
    protected lateinit var plugin: SellGUIMain
    protected val deposits = arrayListOf<Double>()
    protected var rejectDeposits = false

    @BeforeEach
    fun startServer() {
        server = MockBukkit.mock(object : ServerMock() {
            private val unsafeValues = object : UnsafeValuesMock() {
                // Legacy alias conversion is not implemented by this MockBukkit release.
                // These tests exercise modern items, not legacy material migration.
                override fun fromLegacy(material: Material): Material =
                    Material.getMaterial(material.name.removePrefix("LEGACY_")) ?: Material.AIR
                override fun fromLegacy(material: org.bukkit.material.MaterialData, itemPriority: Boolean): Material =
                    fromLegacy(material.itemType)
            }
            override fun getUnsafe(): UnsafeValuesMock = unsafeValues
        })
        val vault = MockBukkit.createMockPlugin("Vault")
        MockBukkit.createMockPlugin("NBTAPI")
        val economy = Proxy.newProxyInstance(Economy::class.java.classLoader, arrayOf(Economy::class.java)) { _, method, args ->
            if (method.name == "depositPlayer") {
                val amount = args[1] as Double
                if (rejectDeposits) EconomyResponse(amount, deposits.sum(), EconomyResponse.ResponseType.FAILURE, "Rejected by test provider")
                else {
                    deposits += amount
                    EconomyResponse(amount, deposits.sum(), EconomyResponse.ResponseType.SUCCESS, null)
                }
            } else when (method.returnType) {
                Boolean::class.javaPrimitiveType -> true
                Int::class.javaPrimitiveType -> 2
                Double::class.javaPrimitiveType -> deposits.sum()
                String::class.java -> "TestEconomy"
                else -> null
            }
        } as Economy
        server.servicesManager.register(Economy::class.java, economy, vault, ServicePriority.Normal)
        plugin = MockBukkit.load(SellGUIMain::class.java)
    }

    @AfterEach
    fun stopServer() {
        MockBukkit.unmock()
        SellGUIMain.instance = null
        SellGUIMain.econ = null
    }
}
