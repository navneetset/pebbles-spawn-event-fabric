package tech.sethi.pebbles.spawnevents.config.spawns

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import tech.sethi.pebbles.spawnevents.SpawnEvents
import tech.sethi.pebbles.spawnevents.config.ConfigHandler
import tech.sethi.pebbles.spawnevents.config.eventhistory.EventHistoryConfig.HistoryEntry
import tech.sethi.pebbles.spawnevents.config.eventhistory.EventHistoryConfig.HistoryType
import java.io.File
import java.util.*

object SpawnEventConfig {

    val zapdosMessage = """
    <blue>The mythical entity, <yellow>Zapdos</yellow>, has been spotted in <light_purple>{biome}!
    
    <blue>Closest player: <aqua>{player_name.spawned}</aqua>
""".trimIndent()

    val gson = ConfigHandler.gson

    val spawnEventFolder = File(SpawnEvents.configDir, "spawns")

    var spawnEvents = listOf<SpawnEvent>()


    init {
        ServerLifecycleEvents.SERVER_STARTED.register {
            SpawnEvents.LOGGER.info("Loaded ${spawnEvents.size} spawn events")

            SpawnEvents.LOGGER.info("Events: ${spawnEvents.joinToString { it.pokemonProperties }}")
        }
    }

    fun reload() {

        // create example spawn event if none exist
        if (spawnEventFolder.listFiles()?.isEmpty() == true) {
            val exampleSpawnEvent = SpawnEvent()
            File(spawnEventFolder, "zapdos-example.json").writeText(gson.toJson(exampleSpawnEvent))
        }

        spawnEventFolder.mkdirs()

        spawnEvents = spawnEventFolder.listFiles()?.map { file ->
            gson.fromJson(file.readText(), SpawnEvent::class.java)
        } ?: listOf()
    }

    data class SpawnEvent(
        val pokemonProperties: String = "zapdos lvl=50 special_attack_iv=31",
        val broadcast: Broadcast = Broadcast(),
        val spawnWeight: Int = 1,
        val sound: String? = "minecraft:entity.lightning_bolt.thunder",
    ) {
        fun toPokemon(): Pokemon {
            return PokemonProperties.parse(pokemonProperties).create()
        }

        fun toHistoryEvent(
            pokemonUuid: UUID,
            time: Long,
            historyType: HistoryType?,
            dimension: String,
            biome: String,
            playerSpawned: String
        ): HistoryEntry {
            return HistoryEntry(
                pokemonUuid = pokemonUuid.toString(),
                time = time,
                type = historyType,
                playerSpawned = playerSpawned,
                playerActed = null,
                pokemonProperties = pokemonProperties,
                dimension = dimension,
                biome = biome
            )
        }

        fun parseSpawnBroadcast(dimension: String, biome: String, playerSpawned: String): String {
            val pokemon = toPokemon()
            return broadcast.spawn.replace("{pokemon}", pokemon.species.translatedName.string).replace("{biome}", biome)
                .replace("{dimension}", dimension).replace("{player_name.spawned}", playerSpawned)
        }

        fun parseDespawnBroadcast(dimension: String, biome: String): String {
            val pokemon = toPokemon()
            return broadcast.despawn.replace("{pokemon}", pokemon.species.translatedName.string)
                .replace("{biome}", biome).replace("{dimension}", dimension)
        }

        fun parseCaptureBroadcast(playerName: String): String {
            val pokemon = toPokemon()
            return broadcast.capture.replace("{pokemon}", pokemon.species.translatedName.string)
                .replace("{player_name.acted}", playerName)
        }

        fun parseKillBroadcast(playerName: String): String {
            val pokemon = toPokemon()
            return broadcast.kill.replace("{pokemon}", pokemon.species.translatedName.string)
                .replace("{player_name.acted}", playerName)
        }
    }

    data class Broadcast(
        val spawn: String = zapdosMessage,
        val despawn: String = "<blue>The mythical entity, <yellow>Zapdos</yellow>, has despawned.</blue>",
        val capture: String = "<blue>The mythical entity, <yellow>Zapdos</yellow>, has been captured by <aqua>{player_name.acted}</aqua>!</blue>",
        val kill: String = "<blue>The mythical entity, <yellow>Zapdos</yellow>, has been killed by <aqua>{player_name.acted}</aqua>!</blue>"
    )
}