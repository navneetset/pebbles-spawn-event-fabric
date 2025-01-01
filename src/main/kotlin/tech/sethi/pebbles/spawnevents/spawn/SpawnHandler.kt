package tech.sethi.pebbles.spawnevents.spawn

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.server
import kotlinx.coroutines.*
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import tech.sethi.pebbles.spawnevents.config.ConfigHandler
import tech.sethi.pebbles.spawnevents.config.eventhistory.EventHistoryConfig
import tech.sethi.pebbles.spawnevents.config.spawns.SpawnEventConfig
import tech.sethi.pebbles.spawnevents.util.PM
import tech.sethi.pebbles.spawnevents.util.PlaySound
import xyz.nucleoid.stimuli.Stimuli
import xyz.nucleoid.stimuli.event.entity.EntityDeathEvent
import java.util.*
import kotlin.random.Random

object SpawnHandler {

    val liveSpawns = mutableMapOf<EventHistoryConfig.HistoryEntry, Pair<SpawnEventConfig.SpawnEvent, PokemonEntity>>()
    val latestPlayers = listOf<UUID>()

    val queueRemove = mutableListOf<EventHistoryConfig.HistoryEntry>()

    var tick = 0
    var spawnInterval = ConfigHandler.config.spawnInterval

    init {
        if (ConfigHandler.config.enabled) {
            ServerTickEvents.END_SERVER_TICK.register {
                tick++
                if (tick % 1200 == 0) {
                    // only decrease interval if there are no live spawns
                    if (liveSpawns.isEmpty() || liveSpawns.values.all { it.second.isRemoved || it.second.isDead }) {
                        spawnInterval--
                    }

                    tick = 0


                    if (spawnInterval <= 0) {
                        spawnInterval = ConfigHandler.config.spawnInterval
                        spawn()
                    }
                }

                // keep chunks loaded
                liveSpawns.values.forEach { (_, pokemonEntity) ->
                    val historyEntry =
                        liveSpawns.keys.firstOrNull { it.pokemonUuid == pokemonEntity.pokemon.uuid.toString() }
                    // check if history wasn't saved yet
                    if (historyEntry?.type != null) {
                        // queue for removal
                        queueRemove.add(historyEntry)
                        return@forEach
                    }

                    if (pokemonEntity.isRemoved || pokemonEntity.isDead) {
                        // if despawned, save in history as despawned
                        if (pokemonEntity.removalReason == Entity.RemovalReason.DISCARDED && pokemonEntity.attacker !is PlayerEntity) {
                            historyEntry!!.type = EventHistoryConfig.HistoryType.DESPAWNED
                            historyEntry.time = System.currentTimeMillis()

                            EventHistoryConfig.addHistoryEntry(historyEntry)
                        }
                        return@forEach
                    }

                    pokemonEntity.world.chunkManager.getChunk(pokemonEntity.blockX, pokemonEntity.blockZ)
                }

                // remove all queued entries
                queueRemove.removeIf { entry ->
                    liveSpawns.remove(entry)
                    true
                }
            }

            handleAction()
        }

        Stimuli.global().listen(EntityDeathEvent.EVENT, PokemonDeathEvent)
    }

    fun handleAction() {
        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            CoroutineScope(Dispatchers.IO).launch {
                val losers = event.losers
                val loserPokemonList = losers.map { it.pokemonList }.flatten()
                val loserPokemon = loserPokemonList.map { it.originalPokemon }
                val loserPokemonUuids = loserPokemon.map { it.uuid }

                val winner = event.winners.firstOrNull()
                val winnerPlayers = winner?.getPlayerUUIDs()
                val winnerPlayer = server()!!.playerManager.getPlayer(winnerPlayers?.firstOrNull() ?: return@launch)

                // check if any of the losers are live spawns
                val liveSpawn = liveSpawns.values.firstOrNull { it.second.pokemon.uuid in loserPokemonUuids }

                if (event.wasWildCapture) {
                    if (liveSpawn != null) {
                        liveSpawn.first.parseCaptureBroadcast(winnerPlayer?.name?.string ?: "Unknown").also { message ->
                            server()!!.playerManager.playerList.forEach { player ->
                                player.sendMessage(PM.returnStyledText(message), false)
                            }
                        }

                        // save to history
                        val historyEntry =
                            liveSpawns.keys.firstOrNull { it.pokemonUuid == liveSpawn.second.pokemon.uuid.toString() }
                        historyEntry!!.playerActed = winnerPlayer?.name?.string ?: "Unknown"
                        historyEntry.type = EventHistoryConfig.HistoryType.CAPTURED
                        historyEntry.time = System.currentTimeMillis()
                        EventHistoryConfig.addHistoryEntry(historyEntry)

                        liveSpawns.clear()
                    }
                } else {
                    if (liveSpawn != null) {
                        val spawnEvent = liveSpawn.first
                        spawnEvent.parseKillBroadcast(winnerPlayer?.name?.string ?: "Unknown").also { message ->
                            server()!!.playerManager.playerList.forEach { player ->
                                player.sendMessage(PM.returnStyledText(message), false)
                            }
                        }

                        // save to history
                        val historyEntry =
                            liveSpawns.keys.firstOrNull { it.pokemonUuid == liveSpawn.second.pokemon.uuid.toString() }
                        historyEntry!!.playerActed = winnerPlayer?.name?.string ?: "Unknown"
                        historyEntry.type = EventHistoryConfig.HistoryType.KILLED
                        historyEntry.time = System.currentTimeMillis()
                        EventHistoryConfig.addHistoryEntry(historyEntry)

                        liveSpawns.clear()
                    }
                }
            }
        }

        CobblemonEvents.POKEMON_FAINTED.subscribe {
            CoroutineScope(Dispatchers.IO).launch {
                val pokemon = it.pokemon
                val uuid = pokemon.uuid
                val spawnEvent = liveSpawns.values.firstOrNull { it.second.uuid == uuid }?.first
                val historyEntry = liveSpawns.keys.firstOrNull { it.pokemonUuid == uuid.toString() }
                if (spawnEvent != null) {
                    val pokemonEntity = liveSpawns.values.first { it.second.uuid == uuid }.second
                    // nearest player
                    val killer = pokemonEntity.world.getClosestPlayer(
                        pokemonEntity.x, pokemonEntity.y, pokemonEntity.z, 32.0, false
                    )

                    spawnEvent.parseKillBroadcast(killer?.name?.string ?: "Unknown").also { message ->
                        server()!!.playerManager.playerList.forEach { player ->
                            player.sendMessage(PM.returnStyledText(message), false)
                        }
                    }

                    // save to history
                    historyEntry!!.playerActed = killer?.name?.string ?: "Unknown"
                    historyEntry.type = EventHistoryConfig.HistoryType.KILLED
                    historyEntry.time = System.currentTimeMillis()
                    EventHistoryConfig.addHistoryEntry(historyEntry)

                    liveSpawns.clear()
                }
            }
        }

        CobblemonEvents.POKEMON_CAPTURED.subscribe {
            val pokemon = it.pokemon
            val uuid = pokemon.uuid
            val spawnEvent = liveSpawns.values.firstOrNull { it.second.pokemon.uuid == uuid }?.first
            val historyEntry = liveSpawns.keys.firstOrNull { it.pokemonUuid == uuid.toString() }

            if (spawnEvent != null) {
                val catcher = it.player
                spawnEvent.parseCaptureBroadcast(catcher.name.string).also { message ->
                    server()!!.playerManager.playerList.forEach { player ->
                        player.sendMessage(PM.returnStyledText(message), false)
                    }
                }

                // save to history
                historyEntry!!.playerActed = catcher.name.string
                historyEntry.type = EventHistoryConfig.HistoryType.CAPTURED
                historyEntry.time = System.currentTimeMillis()
                EventHistoryConfig.addHistoryEntry(historyEntry)

                liveSpawns.clear()
            }
        }
    }


    fun spawn() {
        val spawnEvents = SpawnEventConfig.spawnEvents
        val totalWeight = spawnEvents.sumOf { it.spawnWeight }

        val playerCount = server()!!.playerManager.playerList.size
        if (playerCount < ConfigHandler.config.minPlayers) {
            return
        }

        val random = Math.random() * totalWeight
        var currentWeight = 0

        var selectedSpawnEvent: SpawnEventConfig.SpawnEvent? = null
        // shuffle the spawn events so that the same event doesn't always get selected, make sure at least one event is selected
        spawnEvents.shuffled().forEach { spawnEvent ->
            currentWeight += spawnEvent.spawnWeight
            if (random <= currentWeight && selectedSpawnEvent == null) {
                selectedSpawnEvent = spawnEvent
            }
        }

        // select a random player to spawn near, but lower the chance of selecting the same player twice in a row
        val players = server()!!.playerManager.playerList.filter { player ->
            player.uuid !in latestPlayers
        }.shuffled()

        // if null, select a random player
        val player = players.firstOrNull() ?: server()!!.playerManager.playerList.random()

        // if list is bigger than 2, remove the oldest player
        if (latestPlayers.size >= 2) {
            latestPlayers.drop(1)
        }

        latestPlayers.plus(player.uuid)

        val world = player.world.registryKey.value.toString()
        val biome =
            PM.getLocaleText(player.world.getBiome(player.blockPos).key.get().value.toTranslationKey()).split(".")
                .last().replace("_", " ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val playerName = player.name.string

        val pokemon = selectedSpawnEvent!!.toPokemon()
        val spawnPos = player.blockPos.up(
            player.world.getTopPosition(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, player.blockPos
            ).y - 1
        )

        var x = spawnPos.x + getRandomDistance()
        var z = spawnPos.z + getRandomDistance()
        var y = player.world.getTopPosition(
            Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos(x.toInt(), 0, z.toInt())
        ).y.toDouble()

        // make sure it's not water, attempt 30 times
        var attempts = 0

        // Make sure blocks below are solid and not water
        while (attempts < 30) {
            val pos = BlockPos(x.toInt(), y.toInt() - 1, z.toInt())

            // Check if the block is not solid or is water or the block above isn't air or replaceable
            if (isBlockSolid(pos, player.serverWorld).not() || isBlockSolid(
                    pos.down(), player.serverWorld
                ).not() || isBlockSolid(pos.down(2), player.serverWorld).not() || !isBlockAirOrReplaceable(
                    pos.up(), player.serverWorld
                )
            ) {
                // Update spawn position
                x = spawnPos.x + getRandomDistance()
                z = spawnPos.z + getRandomDistance()
                y = player.world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos(x.toInt(), 0, z.toInt())
                ).y.toDouble()
            } else {
                // Found a valid position
                break
            }
            attempts++
        }

        val pokemonEntity = PokemonEntity(player.world, pokemon)
        pokemonEntity.updatePosition(x, y + 1, z)

        // spawn the pokemon
        // force load chunk
        player.world.chunkManager.getChunk(pokemonEntity.blockX, pokemonEntity.blockZ)
        server()!!.execute {
            pokemonEntity.setPersistent()
            player.world.spawnEntity(pokemonEntity)
        }

        PM.returnStyledText(selectedSpawnEvent!!.parseSpawnBroadcast(world, biome, playerName)).also { message ->
            server()!!.playerManager.playerList.forEach { player ->
                player.sendMessage(message, false)
                if (selectedSpawnEvent!!.sound != null) {
                    PlaySound.playSound(
                        Identifier.of(selectedSpawnEvent!!.sound),
                        SoundCategory.AMBIENT,
                        1.0f,
                        1.0f,
                        player.blockPos,
                        player.world
                    )
                }
            }
        }

        selectedSpawnEvent!!.toHistoryEvent(
            pokemon.uuid, System.currentTimeMillis(), null, world, biome, playerName
        ).also { historyEntry ->
            liveSpawns[historyEntry] = Pair(selectedSpawnEvent!!, pokemonEntity)
        }
    }

    fun getRandomDistance(): Double {
        val minDistance = 20.0
        val maxDistance = 50.0
        return if (Random.nextBoolean()) {
            // Randomly choose positive distance
            Random.nextDouble(minDistance, maxDistance)
        } else {
            // Randomly choose negative distance
            -Random.nextDouble(minDistance, maxDistance)
        }
    }

    fun isBlockSolid(blockPos: BlockPos, world: ServerWorld): Boolean {
        val liquidBlocks = listOf(Blocks.WATER, Blocks.LAVA, Blocks.BUBBLE_COLUMN, Blocks.CAULDRON, Blocks.POWDER_SNOW)
        val blockState = world.getBlockState(blockPos)
        return blockState.isSolidBlock(world, blockPos) && !liquidBlocks.contains(blockState.block)
    }

    fun isBlockAirOrReplaceable(blockPos: BlockPos, world: ServerWorld): Boolean {
        val blockState = world.getBlockState(blockPos)
        return blockState.isAir || blockState.isReplaceable
    }

}