package tech.sethi.pebbles.spawnevents.spawn

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.server
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResult
import tech.sethi.pebbles.spawnevents.config.eventhistory.EventHistoryConfig
import tech.sethi.pebbles.spawnevents.spawn.SpawnHandler.liveSpawns
import tech.sethi.pebbles.spawnevents.util.PM
import xyz.nucleoid.stimuli.event.entity.EntityDeathEvent
import java.util.*

object PokemonDeathEvent: EntityDeathEvent {
    override fun onDeath(entity: LivingEntity?, dmgSrc: DamageSource?): ActionResult {
        if (entity is PokemonEntity) {
            if (entity.attacker is PlayerEntity) {
                val killer = entity.attacker as? PlayerEntity ?: return ActionResult.PASS
                val uuid = entity.pokemon.uuid
                val spawnEvent = liveSpawns.values.firstOrNull { it.second.pokemon.uuid == uuid }?.first
                val historyEntry = liveSpawns.keys.firstOrNull { it.pokemonUuid == uuid.toString() }

                if (spawnEvent != null) {
                    spawnEvent.parseKillBroadcast(killer.name?.string ?: "Unknown").also { message ->
                        server()!!.playerManager.playerList.forEach { player ->
                            player.sendMessage(PM.returnStyledText(message), false)
                        }
                    }

                    // save to history
                    historyEntry!!.playerActed = killer.name?.string ?: "Unknown"
                    historyEntry.type = EventHistoryConfig.HistoryType.KILLED
                    historyEntry.time = System.currentTimeMillis()
                    EventHistoryConfig.addHistoryEntry(historyEntry)

                    liveSpawns.clear()
                }
            } else {
                val uuid = entity.pokemon.uuid
                val spawnEvent = liveSpawns.values.firstOrNull { it.second.pokemon.uuid == uuid }?.first
                val historyEntry = liveSpawns.keys.firstOrNull { it.pokemonUuid == uuid.toString() }

                if (spawnEvent != null) {
                    spawnEvent.parseDespawnBroadcast(entity.world.registryKey.value.toString(),
                        PM.getLocaleText(entity.world.getBiome(entity.blockPos).key.get().value.toTranslationKey())
                            .split(".").last().replace("_", " ")
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
                        .also { message ->
                            server()!!.playerManager.playerList.forEach { player ->
                                player.sendMessage(PM.returnStyledText(message), false)
                            }
                        }

                    // save to history
                    historyEntry!!.type = EventHistoryConfig.HistoryType.DESPAWNED
                    historyEntry.time = System.currentTimeMillis()
                    EventHistoryConfig.addHistoryEntry(historyEntry)

                    liveSpawns.clear()
                }
            }
        }

        return ActionResult.PASS
    }
}