package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore

class TrashGUI : InventoryHolder {
    private val inventory: Inventory

    companion object {
        const val TITLE = "§c쓰레기통 (아이템 파기)"
        val ACTION_KEY = NamespacedKey(RPGcore.instance, "rpgcore_trash_action")
    }

    init {
        inventory = Bukkit.createInventory(this, 54, TITLE)
        initializeItems()
    }

    private fun initializeItems() {
        val background = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName(" ") }
        }
        for (i in 45 until 54) {
            inventory.setItem(i, background)
        }

        val confirmButton = ItemStack(Material.LAVA_BUCKET).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§4§l모든 아이템 파기")
                lore = listOf(
                    "§c경고: 이 버튼을 클릭하면",
                    "§c창고 위의 모든 아이템이 영구적으로 사라집니다.",
                    "§c이 작업은 되돌릴 수 없습니다."
                )
                persistentDataContainer.set(ACTION_KEY, PersistentDataType.STRING, "CONFIRM_DELETE")
            }
        }
        inventory.setItem(49, confirmButton) // 중앙
    }

    fun open(player: Player) {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory = inventory
}