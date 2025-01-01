package tech.sethi.pebbles.spawnevents

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import tech.sethi.pebbles.spawnevents.commands.SpawnEventCommands
import tech.sethi.pebbles.spawnevents.config.ConfigHandler
import tech.sethi.pebbles.spawnevents.spawn.SpawnHandler

object SpawnEvents : ModInitializer {
    val LOGGER = LoggerFactory.getLogger("pebbles-spawn-event")
    var server: MinecraftServer? = null

    val configDir = "config/pebbles-spawnevents"
    override fun onInitialize() {
        LOGGER.info("Pebble's Spawn Event Initialized!")

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            SpawnEventCommands.register(dispatcher)
        }
        ServerLifecycleEvents.SERVER_STARTING.register {
            server = it
        }

        ServerLifecycleEvents.SERVER_STARTED.register {
            SpawnHandler
            ConfigHandler
        }
    }
}