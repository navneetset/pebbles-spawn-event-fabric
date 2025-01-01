package tech.sethi.pebbles.spawnevents.config

import com.google.gson.GsonBuilder
import tech.sethi.pebbles.spawnevents.util.ConfigFileHandler
import tech.sethi.pebbles.spawnevents.SpawnEvents
import tech.sethi.pebbles.spawnevents.config.eventhistory.EventHistoryConfig
import tech.sethi.pebbles.spawnevents.config.spawns.SpawnEventConfig
import java.io.File

object ConfigHandler {

    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    val configFile = File(SpawnEvents.configDir, "config.json")
    val configFileHandler = ConfigFileHandler(Config::class.java, configFile, gson)

    var config = Config()

    init {
        reload()
    }

    fun reload() {
        if (config.enabled) {
            configFileHandler.reload()
            config = configFileHandler.config

            SpawnEventConfig.reload()
            EventHistoryConfig.reload()
        }
    }


    data class Config(
        val enabled: Boolean = true,
        val minPlayers: Int = 1,
        val spawnInterval: Int = 30,
    )
}