package org.flash.rpgcore.guis

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.managers.DungeonManager

class EncyclopediaDungeonGUI(private val player: Player) : InventoryHolder {

    private val inventory: Inventory

    companion object {
        const val GUI_TITLE = "§0몬스터 도감: 던전 선택"
        val DUNGEON_ID_KEY = NamespacedKey(RPGcore.instance, "encyclopedia_dungeon_id")
    }

    init {
        val dungeons = DungeonManager.getAllDungeons().sortedBy { ChatColor.stripColor(it.displayName) }
        val guiSize = (dungeons.size / 9 + 1) * 9
        inventory = Bukkit.createInventory(this, guiSize.coerceIn(9, 54), GUI_TITLE)
        initializeItems(dungeons)
    }

    private fun initializeItems(dungeons: List<org.flash.rpgcore.dungeons.DungeonData>) {
        dungeons.forEach { dungeon ->
            val material = Material.getMaterial(dungeon.iconMaterial.uppercase()) ?: Material.STONE
            val item = ItemStack(material)
            val meta = item.itemMeta ?: Bukkit.getItemFactory().getItemMeta(material)!!

            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', dungeon.displayName))
            meta.lore = listOf(
                "§7이 던전에 출현하는",
                "§7몬스터 목록을 봅니다.",
                "",
                "§e클릭하여 확인"
            )
            meta.persistentDataContainer.set(DUNGEON_ID_KEY, PersistentDataType.STRING, dungeon.id)

            item.itemMeta = meta
            inventory.addItem(item)
        }
    }

    fun open() {
        player.openInventory(inventory)
    }

    override fun getInventory(): Inventory = inventory
}