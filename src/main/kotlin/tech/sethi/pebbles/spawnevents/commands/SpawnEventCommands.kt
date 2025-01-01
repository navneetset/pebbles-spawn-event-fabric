package tech.sethi.pebbles.spawnevents.commands

import com.mojang.brigadier.CommandDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import tech.sethi.pebbles.spawnevents.SpawnEvents
import tech.sethi.pebbles.spawnevents.config.ConfigHandler
import tech.sethi.pebbles.spawnevents.config.spawns.SpawnEventConfig
import tech.sethi.pebbles.spawnevents.screenhandler.EventMenu
import tech.sethi.pebbles.spawnevents.util.PM

object SpawnEventCommands {

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val spawnEventCommand = literal("spawnevent").executes { ctx ->
            val player = ctx.source.player ?: return@executes 0

            EventMenu.open(player)

            1
        }

        val reloadCommand = literal("reload").requires { it.hasPermissionLevel(2) }.executes { ctx ->
            ConfigHandler.reload()
            SpawnEvents.LOGGER.info("Loaded ${SpawnEventConfig.spawnEvents.size} spawn events")

            SpawnEvents.LOGGER.info("Events: ${SpawnEventConfig.spawnEvents.joinToString { it.pokemonProperties }}")

            ctx.source.sendFeedback({ PM.returnStyledText("Reloaded spawn events") }, false)
            ctx.source.sendFeedback({ PM.returnStyledText("Loaded ${SpawnEventConfig.spawnEvents.size} spawn events") }, false)
            ctx.source.sendFeedback({ PM.returnStyledText("Events: ${SpawnEventConfig.spawnEvents.joinToString { it.pokemonProperties }}") }, false)
            1
        }

        spawnEventCommand.then(reloadCommand)

        dispatcher.register(spawnEventCommand)
    }
}