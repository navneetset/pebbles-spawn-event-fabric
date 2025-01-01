package tech.sethi.pebbles.spawnevents.util

import com.cobblemon.mod.common.api.net.NetworkPacket
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class PlaySound(sound: Identifier, category: SoundCategory, volume: Float, pitch: Float, pos: BlockPos, world: World, radius: Double = 16.0) {

    private val className = "com.cobblemon.mod.common.net.messages.client.sound.UnvalidatedPlaySoundS2CPacket"
    private val kClass: KClass<*> = Class.forName(className).kotlin

    private val constructor = kClass.primaryConstructor

    private val instance = constructor?.call(
        sound,
        category,
        pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
        volume, pitch
    ) as NetworkPacket<*>

    companion object {
        fun playSound(sound: Identifier, category: SoundCategory, volume: Float, pitch: Float, pos: BlockPos, world: World, radius: Double = 16.0) {
            PlaySound(sound, category, volume, pitch, pos, world, radius).instance.sendToPlayersAround(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), radius, world.registryKey)
        }
    }
}