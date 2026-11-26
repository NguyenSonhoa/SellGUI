package me.aov.sellgui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class OptionalBedrockDependencyTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `Java enable and sale work with no Geyser Floodgate or Cumulus classes`() {
        val classpath = System.getProperty("java.class.path").split(File.pathSeparator)
            .filterNot { it.replace('\\', '/').contains("/org/geysermc/") || it.replace('\\', '/').contains("/org/cloudburstmc/") }
            .joinToString(File.pathSeparator)
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val output = tempDir.resolve("java-only.log")
        val process = ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp", classpath, OptionalBedrockDependencyTest::class.java.name)
            .redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Java-only smoke test timed out")
            assertEquals(0, process.exitValue(), String(Files.readAllBytes(output), Charsets.UTF_8))
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            for (api in listOf("org.geysermc.floodgate.api.FloodgateApi", "org.geysermc.geyser.api.GeyserApi", "org.geysermc.cumulus.form.ModalForm")) {
                assertThrows(ClassNotFoundException::class.java) { Class.forName(api) }
            }
            val startup = SellGUIPluginTest()
            try {
                startup.startServer()
                startup.`plugin enables with its real descriptor and disables cleanly`()
            } finally {
                startup.stopServer()
            }
            val sale = BedrockSupportTest()
            try {
                sale.startServer()
                sale.`collect to cursor triggers sell controls but cannot collect ordinary items`()
            } finally {
                sale.stopServer()
            }
        }
    }
}
