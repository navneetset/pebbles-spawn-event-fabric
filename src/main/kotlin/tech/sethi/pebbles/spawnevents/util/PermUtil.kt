package tech.sethi.pebbles.spawnevents.util

import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.command.ServerCommandSource

object PermUtil {
    private fun isLuckPermsPresent(): Boolean {
        return try {
            Class.forName("net.luckperms.api.LuckPerms")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun getLuckPermsApi(): LuckPerms? {
        return try {
            LuckPermsProvider.get()
        } catch (e: IllegalStateException) {
            null
        }
    }

    fun commandRequiresPermission(source: ServerCommandSource, permission: String): Boolean {
        val player = source.player as? PlayerEntity
        return player != null && (source.hasPermissionLevel(2) || isLuckPermsPresent() && getLuckPermsApi()?.userManager?.getUser(
            player.uuid
        )!!.cachedData.permissionData.checkPermission(permission)
            .asBoolean()) || source.entity == null
    }
}