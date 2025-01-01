package tech.sethi.pebbles.spawnevents.util

import com.cobblemon.mod.common.util.server
import com.mojang.brigadier.ParseResults
import com.mojang.serialization.Dynamic
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.SharedConstants
import net.minecraft.component.ComponentChanges
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.datafixer.TypeReferences
import net.minecraft.entity.effect.StatusEffect
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.*
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.sound.SoundEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Identifier


object PM {

    private fun parseMessageWithStyles(text: String, placeholder: String, style: Boolean = true): Component {
        val mm = if (style) {
            MiniMessage.miniMessage()
        } else {
            MiniMessage.builder().tags(TagResolver.empty()).build()
        }

        return mm.deserialize(text.replace("{placeholder}", placeholder)).decoration(TextDecoration.ITALIC, false)
    }

    fun returnStyledText(text: String, style: Boolean = true): MutableText {
        val component = parseMessageWithStyles(text, "placeholder", style)
        val gson = GsonComponentSerializer.gson()
        val json = gson.serialize(component)
        return Text.Serialization.fromJson(json, DynamicRegistryManager.EMPTY) as MutableText
    }

    fun returnStyledJson(text: String): String {
        val component = parseMessageWithStyles(text, "placeholder")
        val gson = GsonComponentSerializer.gson()
        val json = gson.serialize(component)
        return json
    }

    fun setLore(itemStack: ItemStack, lore: List<String>) {
        val textLoreList = mutableListOf<Text>()

        for (line in lore) {
            val text = returnStyledText(line)
            textLoreList.add(text)
        }

        val loreComponent = LoreComponent(textLoreList)
        itemStack.set(DataComponentTypes.LORE, loreComponent)
    }

    fun sendText(player: PlayerEntity, text: String) {
        val component = returnStyledText(text)
        player.sendMessage(component, false)
    }

    fun parseCommand(
        command: String, context: String, server: MinecraftServer, player: PlayerEntity?
    ): ParseResults<ServerCommandSource>? {
        val cmdManager = server.commandManager

        when (context) {
            "console" -> {
                return cmdManager?.dispatcher?.parse(command, server.commandSource)
            }

            "player" -> {
                return cmdManager?.dispatcher?.parse(command, player?.commandSource)
            }
        }

        return null
    }

    fun getItem(itemId: String): Item {
        return Registries.ITEM.get(Identifier.tryParse(itemId))
    }

    fun getSounds(soundId: String): SoundEvent? {
        return Registries.SOUND_EVENT.get(Identifier.tryParse(soundId))
    }

    fun getStatusEffect(statusEffectId: String): StatusEffect {
        return Registries.STATUS_EFFECT.get(Identifier.tryParse(statusEffectId))
            ?: throw Exception("Status effect $statusEffectId not found")
    }

    fun runCommand(command: String) {
        try {
            val parseResults: ParseResults<ServerCommandSource> =
                server()!!.commandManager.dispatcher.parse(command, server()!!.commandSource)
            server()!!.commandManager.dispatcher.execute(parseResults)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLocaleText(key: String): String {
        return Text.translatable(key).string
    }

    fun createItemStack(
        item: Item, count: Int, name: String? = null, lore: List<String> = listOf(), nbtString: String? = null
    ): ItemStack {
        var itemStack = ItemStack(item, count)

        if (nbtString != null && nbtString != "" && nbtString != "{}") {
            val parsedNbt = StringNbtReader.parse(nbtString)

            val namespacedKeyPattern = Regex("^[a-z0-9_.-]+:[a-z0-9_/.-]+$")

            val isLegacy = parsedNbt.keys.any { !namespacedKeyPattern.matches(it) }

            if (isLegacy) {
                val legacyNbt = NbtCompound().apply {
                    putString("id", itemStack.registryEntry.idAsString)
                    putInt("Count", count)
                    put("tag", parsedNbt)
                }

                val updatedNbt = server()!!.dataFixer?.update(
                    TypeReferences.ITEM_STACK,
                    Dynamic(server()!!.registryManager.getOps(NbtOps.INSTANCE), legacyNbt),
                    3700,
                    SharedConstants.getGameVersion().saveVersion.id
                )?.value

                itemStack =
                    ItemStack.CODEC.parse(server()!!.registryManager.getOps(NbtOps.INSTANCE), updatedNbt).result().orElse(ItemStack.EMPTY)
            } else {
                val updatedNbt =
                    ComponentChanges.CODEC.parse(server()!!.registryManager.getOps(NbtOps.INSTANCE), StringNbtReader.parse(nbtString))
                        .result().orElse(null)
                itemStack.applyChanges(updatedNbt)
                itemStack.count = count
            }
        }

        if (name != null) {
            itemStack.set(DataComponentTypes.CUSTOM_NAME, returnStyledText(name))
        }

        if (lore.isEmpty().not()) {
            setLore(itemStack, lore)
        }

        return itemStack
    }
}

data class SerializedItemStack(
    val displayName: String?,
    val material: String,
    val amount: Int,
    val nbt: String?,
    val lore: List<String> = listOf()
) {
    fun toItemStack(): ItemStack {
        val item = PM.getItem(material)
        val itemStack = PM.createItemStack(item = item, count = amount, lore = lore, nbtString = nbt)
        if (displayName != null) {
            itemStack.set(DataComponentTypes.CUSTOM_NAME, PM.returnStyledText(displayName))
        }
        return itemStack
    }
}
