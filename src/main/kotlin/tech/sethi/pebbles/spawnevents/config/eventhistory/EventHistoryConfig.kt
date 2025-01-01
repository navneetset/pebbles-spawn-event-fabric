package tech.sethi.pebbles.spawnevents.config.eventhistory

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.item.PokemonItem
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import tech.sethi.pebbles.spawnevents.util.ConfigFileHandler
import tech.sethi.pebbles.spawnevents.SpawnEvents
import tech.sethi.pebbles.spawnevents.config.ConfigHandler
import tech.sethi.pebbles.spawnevents.spawn.SpawnHandler
import tech.sethi.pebbles.spawnevents.util.PM
import tech.sethi.pebbles.spawnevents.util.SerializedItemStack
import java.io.File
import java.util.*

object EventHistoryConfig {
    val gson = ConfigHandler.gson

    val configFile = File(SpawnEvents.configDir, "event-history/event-history-config.json")
    val screenConfigFile = File(SpawnEvents.configDir, "event-history/event-history-screen.json")
    val historyFile = File(SpawnEvents.configDir, "event-history/history.json")

    val configFileHandler = ConfigFileHandler(EventHistory::class.java, configFile, gson)
    val screenConfigFileHandler = ConfigFileHandler(EventHistoryScreen::class.java, screenConfigFile, gson)
    val historyFileHandler = ConfigFileHandler(History::class.java, historyFile, gson)

    var config = EventHistory()
    var screenConfig = EventHistoryScreen()
    var history = History()

    init {
        reload()
    }

    fun reload() {
        configFileHandler.reload()
        screenConfigFileHandler.reload()
        historyFileHandler.reload()

        config = configFileHandler.config
        screenConfig = screenConfigFileHandler.config
        history = historyFileHandler.config
    }

    fun addHistoryEntry(entry: HistoryEntry) {
        history.history.add(entry)
        historyFileHandler.save()
    }

    fun reloadHistory() {
        historyFileHandler.reload()
        history = historyFileHandler.config
    }

    fun getTimePassed(time: Long): String {
        val timePassed = System.currentTimeMillis() - time
        val seconds = timePassed / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days days"
            hours > 0 -> "$hours hours"
            minutes > 0 -> "$minutes minutes"
            else -> "$seconds seconds"
        }
    }


    data class EventHistory(
        val enabled: Boolean = true,
        val capturedStatus: String = "<green>Captured",
        val killedStatus: String = "<red>Killed",
        val despawnedStatus: String = "<yellow>Despawned",
        val unknownStatus: String = "<gray>Unknown",
    )

    data class EventHistoryScreen(
        val title: String = "<blue>Legendary Spawn History",
        val liveSpawnSlot: Int = 4,
        val liveSpawnLores: List<String> = listOf(
            "{pokemon}",
            "<gray>Spawned near <aqua>{player_name.spawned}</aqua> in {dimension} <red>{time_passed}</red> ago",
            "<gray>Biome: <yellow>{biome}"
        ),
        val historySlots: List<Int> = listOf(18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35),
        val historyLores: List<String> = listOf(
            "{pokemon}",
            "<gray>Spawned near <aqua>{player_name.spawned}</aqua> in {dimension} <red>{time_passed}</red> ago",
            "<gray>Biome: <yellow>{biome}",
            "Status: {status} by {player_name.acted}"
        ),
        val navForwardSlots: List<Int> = listOf(37),
        val navForwardStack: SerializedItemStack = SerializedItemStack(
            displayName = "<aqua>Next Page",
            material = "minecraft:arrow",
            amount = 1,
            nbt = "",
            lore = listOf("<yellow>Click to view next page")
        ),
        val navBackSlots: List<Int> = listOf(36),
        val navBackStack: SerializedItemStack = SerializedItemStack(
            displayName = "<aqua>Previous Page",
            material = "minecraft:arrow",
            amount = 1,
            nbt = "",
            lore = listOf("<yellow>Click to view previous page")
        ),
        val emptySlotStack: SerializedItemStack = SerializedItemStack(
            displayName = "<gray>",
            material = "minecraft:gray_stained_glass_pane",
            amount = 1,
            nbt = "",
            lore = listOf("")
        ),
    ) {
        fun liveSpawnItemStack(): ItemStack {
            val itemStack = SpawnHandler.liveSpawns.keys.firstOrNull()?.toItemStack() ?: return ItemStack.EMPTY

            val lore = mutableListOf<String>()
            val dimension = SpawnHandler.liveSpawns.keys.firstOrNull()?.dimension?.split(":")?.lastOrNull()
                ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                ?: "Unknown"
            liveSpawnLores.forEach { loreLine ->
                lore.add(
                    loreLine.replace(
                        "{pokemon}",
                        SpawnHandler.liveSpawns.values.firstOrNull()?.second?.pokemon?.species?.translatedName?.string
                            ?: "Unknown"
                    ).replace(
                        "{player_name.spawned}", SpawnHandler.liveSpawns.keys.firstOrNull()?.playerSpawned ?: "Unknown"
                    ).replace("{dimension}", dimension)
                        .replace("{biome}", SpawnHandler.liveSpawns.keys.firstOrNull()?.biome ?: "Unknown")
                        .replace("{time_passed}", getTimePassed(SpawnHandler.liveSpawns.keys.firstOrNull()?.time ?: 0))
                )
            }

            PM.setLore(itemStack, lore)

            return itemStack
        }
    }

    data class History(
        val history: MutableList<HistoryEntry> = mutableListOf()
    )

    enum class HistoryType {
        CAPTURED, KILLED, DESPAWNED, UNKNOWN
    }

    data class HistoryEntry(
        val pokemonUuid: String,
        var time: Long,
        var type: HistoryType? = null,
        val playerSpawned: String? = null,
        var playerActed: String? = null,
        val pokemonProperties: String,
        val dimension: String,
        val biome: String,
    ) {
        fun toItemStack(): ItemStack {
            val pokemon = PokemonProperties.parse(pokemonProperties).create()
            val itemStack = PokemonItem.from(pokemon)
            val pokemonName = pokemon.species.translatedName.string.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }
            val typeDisplay = type ?: HistoryType.UNKNOWN
            val displayName = "<blue>[$typeDisplay] $pokemonName"

            val lore = mutableListOf<String>()
            val dimensionName = dimension.split(":").lastOrNull()?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            } ?: "Unknown"
            screenConfig.historyLores.forEach { loreLine ->
                lore.add(
                    loreLine.replace("{pokemon}", pokemonName)
                        .replace("{player_name.spawned}", playerSpawned ?: "Unknown")
                        .replace("{player_name.acted}", playerActed ?: "Unknown").replace("{dimension}", dimensionName)
                        .replace("{biome}", biome).replace("{status}", type?.name ?: "Unknown")
                        .replace("{time_passed}", getTimePassed(time))
                )
            }

            itemStack.set(DataComponentTypes.CUSTOM_NAME, PM.returnStyledText(displayName))
            PM.setLore(itemStack, lore)

            return itemStack
        }
    }
}