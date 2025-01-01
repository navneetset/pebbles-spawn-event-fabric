package tech.sethi.pebbles.spawnevents.screenhandler

import com.cobblemon.mod.common.util.server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import tech.sethi.pebbles.spawnevents.config.eventhistory.EventHistoryConfig
import tech.sethi.pebbles.spawnevents.util.PM
import kotlin.math.min

class EventMenu(
    syncId: Int, private val player: PlayerEntity
) : GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, player.inventory, SimpleInventory(9 * 6), 6) {

    val config = EventHistoryConfig.config
    val screenConfig = EventHistoryConfig.screenConfig

    companion object {
        fun open(player: ServerPlayerEntity) {
            player.openHandledScreen(
                SimpleNamedScreenHandlerFactory(
                    { syncId, playerInventory, _ ->
                        EventMenu(syncId, playerInventory.player)
                    }, PM.returnStyledText(EventHistoryConfig.screenConfig.title)
                )
            )
        }
    }

    var historyPage = 0


    val liveSpawnSlot = screenConfig.liveSpawnSlot

    val historySlots = screenConfig.historySlots
    val navForwardSlots = screenConfig.navForwardSlots
    val navBackSlots = screenConfig.navBackSlots

    private val itemsPerPage: Int = historySlots.size


    init {
        setupPage()
    }

    fun setupPage() {

        liveSpawnSlot.let { slot ->
            screenConfig.liveSpawnItemStack().also { stack ->
                inventory.setStack(slot, stack)
            }
        }

        setupHistoryStack()

        navForwardSlots.forEach { slot ->
            inventory.setStack(slot, screenConfig.navForwardStack.toItemStack())
        }

        navBackSlots.forEach { slot ->
            inventory.setStack(slot, screenConfig.navBackStack.toItemStack())
        }

    }


    fun setupHistoryStack() {
        CoroutineScope(Dispatchers.IO).launch {
            // Ensure history is up-to-date
            EventHistoryConfig.reloadHistory()

            val pageStacks = getPageHistoryStacks() // Get only the stacks for the current page

            server()!!.execute {
                // Clear existing items in history slots
                historySlots.forEach { slot -> inventory.setStack(slot, ItemStack.EMPTY) }

                // Place the current page's history items into the corresponding slots
                pageStacks.forEachIndexed { index, stack ->
                    inventory.setStack(historySlots[index], stack)
                }
            }
        }
    }

    private fun getPageHistoryStacks(): List<ItemStack> {
        // Calculate the start and end index for the current page
        val startIndex = historyPage * itemsPerPage
        val endIndex = min(startIndex + itemsPerPage, EventHistoryConfig.history.history.size)

        // Return the sublist of history items for the current page
        return EventHistoryConfig.history.history.reversed().subList(startIndex, endIndex).map { it.toItemStack() }
    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?) {
        when (slotIndex) {
            in navForwardSlots -> {
                val maxPage = (EventHistoryConfig.history.history.size - 1) / itemsPerPage
                if (historyPage < maxPage) {
                    historyPage++
                    setupHistoryStack()
                }
            }
            in navBackSlots -> {
                if (historyPage > 0) {
                    historyPage--
                    setupHistoryStack()
                }
            }
        }

        return
    }
}